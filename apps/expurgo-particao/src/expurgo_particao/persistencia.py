"""Acesso a dados da rotina de reclamação -- todo SQL que toca `autorizacoes` vive aqui.

Nomes de tabela de partição (`autorizacoes_pe<numero>`) não podem ser parametrizados como
valor de bind -- usamos `psycopg.sql.Identifier`, e validamos a faixa antes de montar o
identificador (nunca interpolação de string crua).
"""

from __future__ import annotations

import datetime as dt

from psycopg import Cursor, sql
from psycopg.rows import TupleRow

from expurgo_particao.estado import ResultadoExecucao

PARTICAO_EXPURGO_INICIO = 900
PARTICAO_EXPURGO_FIM = 999

NOME_TABELA_REGISTRO = "expurgo_particao_registro"


def nome_tabela_particao(particao: int) -> sql.Identifier:
    """Monta o identificador da partição, validando a faixa antes.

    A validação de faixa é a defesa contra SQL injection deste módulo: só depois dela o
    número vira `sql.Identifier`. Nunca troque por f-string.

    Args:
        particao: número da partição, dentro da faixa de expurgo (900..999).

    Returns:
        Identificador seguro para interpolar em `sql.SQL(...).format(...)`.

    Raises:
        ValueError: se a partição estiver fora da faixa de expurgo.
    """
    if not (PARTICAO_EXPURGO_INICIO <= particao <= PARTICAO_EXPURGO_FIM):
        raise ValueError(
            f"particao fora da faixa de expurgo "
            f"({PARTICAO_EXPURGO_INICIO}..{PARTICAO_EXPURGO_FIM}): {particao}"
        )
    return sql.Identifier(f"autorizacoes_pe{particao}")


def _linha_unica(cur: Cursor[TupleRow], consulta: str) -> TupleRow:
    """Agregacao sempre devolve exatamente uma linha; ausencia e' estado impossivel."""
    linha = cur.fetchone()
    if linha is None:
        raise RuntimeError(f"consulta nao devolveu linha alguma: {consulta}")
    return linha


def max_data_hora_ultima_atlz(cur: Cursor[TupleRow], particao: int) -> dt.datetime | None:
    """Data de atualização mais recente encontrada na partição.

    A coluna é `NOT NULL` (migration v1.0.0), então `None` aqui significa exatamente uma
    coisa: a partição está vazia. É o que dispensa uma consulta prévia de existência.

    Args:
        cur: cursor aberto na transação da rotina.
        particao: partição alvo do ciclo.

    Returns:
        O `max(data_hora_ultima_atlz)` da partição, ou `None` se ela estiver vazia.
    """
    query = sql.SQL("SELECT max(data_hora_ultima_atlz) FROM {}").format(
        nome_tabela_particao(particao)
    )
    cur.execute(query)
    valor: dt.datetime | None = _linha_unica(cur, "max_data_hora_ultima_atlz")[0]
    return valor


def truncar_particao(cur: Cursor[TupleRow], particao: int) -> None:
    """Esvazia a partição folha.

    `TRUNCATE` na folha não toma `ACCESS EXCLUSIVE` na tabela pai `autorizacoes` -- é por
    isso que ele, e não `DELETE`/`DETACH`/`DROP`, é a operação escolhida.

    Args:
        cur: cursor aberto na transação da rotina.
        particao: partição alvo do ciclo.
    """
    query = sql.SQL("TRUNCATE {}").format(nome_tabela_particao(particao))
    cur.execute(query)


def gravar_registro(cur: Cursor[TupleRow], resultado: ResultadoExecucao) -> None:
    """Grava o registro forense da execução.

    Chamado em toda execução, inclusive quando a ação foi nenhuma: a ausência de registro
    é o sinal de que a rotina parou, então uma execução sem efeito precisa deixar rastro.

    Args:
        cur: cursor aberto na transação da rotina. No caminho de esvaziamento é a MESMA
            transação do `TRUNCATE`, de propósito -- não pode existir gaveta esvaziada
            sem registro correspondente.
        resultado: o que foi calculado e o que foi feito.
    """
    cur.execute(
        sql.SQL(
            """
            INSERT INTO {} (
                semana, particao_escrita, particao_alvo, estado, acao, modo_consulta,
                executado_em, detalhe
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
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
            resultado.detalhe,
        ),
    )
