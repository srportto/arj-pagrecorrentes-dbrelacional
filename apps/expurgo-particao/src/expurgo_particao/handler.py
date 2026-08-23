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

from expurgo_particao.rotina import executar

logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO"))
logger = logging.getLogger(__name__)


def _montar_dsn() -> str:
    host = os.environ["DB_HOST"]
    port = os.environ.get("DB_PORT", "5432")
    nome = os.environ["DB_NAME"]
    usuario = os.environ["DB_USER_NAME"]
    senha = os.environ["DB_PASSWORD"]
    return f"postgresql://{usuario}:{senha}@{host}:{port}/{nome}"


def lambda_handler(event: dict, context=None) -> dict:
    event = event or {}

    data_referencia_str = event.get("data_referencia")
    data_referencia = (
        dt.date.fromisoformat(data_referencia_str) if data_referencia_str else None
    )
    modo_consulta = bool(event.get("modo_consulta", False))

    resultado = executar(
        dsn=_montar_dsn(),
        data_referencia=data_referencia,
        modo_consulta=modo_consulta,
    )

    registro = resultado.como_registro()
    logger.info("resultado da execucao: %s", registro)

    return {"statusCode": 200, "body": registro}
