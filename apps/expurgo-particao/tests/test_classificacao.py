"""Testes de `classificacao.py` -- lógica pura, sem Postgres."""

from __future__ import annotations

import datetime as dt

from expurgo_particao.classificacao import (
    IDADE_MINIMA_DIAS_CICLO_ANTERIOR,
    classificar_estado,
)
from expurgo_particao.estado import EstadoParticao


def test_particao_vazia() -> None:
    assert classificar_estado(None, dt.date(2026, 8, 22)) == EstadoParticao.VAZIA


def test_dado_com_98_semanas_e_ciclo_anterior() -> None:
    data_referencia = dt.date(2026, 8, 22)
    data_mais_recente = dt.datetime(2024, 10, 4, 12, 0, 0)  # ~98 semanas atrás
    assert (
        classificar_estado(data_mais_recente, data_referencia) == EstadoParticao.DADO_CICLO_ANTERIOR
    )


def test_dado_recente_e_anomalia() -> None:
    data_referencia = dt.date(2026, 8, 22)
    data_mais_recente = dt.datetime(2026, 8, 15, 12, 0, 0)  # uma semana atrás
    assert classificar_estado(data_mais_recente, data_referencia) == EstadoParticao.DADO_RECENTE


def test_fronteira_exata_do_limiar_e_ciclo_anterior() -> None:
    data_referencia = dt.date(2026, 8, 22)
    data_mais_recente = dt.datetime.combine(
        data_referencia - dt.timedelta(days=IDADE_MINIMA_DIAS_CICLO_ANTERIOR), dt.time.min
    )
    assert (
        classificar_estado(data_mais_recente, data_referencia) == EstadoParticao.DADO_CICLO_ANTERIOR
    )


def test_um_dia_antes_da_fronteira_e_recente() -> None:
    data_referencia = dt.date(2026, 8, 22)
    data_mais_recente = dt.datetime.combine(
        data_referencia - dt.timedelta(days=IDADE_MINIMA_DIAS_CICLO_ANTERIOR - 1), dt.time.min
    )
    assert classificar_estado(data_mais_recente, data_referencia) == EstadoParticao.DADO_RECENTE
