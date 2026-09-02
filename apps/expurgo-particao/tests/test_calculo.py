"""Testes de `calculo.py` -- lógica pura, sem Postgres.

Os valores de paridade replicam `ControleExpurgoAutorizacaoTest` (Java, contratocommand)
ponto a ponto -- as duas suítes devem sempre concordar, já que a fórmula é espelhada
manualmente entre as duas linguagens.
"""

from __future__ import annotations

import datetime as dt
from zoneinfo import ZoneInfo

import pytest

from expurgo_particao.calculo import (
    PARTICAO_EXPURGO_FIM,
    PARTICAO_EXPURGO_INICIO,
    QUANTIDADE_GAVETAS_EXPURGO,
    agora_utc_date,
    obter_particao_alvo,
    obter_particao_expurgo_write,
)

EPOCH_DAY = dt.date(1970, 1, 1)


def _mais_semanas(semanas: int) -> dt.date:
    return EPOCH_DAY + dt.timedelta(weeks=semanas)


class TestParidadeComJava:
    """Espelha, valor a valor, os testes de ControleExpurgoAutorizacaoTest."""

    def test_epoch_day(self) -> None:
        assert obter_particao_expurgo_write(EPOCH_DAY) == 900

    def test_uma_semana_apos_epoch(self) -> None:
        assert obter_particao_expurgo_write(_mais_semanas(1)) == 901

    def test_99_semanas_apos_epoch(self) -> None:
        assert obter_particao_expurgo_write(_mais_semanas(99)) == 999

    def test_100_semanas_apos_epoch_volta_ao_ciclo(self) -> None:
        assert obter_particao_expurgo_write(_mais_semanas(100)) == 900

    def test_50_semanas_apos_epoch(self) -> None:
        assert obter_particao_expurgo_write(_mais_semanas(50)) == 950

    @pytest.mark.parametrize("semanas", [0, 1, 5, 10, 25, 50, 75, 99, 100, 150, 199, 200])
    def test_multiplas_semanas(self, semanas: int) -> None:
        esperado = (semanas % 100) + 900
        assert obter_particao_expurgo_write(_mais_semanas(semanas)) == esperado

    def test_todos_os_valores_no_900_a_999(self) -> None:
        particoes_geradas = {
            obter_particao_expurgo_write(_mais_semanas(semanas)) for semanas in range(1000)
        }
        assert len(particoes_geradas) == 100
        assert particoes_geradas == set(range(900, 1000))

    def test_resultado_sempre_no_range(self) -> None:
        datas = [
            dt.date(1970, 1, 1),
            dt.date(2000, 1, 1),
            dt.date(2026, 4, 18),
            dt.date(2050, 12, 31),
            dt.date(2100, 6, 15),
        ]
        for data in datas:
            resultado = obter_particao_expurgo_write(data)
            assert PARTICAO_EXPURGO_INICIO <= resultado <= PARTICAO_EXPURGO_FIM

    def test_formula_exata(self) -> None:
        for semanas in range(300):
            esperado = (semanas % 100) + 900
            assert obter_particao_expurgo_write(_mais_semanas(semanas)) == esperado

    def test_consistencia_para_mesma_data(self) -> None:
        data = dt.date(2026, 4, 18)
        resultado1 = obter_particao_expurgo_write(data)
        resultado2 = obter_particao_expurgo_write(data)
        assert resultado1 == resultado2


class TestParticaoAlvo:
    def test_alvo_e_escrita_mais_dois(self) -> None:
        data = dt.date(2026, 8, 22)
        assert obter_particao_alvo(data) == obter_particao_expurgo_write(data) + 2

    def test_alvo_respeita_retorno_ciclico(self) -> None:
        # semana cuja particao de escrita e 998 -> alvo (998+2=1000) deve voltar a 900
        data_998 = _mais_semanas(98)
        assert obter_particao_expurgo_write(data_998) == 998
        assert obter_particao_alvo(data_998) == 900

        # semana cuja particao de escrita e 999 -> alvo (999+2=1001) deve voltar a 901
        data_999 = _mais_semanas(99)
        assert obter_particao_expurgo_write(data_999) == 999
        assert obter_particao_alvo(data_999) == 901

    def test_alvo_permanece_na_faixa_de_expurgo(self) -> None:
        for semanas in range(QUANTIDADE_GAVETAS_EXPURGO):
            alvo = obter_particao_alvo(_mais_semanas(semanas))
            assert PARTICAO_EXPURGO_INICIO <= alvo <= PARTICAO_EXPURGO_FIM

    def test_alvo_nunca_coincide_com_a_escrita(self) -> None:
        """Para toda semana de um ciclo completo -- requisito explícito da spec."""
        for semanas in range(QUANTIDADE_GAVETAS_EXPURGO):
            data = _mais_semanas(semanas)
            assert obter_particao_alvo(data) != obter_particao_expurgo_write(data)


class TestFusoHorario:
    """A rotina e o contratocommand nao podem discordar sobre qual e a semana corrente."""

    def test_agora_sem_argumento_usa_utc_real(self) -> None:
        antes = dt.datetime.now(dt.UTC).date()
        resultado = agora_utc_date()
        depois = dt.datetime.now(dt.UTC).date()
        assert antes <= resultado <= depois

    def test_datetime_naive_e_rejeitado(self) -> None:
        with pytest.raises(ValueError):
            agora_utc_date(dt.datetime(2026, 8, 27, 0, 30))

    def test_mesmo_instante_utc_e_sao_paulo_produz_a_mesma_data(self) -> None:
        # 2026-08-27 00:30 UTC (quinta, logo apos a virada da gaveta) --
        # em America/Sao_Paulo (UTC-3) o relogio de parede marca 2026-08-26 21:30,
        # um dia civil ANTES. A funcao deve devolver 2026-08-27 nos dois casos, porque
        # ambos representam o MESMO instante, so expresso em fusos diferentes.
        instante_utc = dt.datetime(2026, 8, 27, 0, 30, tzinfo=dt.UTC)
        instante_sao_paulo = instante_utc.astimezone(ZoneInfo("America/Sao_Paulo"))

        assert instante_sao_paulo.date() == dt.date(2026, 8, 26)  # a armadilha que o teste evita

        assert agora_utc_date(instante_utc) == dt.date(2026, 8, 27)
        assert agora_utc_date(instante_sao_paulo) == dt.date(2026, 8, 27)

    def test_semana_calculada_bate_independente_do_fuso_de_entrada(self) -> None:
        instante_utc = dt.datetime(2026, 8, 27, 0, 30, tzinfo=dt.UTC)
        instante_sao_paulo = instante_utc.astimezone(ZoneInfo("America/Sao_Paulo"))
        instante_tokyo = instante_utc.astimezone(ZoneInfo("Asia/Tokyo"))

        particao_utc = obter_particao_expurgo_write(agora_utc_date(instante_utc))
        particao_sao_paulo = obter_particao_expurgo_write(agora_utc_date(instante_sao_paulo))
        particao_tokyo = obter_particao_expurgo_write(agora_utc_date(instante_tokyo))

        assert particao_utc == particao_sao_paulo == particao_tokyo
