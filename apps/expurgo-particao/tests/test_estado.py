"""Testes do registro que toda execução produz -- puros, sem banco (rodam no CI)."""

from __future__ import annotations

import dataclasses
import datetime as dt

import pytest

from expurgo_particao.estado import Acao, EstadoParticao, ResultadoExecucao

EXECUTADO_EM = dt.datetime(2026, 8, 22, 12, 30, 45, tzinfo=dt.UTC)


def _resultado(
    estado: EstadoParticao | None = EstadoParticao.VAZIA,
    acao: Acao = Acao.NENHUMA,
    modo_consulta: bool = False,
) -> ResultadoExecucao:
    return ResultadoExecucao(
        semana=2955,
        particao_escrita=955,
        particao_alvo=957,
        estado=estado,
        acao=acao,
        modo_consulta=modo_consulta,
        executado_em=EXECUTADO_EM,
    )


def test_registro_carrega_o_que_foi_calculado_e_nao_so_o_que_foi_feito() -> None:
    registro = _resultado().como_registro()

    # A semana e as duas particoes sao o que torna uma execucao sem efeito distinguivel
    # de uma rotina quebrada -- ver spec `reclamacao-particao-expurgo`.
    assert registro["semana"] == 2955
    assert registro["particao_escrita"] == 955
    assert registro["particao_alvo"] == 957
    assert registro["acao"] == "NENHUMA"
    assert registro["estado"] == "VAZIA"


def test_estado_ausente_vira_none_e_nao_string() -> None:
    """RECUSA_LOCK_TIMEOUT nao observou estado algum -- nao e' o mesmo que VAZIA."""
    registro = _resultado(estado=None, acao=Acao.RECUSA_LOCK_TIMEOUT).como_registro()

    assert registro["estado"] is None
    assert registro["acao"] == "RECUSA_LOCK_TIMEOUT"


def test_executado_em_sai_em_iso8601_com_fuso() -> None:
    registro = _resultado().como_registro()

    assert registro["executado_em"] == "2026-08-22T12:30:45+00:00"


@pytest.mark.parametrize("modo_consulta", [True, False])
def test_modo_consulta_e_registrado_como_booleano(modo_consulta: bool) -> None:
    registro = _resultado(modo_consulta=modo_consulta).como_registro()

    assert registro["modo_consulta"] is modo_consulta


def test_resultado_e_imutavel() -> None:
    resultado = _resultado()

    with pytest.raises(dataclasses.FrozenInstanceError):
        resultado.acao = Acao.TRUNCATE  # type: ignore[misc]  # o ponto do teste e' a recusa


@pytest.mark.parametrize("acao", list(Acao))
def test_toda_acao_serializa_como_o_proprio_nome(acao: Acao) -> None:
    """O valor gravado na coluna `acao` precisa bater com o CHECK da migration."""
    registro = _resultado(acao=acao).como_registro()

    assert registro["acao"] == acao.name


@pytest.mark.parametrize("estado", list(EstadoParticao))
def test_todo_estado_serializa_como_o_proprio_nome(estado: EstadoParticao) -> None:
    registro = _resultado(estado=estado).como_registro()

    assert registro["estado"] == estado.name
