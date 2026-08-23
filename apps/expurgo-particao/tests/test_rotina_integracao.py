"""Testes de integração contra PostgreSQL real -- exercitam o TRUNCATE de verdade.

Requer o Postgres local do monorepo no ar. Usa uma data de referência fixa
(2026-08-22, semana 2955) cujas partições calculadas (escrita=955, alvo=957,
vizinhas=956/958) não colidem com a linha real pré-existente do ambiente de
desenvolvimento (partição 954) -- ver a spec `reclamacao-particao-expurgo`, requisito
"Caminho de expurgo verificável sem depender da passagem do tempo".
"""

from __future__ import annotations

import datetime as dt

import pytest

from expurgo_particao.calculo import obter_particao_alvo, obter_particao_expurgo_write
from expurgo_particao.estado import Acao, EstadoParticao
from expurgo_particao.persistencia import NOME_TABELA_REGISTRO
from expurgo_particao.rotina import executar
from conftest import (
    contar_linhas,
    inserir_autorizacao_sintetica,
    particao_ainda_anexada,
    truncar_particoes,
)

DATA_REFERENCIA = dt.date(2026, 8, 22)
PARTICAO_ESCRITA = obter_particao_expurgo_write(DATA_REFERENCIA)  # 955
PARTICAO_ALVO = obter_particao_alvo(DATA_REFERENCIA)  # 957
PARTICAO_VIZINHA_ANTERIOR = PARTICAO_ALVO - 1  # 956
PARTICAO_VIZINHA_POSTERIOR = PARTICAO_ALVO + 1  # 958

DATA_CICLO_ANTERIOR = dt.datetime.combine(
    DATA_REFERENCIA - dt.timedelta(weeks=98), dt.time(12, 0)
)
DATA_VIZINHA_ANTERIOR = dt.datetime.combine(
    DATA_REFERENCIA - dt.timedelta(weeks=99), dt.time(12, 0)
)
DATA_VIZINHA_POSTERIOR = dt.datetime.combine(
    DATA_REFERENCIA - dt.timedelta(weeks=97), dt.time(12, 0)
)
DATA_RECENTE = dt.datetime.combine(DATA_REFERENCIA - dt.timedelta(days=5), dt.time(12, 0))


@pytest.fixture(autouse=True)
def limpar_particoes_de_teste(dsn):
    truncar_particoes(dsn, PARTICAO_ALVO, PARTICAO_VIZINHA_ANTERIOR, PARTICAO_VIZINHA_POSTERIOR)
    yield
    truncar_particoes(dsn, PARTICAO_ALVO, PARTICAO_VIZINHA_ANTERIOR, PARTICAO_VIZINHA_POSTERIOR)


def _limpar_registro(conexao):
    with conexao.cursor() as cur:
        cur.execute(f"DELETE FROM {NOME_TABELA_REGISTRO}")
    conexao.commit()


class TestEsvaziamentoComVizinhasIntactas:
    """Cenário 5.1: alvo com dado de 98 semanas -> fica vazia; vizinhas intactas;
    relação ainda anexada ao pai; índices ainda válidos."""

    def test_alvo_com_dado_do_ciclo_anterior_e_esvaziada(self, dsn, conexao):
        _limpar_registro(conexao)
        with conexao.cursor() as cur:
            inserir_autorizacao_sintetica(cur, PARTICAO_ALVO, DATA_CICLO_ANTERIOR)
            inserir_autorizacao_sintetica(cur, PARTICAO_VIZINHA_ANTERIOR, DATA_VIZINHA_ANTERIOR)
            inserir_autorizacao_sintetica(cur, PARTICAO_VIZINHA_POSTERIOR, DATA_VIZINHA_POSTERIOR)
        conexao.commit()

        assert contar_linhas(dsn, PARTICAO_ALVO) == 1

        resultado = executar(dsn, data_referencia=DATA_REFERENCIA)

        assert resultado.estado == EstadoParticao.DADO_CICLO_ANTERIOR
        assert resultado.acao == Acao.TRUNCATE
        assert resultado.particao_alvo == PARTICAO_ALVO
        assert resultado.particao_escrita == PARTICAO_ESCRITA

        # a alvo ficou vazia
        assert contar_linhas(dsn, PARTICAO_ALVO) == 0

        # e continua sendo particao de verdade -- nao foi drop+recriada
        assert particao_ainda_anexada(dsn, PARTICAO_ALVO)

    def test_vizinhas_permanecem_intactas(self, dsn, conexao):
        with conexao.cursor() as cur:
            inserir_autorizacao_sintetica(cur, PARTICAO_ALVO, DATA_CICLO_ANTERIOR)
            inserir_autorizacao_sintetica(cur, PARTICAO_VIZINHA_ANTERIOR, DATA_VIZINHA_ANTERIOR)
            inserir_autorizacao_sintetica(cur, PARTICAO_VIZINHA_ANTERIOR, DATA_VIZINHA_ANTERIOR)
            inserir_autorizacao_sintetica(cur, PARTICAO_VIZINHA_POSTERIOR, DATA_VIZINHA_POSTERIOR)
        conexao.commit()

        assert contar_linhas(dsn, PARTICAO_VIZINHA_ANTERIOR) == 2
        assert contar_linhas(dsn, PARTICAO_VIZINHA_POSTERIOR) == 1

        executar(dsn, data_referencia=DATA_REFERENCIA)

        # apenas a alvo foi afetada
        assert contar_linhas(dsn, PARTICAO_VIZINHA_ANTERIOR) == 2
        assert contar_linhas(dsn, PARTICAO_VIZINHA_POSTERIOR) == 1
        assert particao_ainda_anexada(dsn, PARTICAO_VIZINHA_ANTERIOR)
        assert particao_ainda_anexada(dsn, PARTICAO_VIZINHA_POSTERIOR)


class TestParticaoVazia:
    """Cenário 5.2: alvo vazia -> nenhuma escrita, execução bem-sucedida, sem alarme."""

    def test_particao_vazia_nao_sofre_escrita(self, dsn):
        assert contar_linhas(dsn, PARTICAO_ALVO) == 0

        resultado = executar(dsn, data_referencia=DATA_REFERENCIA)

        assert resultado.estado == EstadoParticao.VAZIA
        assert resultado.acao == Acao.NENHUMA
        assert contar_linhas(dsn, PARTICAO_ALVO) == 0


class TestRecusaDeDadoRecente:
    """Cenário 5.3: alvo com dado recente -> recusa, ROLLBACK, linhas preservadas,
    anomalia registrada."""

    def test_dado_recente_e_recusado(self, dsn, conexao):
        with conexao.cursor() as cur:
            inserir_autorizacao_sintetica(cur, PARTICAO_ALVO, DATA_RECENTE)
        conexao.commit()

        resultado = executar(dsn, data_referencia=DATA_REFERENCIA)

        assert resultado.estado == EstadoParticao.DADO_RECENTE
        assert resultado.acao == Acao.RECUSA_DADO_RECENTE

        # nada foi apagado
        assert contar_linhas(dsn, PARTICAO_ALVO) == 1

    def test_anomalia_fica_registrada(self, dsn, conexao):
        _limpar_registro(conexao)
        with conexao.cursor() as cur:
            inserir_autorizacao_sintetica(cur, PARTICAO_ALVO, DATA_RECENTE)
        conexao.commit()

        executar(dsn, data_referencia=DATA_REFERENCIA)

        with conexao.cursor() as cur:
            cur.execute(
                f"SELECT acao, estado FROM {NOME_TABELA_REGISTRO} "
                "WHERE particao_alvo = %s ORDER BY id DESC LIMIT 1",
                (PARTICAO_ALVO,),
            )
            acao, estado = cur.fetchone()
        assert acao == Acao.RECUSA_DADO_RECENTE.value
        assert estado == EstadoParticao.DADO_RECENTE.value


class TestParticaoVoltaAReceberEscrita:
    """Cenário 5.4: após o esvaziamento, uma inserção na gaveta esvaziada é roteada
    normalmente, sem reanexação nem recriação de índice."""

    def test_insercao_apos_esvaziamento_funciona_sem_cerimonia(self, dsn, conexao):
        with conexao.cursor() as cur:
            inserir_autorizacao_sintetica(cur, PARTICAO_ALVO, DATA_CICLO_ANTERIOR)
        conexao.commit()

        executar(dsn, data_referencia=DATA_REFERENCIA)
        assert contar_linhas(dsn, PARTICAO_ALVO) == 0

        with conexao.cursor() as cur:
            inserir_autorizacao_sintetica(cur, PARTICAO_ALVO, dt.datetime.now())
        conexao.commit()

        assert contar_linhas(dsn, PARTICAO_ALVO) == 1
        assert particao_ainda_anexada(dsn, PARTICAO_ALVO)


class TestModoConsulta:
    """Cenário 5.5: modo consulta com data de referência futura relata o alvo e a ação,
    sem alterar dado algum."""

    def test_consulta_data_futura_nao_altera_dado(self, dsn, conexao):
        data_futura = dt.date(2028, 4, 20)
        with conexao.cursor() as cur:
            inserir_autorizacao_sintetica(cur, PARTICAO_ALVO, DATA_CICLO_ANTERIOR)
        conexao.commit()

        antes = contar_linhas(dsn, PARTICAO_ALVO)

        resultado = executar(dsn, data_referencia=data_futura, modo_consulta=True)

        assert resultado.modo_consulta is True
        assert contar_linhas(dsn, PARTICAO_ALVO) == antes  # nada mudou

    def test_consulta_relata_particao_alvo_calculada(self, dsn):
        data_futura = dt.date(2028, 4, 20)
        resultado = executar(dsn, data_referencia=data_futura, modo_consulta=True)

        assert resultado.particao_alvo == obter_particao_alvo(data_futura)
        assert resultado.particao_escrita == obter_particao_expurgo_write(data_futura)
        assert resultado.acao == Acao.NENHUMA  # consulta nunca aplica, mesmo se houvesse dado
