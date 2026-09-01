# Biblioteca Padrão — Referência

## Pathlib

```python
from pathlib import Path
from tempfile import TemporaryDirectory
from typing import Any

raiz = Path(__file__).parent.parent
config = raiz / "config" / "settings.toml"

def ler_config(caminho: Path) -> dict[str, str]:
    if not caminho.exists():
        raise FileNotFoundError(f"Config não encontrada: {caminho}")
    conteudo = caminho.read_text(encoding="utf-8")
    return parsear_config(conteudo)

def buscar_python(diretorio: Path) -> list[Path]:
    return list(diretorio.rglob("*.py"))

def garantir_diretorio(caminho: Path) -> None:
    caminho.mkdir(parents=True, exist_ok=True)

def processar_com_temp() -> None:
    with TemporaryDirectory() as tmpdir:
        temp = Path(tmpdir) / "saida.txt"
        temp.write_text("dados")
```

## Dataclasses

```python
from dataclasses import dataclass, field, asdict, replace
from typing import Any, ClassVar

@dataclass
class Usuario:
    id: int
    nome: str
    email: str
    ativo: bool = True

@dataclass
class Produto:
    nome: str
    preco: float
    desconto: float = 0.0

    def __post_init__(self) -> None:
        if self.desconto > 1.0:
            raise ValueError("Desconto deve ser <= 1.0")

    @property
    def preco_final(self) -> float:
        return self.preco * (1 - self.desconto)

@dataclass
class Carrinho:
    user_id: int
    itens: list[str] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)

@dataclass(frozen=True)
class Ponto:
    x: float
    y: float

    def distancia(self, outro: "Ponto") -> float:
        return ((self.x - outro.x)**2 + (self.y - outro.y)**2)**0.5

@dataclass
class Config:
    API_VERSION: ClassVar[str] = "v1"
    BASE_URL: ClassVar[str] = "https://api.exemplo.com"
    timeout: int = 30

@dataclass(order=True)
class Prioridade:
    nivel: int
    nome: str = field(compare=False)

# Converter de/para dict
u = Usuario(1, "Alice", "alice@exemplo.com")
d = asdict(u)
u2 = replace(u, nome="Alice Silva")
```

## Functools

```python
from functools import cache, lru_cache, cached_property, partial, wraps, reduce, singledispatch
from typing import Any, Callable, ParamSpec, TypeVar

P = ParamSpec('P')
R = TypeVar('R')

@cache
def fibonacci(n: int) -> int:
    if n < 2:
        return n
    return fibonacci(n - 1) + fibonacci(n - 2)

@lru_cache(maxsize=128)
def buscar_usuario(user_id: int) -> dict[str, Any]:
    return {"id": user_id, "nome": "Usuário"}

class Processador:
    def __init__(self, dados: list[int]) -> None:
        self._dados = dados

    @cached_property
    def media(self) -> float:
        return sum(self._dados) / len(self._dados)

from operator import mul
dobro = partial(mul, 2)

def cronometrar(func: Callable[P, R]) -> Callable[P, R]:
    @wraps(func)
    def wrapper(*args: P.args, **kwargs: P.kwargs) -> R:
        inicio = time.time()
        resultado = func(*args, **kwargs)
        print(f"{func.__name__} levou {time.time() - inicio:.2f}s")
        return resultado
    return wrapper

total = reduce(lambda a, b: a + b, [1, 2, 3, 4, 5])

@singledispatch
def processar(arg: Any) -> str:
    return f"Tipo desconhecido: {type(arg)}"

@processar.register
def _(arg: int) -> str:
    return f"Inteiro: {arg * 2}"

@processar.register
def _(arg: str) -> str:
    return f"String: {arg.upper()}"

@processar.register(list)
def _(arg: list[Any]) -> str:
    return f"Lista com {len(arg)} itens"
```

## Itertools

```python
from itertools import chain, islice, groupby, accumulate, combinations, permutations, product, zip_longest, tee, filterfalse

combinado = list(chain([1, 2], [3, 4], [5, 6]))
primeiros = list(islice(range(1000), 10))

dados = [("A", 1), ("A", 2), ("B", 1)]
agrupado = {k: list(v) for k, v in groupby(dados, key=lambda x: x[0])}

soma_acumulada = list(accumulate([1, 2, 3, 4, 5]))

combos = list(combinations([1, 2, 3], 2))
pares = list(product([1, 2], ['a', 'b']))

emparelhado = list(zip_longest([1, 2], ['a', 'b', 'c'], fillvalue=0))

it1, it2 = tee(range(5), 2)
impares = list(filterfalse(lambda x: x % 2 == 0, range(10)))
```

## Collections

```python
from collections import defaultdict, Counter, deque, namedtuple, ChainMap

indice: defaultdict[str, list[int]] = defaultdict(list)
for i, palavra in enumerate(["ola", "mundo", "ola"]):
    indice[palavra].append(i)

contagem = Counter(["maca", "banana", "maca", "cereja"])
print(contagem.most_common(2))

fila: deque[str] = deque()
fila.append("primeiro")
fila.appendleft("prioridade")
item = fila.popleft()

recente: deque[int] = deque(maxlen=3)
for i in range(5):
    recente.append(i)  # mantém só os últimos 3

Ponto = namedtuple('Ponto', ['x', 'y'])

padrao = {'cor': 'vermelho', 'usuario': 'convidado'}
ambiente = {'usuario': 'admin'}
combinado = ChainMap(ambiente, padrao)
```

## Context managers

```python
from contextlib import contextmanager, suppress, ExitStack
from typing import Iterator

@contextmanager
def recurso_gerenciado(recurso_id: str) -> Iterator[Any]:
    recurso = adquirir_recurso(recurso_id)
    try:
        yield recurso
    finally:
        liberar_recurso(recurso)

with suppress(FileNotFoundError):
    Path("inexistente.txt").unlink()

def processar_arquivos(nomes: list[str]) -> None:
    with ExitStack() as stack:
        arquivos = [stack.enter_context(open(n)) for n in nomes]
        for f in arquivos:
            processar(f.read())
```

## Enum

```python
from enum import Enum, auto, IntEnum, Flag

class Status(Enum):
    PENDENTE = "pendente"
    APROVADO = "aprovado"
    REJEITADO = "rejeitado"

class Cor(Enum):
    VERMELHO = auto()
    VERDE = auto()
    AZUL = auto()

class Prioridade(IntEnum):
    BAIXA = 1
    MEDIA = 2
    ALTA = 3

class Permissao(Flag):
    LEITURA = auto()
    ESCRITA = auto()
    EXECUCAO = auto()

perms = Permissao.LEITURA | Permissao.ESCRITA
if Permissao.LEITURA in perms:
    print("Pode ler")
```

## Logging

```python
import logging

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('app.log'),
        logging.StreamHandler()
    ]
)

logger = logging.getLogger(__name__)

def processar_usuario(user_id: int) -> None:
    logger.info("Processando usuário", extra={"user_id": user_id})
    try:
        pass
    except Exception:
        logger.exception("Falha ao processar usuário", extra={"user_id": user_id})
```
