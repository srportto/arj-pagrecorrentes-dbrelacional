# Sistema de Tipos — Referência

## Anotações básicas

```python
from typing import Any
from collections.abc import Sequence, Mapping

def processar_usuario(nome: str, idade: int, ativo: bool = True) -> dict[str, Any]:
    return {"nome": nome, "idade": idade, "ativo": ativo}

# União com | (Python 3.10+)
def buscar_usuario(user_id: int | str) -> dict[str, Any] | None:
    if isinstance(user_id, int):
        return {"id": user_id}
    return None

# Coleções — prefira collections.abc
def processar_itens(itens: Sequence[str]) -> list[str]:
    return [i.upper() for i in itens]

def mesclar_configs(base: Mapping[str, int], override: dict[str, int]) -> dict[str, int]:
    return {**base, **override}
```

## Tipos genéricos

```python
from typing import TypeVar, Generic

T = TypeVar('T')
K = TypeVar('K')
V = TypeVar('V')

def primeiro_elemento(itens: Sequence[T]) -> T | None:
    return itens[0] if itens else None

class Cache(Generic[K, V]):
    def __init__(self) -> None:
        self._dados: dict[K, V] = {}

    def obter(self, chave: K) -> V | None:
        return self._dados.get(chave)

    def definir(self, chave: K, valor: V) -> None:
        self._dados[chave] = valor

# TypeVar com restrição
from numbers import Number
NumT = TypeVar('NumT', bound=Number)

def somar(a: NumT, b: NumT) -> NumT:
    return a + b  # type: ignore[return-value]
```

## Protocol para tipagem estrutural

```python
from typing import Protocol, runtime_checkable

class Desenhavel(Protocol):
    def desenhar(self) -> str: ...

    @property
    def cor(self) -> str: ...

class Circulo:
    def __init__(self, raio: float, cor: str) -> None:
        self.raio = raio
        self._cor = cor

    def desenhar(self) -> str:
        return f"Desenhando círculo {self._cor}"

    @property
    def cor(self) -> str:
        return self._cor

def renderizar(forma: Desenhavel) -> str:
    return forma.desenhar()

@runtime_checkable
class Fechavel(Protocol):
    def fechar(self) -> None: ...

def limpar(recurso: Fechavel) -> None:
    if isinstance(recurso, Fechavel):
        recurso.fechar()
```

## Tipos avançados

```python
from typing import Literal, TypeAlias, TypedDict, NotRequired, Self, overload

Modo = Literal["leitura", "escrita", "append"]

JsonDict: TypeAlias = dict[str, Any]
UserId: TypeAlias = int | str

class UsuarioDict(TypedDict):
    id: int
    nome: str
    email: str
    idade: NotRequired[int]

class Builder:
    def __init__(self) -> None:
        self._valor = 0

    def adicionar(self, n: int) -> Self:
        self._valor += n
        return self

@overload
def processar(dado: str) -> str: ...

@overload
def processar(dado: int) -> int: ...

def processar(dado: str | int) -> str | int:
    if isinstance(dado, str):
        return dado.upper()
    return dado * 2
```

## Callable e ParamSpec

```python
from collections.abc import Callable
from typing import ParamSpec, TypeVar

P = ParamSpec('P')
R = TypeVar('R')

def aplicar(func: Callable[[int, int], int], a: int, b: int) -> int:
    return func(a, b)

def logar(func: Callable[P, R]) -> Callable[P, R]:
    def wrapper(*args: P.args, **kwargs: P.kwargs) -> R:
        print(f"Chamando {func.__name__}")
        return func(*args, **kwargs)
    return wrapper
```

## Configuração mypy strict

```toml
[tool.mypy]
python_version = "3.11"
strict = true
warn_return_any = true
warn_unused_configs = true
disallow_untyped_defs = true
disallow_any_generics = true
disallow_subclassing_any = true
disallow_untyped_calls = true
disallow_incomplete_defs = true
check_untyped_defs = true
no_implicit_optional = true
warn_redundant_casts = true
warn_unused_ignores = true
warn_no_return = true
warn_unreachable = true
strict_equality = true

[[tool.mypy.overrides]]
module = "third_party.*"
ignore_missing_imports = true
```

## Padrões comuns

```python
# Result type
from dataclasses import dataclass
from typing import Generic, TypeVar

T = TypeVar('T')

@dataclass
class Sucesso(Generic[T]):
    valor: T

@dataclass
class Erro:
    mensagem: str

Resultado = Sucesso[T] | Erro

def dividir(a: int, b: int) -> Resultado[float]:
    if b == 0:
        return Erro("Divisão por zero")
    return Sucesso(a / b)

# Option/Maybe
def obter_seguro(itens: Sequence[T], indice: int) -> T | None:
    try:
        return itens[indice]
    except IndexError:
        return None

# Sentinel
from typing import Final

AUSENTE: Final = object()

def obter_valor(chave: str, default: T | type[AUSENTE] = AUSENTE) -> T:
    if default is AUSENTE:
        raise KeyError(chave)
    return default  # type: ignore[return-value]
```

## Type narrowing

```python
from typing import assert_never

def processar_valor(valor: int | str | None) -> str:
    if valor is None:
        return "null"
    if isinstance(valor, int):
        return str(valor * 2)
    return valor.upper()

def tratar_modo(modo: Literal["leitura", "escrita"]) -> str:
    if modo == "leitura":
        return "Lendo"
    elif modo == "escrita":
        return "Escrevendo"
    else:
        assert_never(modo)  # mypy detecta se modo puder ser outro valor
```
