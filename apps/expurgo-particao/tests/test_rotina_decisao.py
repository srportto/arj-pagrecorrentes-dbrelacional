"""Árvore de decisão do expurgo, exercitada com duplo de conexão -- sem Postgres.

Por que existe: `test_rotina_integracao.py` prova que o `TRUNCATE` real esvazia a gaveta
certa, mas é excluído da esteira (exige banco no ar). Sem este arquivo, a lógica que
**decide destruir dado** não teria verificação automática nenhuma. A divisão é: aqui se
protege a decisão; lá, o efeito.

O duplo responde pelo texto do SQL, não pela ordem das chamadas, para não quebrar quando
uma consulta é acrescentada ou removida de `persistencia.py`.
"""

from __future__ import annotations

import datetime as dt
from types import TracebackType
from typing import Any

import psycopg
import pytest

from expurgo_particao import rotina
from expurgo_particao.estado import Acao, EstadoParticao

DATA_REFERENCIA = dt.date(2026, 8, 22)
PARTICAO_ESCRITA = 955
PARTICAO_ALVO = 957
SEMANA = 2955

DATA_CICLO_ANTERIOR = dt.datetime(2024, 10, 3, 12, 0)  # ~98 semanas antes
DATA_RECENTE = dt.datetime(2026, 8, 17, 12, 0)  # 5 dias antes


class CursorFalso:
    """Responde às consultas de `persistencia.py` pelo texto do SQL."""

    def __init__(self, conexao: ConexaoFalsa) -> None:
        self._conexao = conexao
        self._proxima_linha: tuple[Any, ...] | None = None

    def __enter__(self) -> CursorFalso:
        return self

    def __exit__(self, *_: object) -> None:
        return None

    def execute(self, query: Any, params: Any = None) -> None:
        texto = query.as_string() if hasattr(query, "as_string") else str(query)
        self._conexao.sql_executado.append(texto)

        if self._conexao.erro_ao_ler is not None and ("EXISTS" in texto or "max(" in texto):
            raise self._conexao.erro_ao_ler

        if "EXISTS" in texto:
            self._proxima_linha = (self._conexao.data_mais_recente is not None,)
        elif "max(" in texto:
            self._proxima_linha = (self._conexao.data_mais_recente,)
        elif "INSERT INTO" in texto:
            self._conexao.registros_gravados.append(params)
            self._proxima_linha = None
        else:
            self._proxima_linha = None

    def fetchone(self) -> tuple[Any, ...] | None:
        return self._proxima_linha


class ConexaoFalsa:
    """Registra commits, rollbacks e SQL executado, sem tocar em banco algum."""

    def __init__(
        self,
        data_mais_recente: dt.datetime | None = None,
        erro_ao_ler: Exception | None = None,
    ) -> None:
        self.data_mais_recente = data_mais_recente
        self.erro_ao_ler = erro_ao_ler
        self.sql_executado: list[str] = []
        self.registros_gravados: list[Any] = []
        # Historico ordenado de commit/rollback: e' o que prova a atomicidade.
        self.transacoes: list[str] = []

    def __enter__(self) -> ConexaoFalsa:
        return self

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        tb: TracebackType | None,
    ) -> None:
        return None

    def cursor(self) -> CursorFalso:
        return CursorFalso(self)

    def commit(self) -> None:
        self.transacoes.append("commit")

    def rollback(self) -> None:
        self.transacoes.append("rollback")

    # --- consultas de conveniencia usadas pelas assercoes ---

    @property
    def truncou(self) -> bool:
        return any("TRUNCATE" in s for s in self.sql_executado)

    @property
    def gravou_registro(self) -> bool:
        return any("INSERT INTO" in s for s in self.sql_executado)

    def indice_do(self, marcador: str) -> int:
        for i, s in enumerate(self.sql_executado):
            if marcador in s:
                return i
        raise AssertionError(f"{marcador} nao foi executado; SQL: {self.sql_executado}")


@pytest.fixture
def conectar(monkeypatch: pytest.MonkeyPatch) -> Any:
    """Devolve uma funcao que instala a ConexaoFalsa no lugar de psycopg.connect."""

    def _instalar(conexao: ConexaoFalsa) -> ConexaoFalsa:
        monkeypatch.setattr(psycopg, "connect", lambda *a, **k: conexao)
        return conexao

    return _instalar


class TestCaminhosSemEscrita:
    def test_particao_vazia_nao_trunca_e_registra_nenhuma(self, conectar: Any) -> None:
        conexao = conectar(ConexaoFalsa(data_mais_recente=None))

        resultado = rotina.executar("dsn-falsa", data_referencia=DATA_REFERENCIA)

        assert resultado.estado == EstadoParticao.VAZIA
        assert resultado.acao == Acao.NENHUMA
        assert not conexao.truncou
        assert conexao.gravou_registro

    def test_dado_recente_e_recusado_sem_truncar(self, conectar: Any) -> None:
        conexao = conectar(ConexaoFalsa(data_mais_recente=DATA_RECENTE))

        resultado = rotina.executar("dsn-falsa", data_referencia=DATA_REFERENCIA)

        assert resultado.estado == EstadoParticao.DADO_RECENTE
        assert resultado.acao == Acao.RECUSA_DADO_RECENTE
        assert not conexao.truncou
        assert "rollback" in conexao.transacoes

    def test_modo_consulta_nao_trunca_mesmo_com_dado_esvaziavel(self, conectar: Any) -> None:
        conexao = conectar(ConexaoFalsa(data_mais_recente=DATA_CICLO_ANTERIOR))

        resultado = rotina.executar(
            "dsn-falsa", data_referencia=DATA_REFERENCIA, modo_consulta=True
        )

        assert resultado.estado == EstadoParticao.DADO_CICLO_ANTERIOR
        assert resultado.acao == Acao.NENHUMA
        assert resultado.modo_consulta is True
        assert not conexao.truncou

    def test_lock_timeout_nao_observa_estado_algum(self, conectar: Any) -> None:
        erro = psycopg.errors.LockNotAvailable("lock_timeout esgotado")
        conexao = conectar(ConexaoFalsa(data_mais_recente=None, erro_ao_ler=erro))

        resultado = rotina.executar("dsn-falsa", data_referencia=DATA_REFERENCIA)

        # estado None, nao VAZIA: a verificacao nem chegou a rodar.
        assert resultado.estado is None
        assert resultado.acao == Acao.RECUSA_LOCK_TIMEOUT
        assert not conexao.truncou
        assert conexao.gravou_registro


class TestCaminhoDeEsvaziamento:
    def test_dado_do_ciclo_anterior_e_esvaziado(self, conectar: Any) -> None:
        conexao = conectar(ConexaoFalsa(data_mais_recente=DATA_CICLO_ANTERIOR))

        resultado = rotina.executar("dsn-falsa", data_referencia=DATA_REFERENCIA)

        assert resultado.estado == EstadoParticao.DADO_CICLO_ANTERIOR
        assert resultado.acao == Acao.TRUNCATE
        assert conexao.truncou

    def test_calculo_relatado_bate_com_a_particao_tocada(self, conectar: Any) -> None:
        conectar(ConexaoFalsa(data_mais_recente=DATA_CICLO_ANTERIOR))

        resultado = rotina.executar("dsn-falsa", data_referencia=DATA_REFERENCIA)

        assert resultado.semana == SEMANA
        assert resultado.particao_escrita == PARTICAO_ESCRITA
        assert resultado.particao_alvo == PARTICAO_ALVO

    def test_truncate_e_registro_sao_a_mesma_transacao(self, conectar: Any) -> None:
        """O ponto central da change: nao pode existir gaveta esvaziada sem registro."""
        conexao = conectar(ConexaoFalsa(data_mais_recente=DATA_CICLO_ANTERIOR))

        rotina.executar("dsn-falsa", data_referencia=DATA_REFERENCIA)

        assert conexao.truncou and conexao.gravou_registro
        assert conexao.indice_do("TRUNCATE") < conexao.indice_do("INSERT INTO")
        # Um unico commit apos os dois: se houvesse commit entre eles, existiria uma
        # janela em que a particao esta vazia e o registro nao existe.
        assert conexao.transacoes.count("commit") == 1


class TestDesarmeOperacional:
    """`EXPURGO_PARTICAO_DESARMAR_TRUNCATE` impede o esvaziamento sem parar o registro."""

    def test_desarme_nao_trunca(self, conectar: Any) -> None:
        conexao = conectar(ConexaoFalsa(data_mais_recente=DATA_CICLO_ANTERIOR))

        rotina.executar(
            "dsn-falsa",
            data_referencia=DATA_REFERENCIA,
            ambiente={"EXPURGO_PARTICAO_DESARMAR_TRUNCATE": "true"},
        )

        assert not conexao.truncou

    def test_desarme_e_registrado_como_acao_propria(self, conectar: Any) -> None:
        """Sem acao propria, o auditor teria que deduzir o desarme cruzando estado e acao."""
        conectar(ConexaoFalsa(data_mais_recente=DATA_CICLO_ANTERIOR))

        resultado = rotina.executar(
            "dsn-falsa",
            data_referencia=DATA_REFERENCIA,
            ambiente={"EXPURGO_PARTICAO_DESARMAR_TRUNCATE": "true"},
        )

        assert resultado.acao == Acao.RECUSA_DESARMADO
        assert resultado.estado == EstadoParticao.DADO_CICLO_ANTERIOR

    def test_modo_consulta_continua_registrando_nenhuma(self, conectar: Any) -> None:
        """modo_consulta ja e' explicito na propria coluna -- nao precisa de acao propria."""
        conectar(ConexaoFalsa(data_mais_recente=DATA_CICLO_ANTERIOR))

        resultado = rotina.executar(
            "dsn-falsa", data_referencia=DATA_REFERENCIA, modo_consulta=True
        )

        assert resultado.acao == Acao.NENHUMA


class TestFalhaNaoPrevista:
    """Falha precisa deixar rastro: ausencia de registro significa 'rotina parada'."""

    def test_erro_inesperado_e_registrado_como_falha(self, conectar: Any) -> None:
        erro = psycopg.errors.UndefinedTable("relation autorizacoes_pe957 does not exist")
        conexao = conectar(ConexaoFalsa(erro_ao_ler=erro))

        with pytest.raises(psycopg.errors.UndefinedTable):
            rotina.executar("dsn-falsa", data_referencia=DATA_REFERENCIA)

        assert conexao.gravou_registro, "falha sem registro e' indistinguivel de rotina parada"

    def test_erro_inesperado_e_propagado(self, conectar: Any) -> None:
        """Engolir o erro zeraria a metrica de falha da Lambda -- registra e re-lanca."""
        erro = psycopg.errors.InsufficientPrivilege("permission denied")
        conectar(ConexaoFalsa(erro_ao_ler=erro))

        with pytest.raises(psycopg.errors.InsufficientPrivilege):
            rotina.executar("dsn-falsa", data_referencia=DATA_REFERENCIA)

    def test_falha_nao_trunca(self, conectar: Any) -> None:
        erro = psycopg.errors.UndefinedTable("relation does not exist")
        conexao = conectar(ConexaoFalsa(erro_ao_ler=erro))

        with pytest.raises(psycopg.errors.UndefinedTable):
            rotina.executar("dsn-falsa", data_referencia=DATA_REFERENCIA)

        assert not conexao.truncou
