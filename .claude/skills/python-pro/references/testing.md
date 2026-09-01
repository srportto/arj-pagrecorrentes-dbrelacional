# Testes com pytest — Referência

## Estrutura básica

```python
import pytest

def test_criacao_usuario() -> None:
    usuario = Usuario(id=1, nome="Alice", email="alice@exemplo.com")
    assert usuario.nome == "Alice"
    assert usuario.ativo is True

def test_validacao_usuario() -> None:
    with pytest.raises(ValueError, match="Email inválido"):
        Usuario(id=1, nome="Alice", email="invalido")

class TestUsuarioService:
    def test_buscar(self) -> None:
        servico = UsuarioService()
        usuario = servico.buscar(1)
        assert usuario is not None
```

## Fixtures

```python
from typing import Iterator

@pytest.fixture
def banco() -> Iterator[Banco]:
    db = Banco("test.db")
    db.criar_tabelas()
    yield db
    db.dropar_tabelas()
    db.fechar()

@pytest.fixture
def sessao(banco: Banco) -> Iterator[Sessao]:
    sessao = banco.criar_sessao()
    yield sessao
    sessao.rollback()
    sessao.fechar()

@pytest.fixture
def usuario_padrao() -> Usuario:
    return Usuario(id=1, nome="Teste", email="teste@exemplo.com")

@pytest.fixture(autouse=True)
def resetar_estado() -> Iterator[None]:
    limpar_caches()
    yield
    limpar_temporarios()
```

## Parametrize

```python
@pytest.mark.parametrize("entrada,esperado", [(2, 4), (3, 9), (4, 16), (-2, 4)])
def test_quadrado(entrada: int, esperado: int) -> None:
    assert quadrado(entrada) == esperado

@pytest.mark.parametrize("base", [2, 10])
@pytest.mark.parametrize("expoente", [0, 1, 2])
def test_potencia(base: int, expoente: int) -> None:
    assert base ** expoente >= 0

@pytest.mark.parametrize("email,valido", [
    ("user@exemplo.com", True),
    ("invalido", False),
], ids=["valido", "sem_arroba"])
def test_validacao_email(email: str, valido: bool) -> None:
    assert email_valido(email) == valido

@pytest.fixture
def fabrica_usuario():
    def _criar(nome: str, ativo: bool = True) -> Usuario:
        return Usuario(nome=nome, ativo=ativo)
    return _criar

@pytest.mark.parametrize("nome", ["Alice", "Bob", "Charlie"])
def test_nomes(fabrica_usuario, nome: str) -> None:
    usuario = fabrica_usuario(nome)
    assert usuario.nome == nome
```

## Mocking

```python
from unittest.mock import Mock, patch, AsyncMock

def test_chamada_api_com_mock() -> None:
    cliente_mock = Mock()
    cliente_mock.get.return_value = {"status": "ok"}
    servico = ApiService(cliente_mock)
    resultado = servico.buscar_dados()
    cliente_mock.get.assert_called_once_with("/api/dados")
    assert resultado["status"] == "ok"

def test_chamada_banco() -> None:
    with patch("meuapp.banco.conectar") as mock_conectar:
        mock_conectar.return_value = Mock()
        db = Banco()
        db.conectar()
        mock_conectar.assert_called_once()

@patch("meuapp.usuario.enviar_email")
def test_registro(mock_enviar: Mock) -> None:
    servico = UsuarioService()
    servico.registrar("user@exemplo.com")
    mock_enviar.assert_called_with(para="user@exemplo.com", assunto="Bem-vindo")

def test_logica_retry() -> None:
    api_mock = Mock()
    api_mock.chamar.side_effect = [ConnectionError("Falhou"), {"status": "ok"}]
    resultado = retry_chamada(api_mock)
    assert resultado["status"] == "ok"
    assert api_mock.chamar.call_count == 2

@pytest.mark.asyncio
async def test_funcao_async() -> None:
    banco_mock = AsyncMock()
    banco_mock.buscar_usuario.return_value = Usuario(id=1, nome="Alice")
    servico = UsuarioServiceAsync(banco_mock)
    usuario = await servico.obter_usuario(1)
    banco_mock.buscar_usuario.assert_awaited_once_with(1)
```

## Testes async

```python
@pytest.mark.asyncio
async def test_busca_async() -> None:
    resultado = await buscar_dados("https://api.exemplo.com")
    assert resultado["status"] == "ok"

@pytest.fixture
async def banco_async() -> AsyncIterator[BancoAsync]:
    db = BancoAsync()
    await db.conectar()
    yield db
    await db.desconectar()

@pytest.mark.asyncio
async def test_concorrente() -> None:
    urls = ["http://exemplo.com/1", "http://exemplo.com/2"]
    resultados = await asyncio.gather(*[buscar(u) for u in urls])
    assert len(resultados) == 2
```

## Marcadores

```python
@pytest.mark.skip(reason="Não implementado ainda")
def test_futuro() -> None:
    pass

@pytest.mark.skipif(sys.version_info < (3, 11), reason="Requer Python 3.11+")
def test_novo_recurso() -> None:
    pass

@pytest.mark.xfail(reason="Bug conhecido #123")
def test_bug_conhecido() -> None:
    assert funcao_bugada() == esperado

@pytest.mark.lento
def test_operacao_lenta() -> None:
    time.sleep(5)

# Executar: pytest -m "not lento"
```

## Cobertura

```toml
[tool.pytest.ini_options]
minversion = "7.0"
addopts = [
    "--cov=meuapp",
    "--cov-report=term-missing",
    "--cov-fail-under=90",
    "-ra",
    "--strict-markers",
]
testpaths = ["tests"]
```

## Testes baseados em propriedade (hypothesis)

```python
from hypothesis import given, strategies as st

@given(st.integers(), st.integers())
def test_adicao_comutativa(a: int, b: int) -> None:
    assert a + b == b + a

@given(st.lists(st.integers()))
def test_ordenado(lst: list[int]) -> None:
    ordenado = sorted(lst)
    for i in range(len(ordenado) - 1):
        assert ordenado[i] <= ordenado[i + 1]
```

## Organização de testes

```python
# tests/
#   conftest.py          — fixtures compartilhadas
#   test_usuario.py
#   test_api.py
#   integracao/
#     test_fluxo.py
#   unitario/
#     test_modelos.py

@pytest.fixture
def fabrica_usuario(sessao: Sessao):
    criados: list[Usuario] = []
    def _criar(nome: str = "Teste", email: str | None = None) -> Usuario:
        if email is None:
            email = f"{nome.lower().replace(' ', '.')}@exemplo.com"
        usuario = Usuario(nome=nome, email=email)
        sessao.add(usuario)
        sessao.commit()
        criados.append(usuario)
        return usuario
    yield _criar
    for u in criados:
        sessao.delete(u)
    sessao.commit()
```
