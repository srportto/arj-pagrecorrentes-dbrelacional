"""Acesso a dados da rotina de reclamação -- todo SQL que toca `autorizacoes` vive aqui.

Nomes de tabela de partição (`autorizacoes_pe<numero>`) não podem ser parametrizados como
valor de bind -- usamos `psycopg.sql.Identifier`, e validamos a faixa antes de montar o
identificador (nunca interpolação de string crua).
"""

from __future__ import annotations

import datetime as dt

from psycopg import sql

from expurgo_particao.estado import ResultadoExecucao

PARTICAO_EXPURGO_INICIO = 900
PARTICAO_EXPURGO_FIM = 999

NOME_TABELA_REGISTRO = "expurgo_particao_registro"


def nome_tabela_particao(particao: int) -> sql.Identifier:
    if not (PARTICAO_EXPURGO_INICIO <= particao <= PARTICAO_EXPURGO_FIM):
        raise ValueError(
            f"particao fora da faixa de expurgo ({PARTICAO_EXPURGO_INICIO}..{PARTICAO_EXPURGO_FIM}): "
            f"{particao}"
        )
    return sql.Identifier(f"autorizacoes_pe{particao}")


def existe_dado(cur, particao: int) -> bool:
    query = sql.SQL("SELECT EXISTS (SELECT 1 FROM {} LIMIT 1)").format(
        nome_tabela_particao(particao)
    )
    cur.execute(query)
    return bool(cur.fetchone()[0])


def max_data_hora_ultima_atlz(cur, particao: int) -> dt.datetime | None:
    query = sql.SQL("SELECT max(data_hora_ultima_atlz) FROM {}").format(
        nome_tabela_particao(particao)
    )
    cur.execute(query)
    return cur.fetchone()[0]


def truncar_particao(cur, particao: int) -> None:
    query = sql.SQL("TRUNCATE {}").format(nome_tabela_particao(particao))
    cur.execute(query)


def gravar_registro(cur, resultado: ResultadoExecucao) -> None:
    cur.execute(
        sql.SQL(
            """
            INSERT INTO {} (
                semana, particao_escrita, particao_alvo, estado, acao, modo_consulta,
                executado_em
            ) VALUES (%s, %s, %s, %s, %s, %s, %s)
            """
        ).format(sql.Identifier(NOME_TABELA_REGISTRO)),
        (
            resultado.semana,
            resultado.particao_escrita,
            resultado.particao_alvo,
            resultado.estado.value if resultado.estado is not None else None,
            resultado.acao.value,
            resultado.modo_consulta,
            resultado.executado_em,
        ),
    )
