"""Testes do ponto de entrada da Lambda -- puros, sem banco (rodam no CI).

`executar` e' substituido por um duplo, entao nenhum destes testes abre conexao: o que
esta sob teste aqui e' a montagem da DSN, a leitura do evento e a leitura do ambiente.
"""

from __future__ import annotations

import datetime as dt
from typing import Any

import psycopg
import pytest

from expurgo_particao import handler
from expurgo_particao.estado import Acao, EstadoParticao, ResultadoExecucao

AMBIENTE_COMPLETO = {
    "DB_HOST": "host.docker.internal",
    "DB_PORT": "5432",
    "DB_NAME": "db-csp-postgres",
    "DB_USER_NAME": "expurgo_particao_rotina",
    "DB_PASSWORD": "senha-simples",
}

# Caracteres que tem significado sintatico numa URI de conexao -- uma senha gerada pelo
# Secrets Manager contem simbolos por padrao, entao este nao e' caso hipotetico.
SENHAS_COM_CARACTERE_RESERVADO = [
    "sen@ha",
    "sen/ha",
    "sen:ha",
    "sen#ha",
    "sen?ha",
    "sen%ha",
    "p@ss:w/rd#1",
]


@pytest.fixture
def ambiente(monkeypatch: pytest.MonkeyPatch) -> None:
    for chave, valor in AMBIENTE_COMPLETO.items():
        monkeypatch.setenv(chave, valor)


def _resultado_qualquer() -> ResultadoExecucao:
    return ResultadoExecucao(
        semana=2955,
        particao_escrita=955,
        particao_alvo=957,
        estado=EstadoParticao.VAZIA,
        acao=Acao.NENHUMA,
        modo_consulta=False,
        executado_em=dt.datetime(2026, 8, 22, 12, 0, tzinfo=dt.UTC),
    )


class TestMontagemDaDsn:
    """A DSN precisa sobreviver a qualquer senha valida, nao so as alfanumericas."""

    def test_senha_simples_monta_dsn_utilizavel(self, ambiente: None) -> None:
        dsn = handler._montar_dsn()

        partes = psycopg.conninfo.conninfo_to_dict(dsn)
        assert partes["password"] == "senha-simples"
        assert partes["host"] == "host.docker.internal"
        assert partes["dbname"] == "db-csp-postgres"
        assert partes["user"] == "expurgo_particao_rotina"

    @pytest.mark.parametrize("senha", SENHAS_COM_CARACTERE_RESERVADO)
    def test_senha_com_caractere_reservado_preserva_credencial(
        self, ambiente: None, monkeypatch: pytest.MonkeyPatch, senha: str
    ) -> None:
        monkeypatch.setenv("DB_PASSWORD", senha)

        dsn = handler._montar_dsn()

        # O que importa nao e' o formato da string, e' o que o psycopg extrai dela:
        # senha corrompida no parse derruba a Lambda com erro que aponta para rede.
        partes = psycopg.conninfo.conninfo_to_dict(dsn)
        assert partes["password"] == senha
        assert partes["host"] == "host.docker.internal"
        assert partes["dbname"] == "db-csp-postgres"

    def test_porta_tem_valor_padrao(self, ambiente: None, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.delenv("DB_PORT")

        partes = psycopg.conninfo.conninfo_to_dict(handler._montar_dsn())

        assert str(partes["port"]) == "5432"

    @pytest.mark.parametrize("obrigatoria", ["DB_HOST", "DB_NAME", "DB_USER_NAME", "DB_PASSWORD"])
    def test_variavel_obrigatoria_ausente_falha_nomeando_a_variavel(
        self, ambiente: None, monkeypatch: pytest.MonkeyPatch, obrigatoria: str
    ) -> None:
        monkeypatch.delenv(obrigatoria)

        with pytest.raises(KeyError, match=obrigatoria):
            handler._montar_dsn()


class TestLeituraDoEvento:
    @pytest.fixture(autouse=True)
    def rotina_dublada(
        self, ambiente: None, monkeypatch: pytest.MonkeyPatch
    ) -> list[dict[str, Any]]:
        """Captura os argumentos com que `executar` foi chamada, sem tocar no banco."""
        chamadas: list[dict[str, Any]] = []

        def _executar(**kwargs: Any) -> ResultadoExecucao:
            chamadas.append(kwargs)
            return _resultado_qualquer()

        monkeypatch.setattr(handler, "executar", _executar)
        return chamadas

    def test_evento_vazio_usa_data_corrente_e_aplica(
        self, rotina_dublada: list[dict[str, Any]]
    ) -> None:
        handler.lambda_handler({})

        assert rotina_dublada[0]["data_referencia"] is None
        assert rotina_dublada[0]["modo_consulta"] is False

    def test_evento_none_e_tratado_como_vazio(self, rotina_dublada: list[dict[str, Any]]) -> None:
        handler.lambda_handler(None)

        assert rotina_dublada[0]["data_referencia"] is None

    def test_data_referencia_valida_e_repassada(self, rotina_dublada: list[dict[str, Any]]) -> None:
        handler.lambda_handler({"data_referencia": "2028-04-20"})

        assert rotina_dublada[0]["data_referencia"] == dt.date(2028, 4, 20)

    @pytest.mark.parametrize("invalida", ["20/04/2028", "2028-13-01", "ontem", "2028-04-31"])
    def test_data_referencia_invalida_falha_antes_de_tocar_no_banco(
        self, rotina_dublada: list[dict[str, Any]], invalida: str
    ) -> None:
        with pytest.raises(ValueError):
            handler.lambda_handler({"data_referencia": invalida})

        assert rotina_dublada == [], "a rotina nao deveria ter sido chamada"

    @pytest.mark.parametrize(
        "valor,esperado",
        [(True, True), (False, False), ("sim", True), ("", False), (1, True), (0, False)],
    )
    def test_modo_consulta_e_coagido_para_booleano(
        self, rotina_dublada: list[dict[str, Any]], valor: object, esperado: bool
    ) -> None:
        handler.lambda_handler({"modo_consulta": valor})

        assert rotina_dublada[0]["modo_consulta"] is esperado

    def test_resposta_carrega_o_registro_da_execucao(
        self, rotina_dublada: list[dict[str, Any]]
    ) -> None:
        resposta = handler.lambda_handler({})

        assert resposta["statusCode"] == 200
        assert resposta["body"] == _resultado_qualquer().como_registro()
