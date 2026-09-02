"""Fixtures compartilhadas dos testes de integração (Postgres real).

Requer o Postgres local do monorepo no ar (`docker compose up -d` na raiz, ou
`infra/local/postgres/postgres-db-v18.yml` isolado) com a migration
`v1.0.7.-cria-tabela-registro-expurgo-particao.sql` já aplicada.

A DSN de teste vem só de `EXPURGO_PARTICAO_TEST_DSN` — sem default com credencial real
(mesma convenção de `gestao-de-segredos`: falha explícita nomeando a variável ausente é
preferível a um valor padrão de senha, mesmo local).
"""

from __future__ import annotations

import datetime as dt
import os
import uuid
from collections.abc import Iterator

import psycopg
import pytest
from psycopg.rows import TupleRow


def _dsn() -> str:
    try:
        return os.environ["EXPURGO_PARTICAO_TEST_DSN"]
    except KeyError as exc:
        raise RuntimeError(
            "EXPURGO_PARTICAO_TEST_DSN nao definida. Exporte a DSN do Postgres local "
            "antes de rodar os testes de integracao, ex.: "
            "postgresql://docker:<sua-senha>@localhost:5432/db-csp-postgres "
            "(a senha vem do seu .env local, nunca hardcoded aqui)."
        ) from exc


@pytest.fixture(scope="session")
def dsn() -> str:
    return _dsn()


@pytest.fixture
def conexao(dsn: str) -> Iterator[psycopg.Connection[TupleRow]]:
    with psycopg.connect(dsn, autocommit=False) as conn:
        yield conn


def inserir_autorizacao_sintetica(
    cur: psycopg.Cursor[TupleRow],
    particao: int,
    data_hora_ultima_atlz: dt.datetime,
) -> uuid.UUID:
    """Insere uma linha mínima e válida (respeita todo NOT NULL de `autorizacoes`) na
    partição informada, com a idade dada -- usada pelos testes para semear cenários sem
    depender do script `gerar-massa-sintetica-expurgo.sql` (que é para uso manual/CI via
    psql, não para chamada em cada teste)."""
    id_autorizacao = uuid.uuid4()
    cur.execute(
        """
        INSERT INTO autorizacoes (
            id_autorizacao, id_particao_conta, tipo_produto, tipo_jornada, status,
            motivo_status, data_hora_inclusao, data_hora_ultima_atlz, data_inicio_vigencia,
            data_fim_vigencia, valor, id_autorizacao_empresa, valor_limite, frequencia,
            quantidade_dividas_ciclo, indicador_uso_limite_conta, indicador_tipo_mensageria,
            codigo_canal_contratacao, descricao, id_unico_conta_contratante,
            id_pessoa_pagadora, id_pessoa_devedora, id_pessoa_recebedora,
            codigo_canal_cancelamento, id_pessoa_cancelamento, data_hora_cancelamento,
            motivo_cancelamento, metadados
        ) VALUES (
            %(id_autorizacao)s, %(particao)s, 1, 0, 5, 'teste automatizado',
            %(data)s, %(data)s, %(data)s, %(data)s, 100.00, %(chave)s, 1000.00, 1, 0, 0, 0,
            'CANAL_TESTE', 'linha de teste automatizado', %(uuid1)s, %(uuid2)s, %(uuid3)s,
            %(uuid4)s, 'CANAL_TESTE', %(uuid5)s, %(data)s, 'teste automatizado', '{}'
        )
        """,
        {
            "id_autorizacao": id_autorizacao,
            "particao": particao,
            "data": data_hora_ultima_atlz,
            "chave": str(uuid.uuid4()),
            "uuid1": uuid.uuid4(),
            "uuid2": uuid.uuid4(),
            "uuid3": uuid.uuid4(),
            "uuid4": uuid.uuid4(),
            "uuid5": uuid.uuid4(),
        },
    )
    return id_autorizacao


def truncar_particoes(dsn_str: str, *particoes: int) -> None:
    with psycopg.connect(dsn_str, autocommit=True) as conn, conn.cursor() as cur:
        for p in particoes:
            cur.execute(f"TRUNCATE autorizacoes_pe{p}")


def contar_linhas(dsn_str: str, particao: int) -> int:
    with psycopg.connect(dsn_str, autocommit=True) as conn, conn.cursor() as cur:
        cur.execute(f"SELECT count(*) FROM autorizacoes_pe{particao}")
        linha = cur.fetchone()
        if linha is None:
            raise RuntimeError("count(*) nao devolveu linha")
        total: int = linha[0]
        return total


def particao_ainda_anexada(dsn_str: str, particao: int) -> bool:
    """Confirma que a particao continua sendo filha de `autorizacoes` no pg_inherits,
    com o mesmo relpartbound -- a prova de que TRUNCATE nao desanexou nada."""
    nome = f"autorizacoes_pe{particao}"
    with psycopg.connect(dsn_str, autocommit=True) as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT i.inhparent::regclass::text, pg_get_expr(c.relpartbound, c.oid)
            FROM pg_class c
            JOIN pg_inherits i ON i.inhrelid = c.oid
            WHERE c.relname = %s
            """,
            (nome,),
        )
        row = cur.fetchone()
        if row is None:
            return False
        pai, bound = row
        return pai == "autorizacoes" and f"({particao})" in bound
