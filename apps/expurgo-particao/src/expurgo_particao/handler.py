"""Ponto de entrada da Lambda -- lê o evento e as variáveis de ambiente, chama a rotina.

Evento aceito (todos os campos opcionais, ver requisito "Consulta por data de referência
sem efeito colateral" da spec `reclamacao-particao-expurgo`):

    {
        "data_referencia": "2028-04-20",   // AAAA-MM-DD; ausente = data corrente (UTC)
        "modo_consulta": true              // ausente/false = aplica normalmente
    }
"""

from __future__ import annotations

import datetime as dt
import logging
import os
from typing import Any

from psycopg.conninfo import make_conninfo

from expurgo_particao.rotina import executar

# `logging.basicConfig` nao serve aqui: o runtime da Lambda ja instalou um handler no root
# logger antes deste modulo ser importado, e basicConfig e' no-op quando ja existe handler
# -- LOG_LEVEL era silenciosamente ignorado em producao (e so funcionava no teste local).
logger = logging.getLogger(__name__)
logger.setLevel(os.environ.get("LOG_LEVEL", "INFO"))


def _montar_dsn() -> str:
    """Monta a string de conexão a partir do ambiente.

    Usa `make_conninfo` em vez de f-string: senha gerada pelo Secrets Manager contém
    símbolos por padrão, e `@`, `/` ou `%` numa URI montada à mão corrompem a credencial
    no parse -- a Lambda cai com erro que aponta para rede, não para autenticação.

    Raises:
        KeyError: se alguma variável de ambiente obrigatória não estiver definida.
    """
    return make_conninfo(
        host=os.environ["DB_HOST"],
        port=os.environ.get("DB_PORT", "5432"),
        dbname=os.environ["DB_NAME"],
        user=os.environ["DB_USER_NAME"],
        password=os.environ["DB_PASSWORD"],
    )


def lambda_handler(event: dict[str, Any] | None, context: object = None) -> dict[str, Any]:
    event = event or {}

    data_referencia_str = event.get("data_referencia")
    data_referencia = dt.date.fromisoformat(data_referencia_str) if data_referencia_str else None
    modo_consulta = bool(event.get("modo_consulta", False))

    resultado = executar(
        dsn=_montar_dsn(),
        data_referencia=data_referencia,
        modo_consulta=modo_consulta,
    )

    registro = resultado.como_registro()
    logger.info("resultado da execucao: %s", registro)

    return {"statusCode": 200, "body": registro}
