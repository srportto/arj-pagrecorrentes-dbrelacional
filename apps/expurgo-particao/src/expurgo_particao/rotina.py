"""Orquestra a reclamação da partição de expurgo permitida do ciclo.

Abre e fecha a própria conexão a cada chamada -- nunca reaproveita uma conexão guardada
em variável global entre invocações (ver design.md, risco "Conexão em container morno":
o Floci mantém pool de containers quentes, e uma conexão guardada entre invocações de 30
em 30 minutos pode voltar morta).
"""

from __future__ import annotations

import datetime as dt
import logging
import os

import psycopg
from psycopg import sql

from expurgo_particao.calculo import agora_utc_date, obter_particao_alvo, obter_particao_expurgo_write
from expurgo_particao.classificacao import classificar_estado
from expurgo_particao.estado import Acao, EstadoParticao, ResultadoExecucao
from expurgo_particao.persistencia import (
    existe_dado,
    gravar_registro,
    max_data_hora_ultima_atlz,
    truncar_particao,
)

logger = logging.getLogger(__name__)

EPOCH = dt.date(1970, 1, 1)

# 5s: espera pouco e desiste -- a cadência de 30 minutos (336 tentativas por semana) é o
# mecanismo de retry, não vale segurar a listagem do contratoquery na fila do lock.
LOCK_TIMEOUT_MS = 5000

# Interruptor operacional (não fase de rollout, ver design.md D3): quando definida como
# valor verdadeiro, desarma o TRUNCATE sem desarmar o cálculo nem o registro -- a rotina
# continua calculando e relatando o que faria, só não aplica.
NOME_ENV_DESARMAR_TRUNCATE = "EXPURGO_PARTICAO_DESARMAR_TRUNCATE"

_VALORES_VERDADEIROS = {"1", "true", "yes", "on"}


def truncate_permitido(ambiente: dict | None = None) -> bool:
    ambiente = ambiente if ambiente is not None else os.environ
    valor = ambiente.get(NOME_ENV_DESARMAR_TRUNCATE, "").strip().lower()
    return valor not in _VALORES_VERDADEIROS


def _classificar_e_decidir(
    conexao: psycopg.Connection,
    particao_alvo: int,
    data_referencia: dt.date,
    modo_consulta: bool,
    permitir_truncate: bool,
) -> tuple[EstadoParticao | None, Acao]:
    """
    Verificação e esvaziamento (quando ocorre) na MESMA transação -- a reprovação da
    verificação nunca deixa efeito residual (a única escrita possível aqui é o TRUNCATE,
    e ele só acontece depois da classificação, antes do commit).
    """
    with conexao.cursor() as cur:
        # SET nao aceita parametro de bind no protocolo estendido do Postgres -- o valor
        # e' interno (nunca vem de entrada externa), entao sql.Literal e' seguro aqui.
        cur.execute(sql.SQL("SET LOCAL lock_timeout = {}").format(sql.Literal(f"{LOCK_TIMEOUT_MS}ms")))
        try:
            if not existe_dado(cur, particao_alvo):
                estado = EstadoParticao.VAZIA
            else:
                data_mais_recente = max_data_hora_ultima_atlz(cur, particao_alvo)
                estado = classificar_estado(data_mais_recente, data_referencia)

            if estado == EstadoParticao.DADO_CICLO_ANTERIOR and not modo_consulta and permitir_truncate:
                truncar_particao(cur, particao_alvo)
                conexao.commit()
                return estado, Acao.TRUNCATE

            acao = Acao.RECUSA_DADO_RECENTE if estado == EstadoParticao.DADO_RECENTE else Acao.NENHUMA
            conexao.rollback()  # nada foi escrito -- só leitura ate aqui
            return estado, acao
        except psycopg.errors.LockNotAvailable:
            conexao.rollback()
            logger.warning(
                "lock_timeout esgotado na particao %s -- execucao sem efeito, tentando de "
                "novo no proximo ciclo",
                particao_alvo,
            )
            return None, Acao.RECUSA_LOCK_TIMEOUT


def executar(
    dsn: str,
    data_referencia: dt.date | None = None,
    modo_consulta: bool = False,
    ambiente: dict | None = None,
) -> ResultadoExecucao:
    if data_referencia is None:
        data_referencia = agora_utc_date()

    semana = (data_referencia - EPOCH).days // 7
    particao_escrita = obter_particao_expurgo_write(data_referencia)
    particao_alvo = obter_particao_alvo(data_referencia)
    executado_em = dt.datetime.now(dt.timezone.utc)
    permitir_truncate = truncate_permitido(ambiente)

    if not permitir_truncate:
        logger.warning(
            "%s ativo -- TRUNCATE desarmado, rotina so calcula e registra",
            NOME_ENV_DESARMAR_TRUNCATE,
        )

    with psycopg.connect(dsn, autocommit=False) as conexao:
        estado, acao = _classificar_e_decidir(
            conexao, particao_alvo, data_referencia, modo_consulta, permitir_truncate
        )

        resultado = ResultadoExecucao(
            semana=semana,
            particao_escrita=particao_escrita,
            particao_alvo=particao_alvo,
            estado=estado,
            acao=acao,
            modo_consulta=modo_consulta,
            executado_em=executado_em,
        )

        with conexao.cursor() as cur:
            gravar_registro(cur, resultado)
        conexao.commit()

    logger.info("registro=%s", resultado.como_registro())
    return resultado
