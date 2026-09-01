# Padrões Async — Referência

## Básico

```python
import asyncio

async def buscar_dados(url: str) -> dict[str, str]:
    await asyncio.sleep(1)
    return {"url": url, "status": "ok"}

async def main() -> None:
    resultado = await buscar_dados("https://api.exemplo.com")
    print(resultado)

if __name__ == "__main__":
    asyncio.run(main())

# Concorrência com gather
async def buscar_todos(urls: list[str]) -> list[dict[str, str]]:
    tarefas = [buscar_dados(url) for url in urls]
    return await asyncio.gather(*tarefas)

# gather com tratamento de exceção
async def buscar_todos_seguro(urls: list[str]) -> list[dict[str, str] | None]:
    tarefas = [buscar_dados(url) for url in urls]
    resultados = await asyncio.gather(*tarefas, return_exceptions=True)
    return [r if not isinstance(r, Exception) else None for r in resultados]
```

## TaskGroup (Python 3.11+)

```python
from asyncio import TaskGroup

async def processar_lote(itens: list[int]) -> list[int]:
    resultados: list[int] = []
    async with TaskGroup() as tg:
        tarefas = [tg.create_task(processar_item(i)) for i in itens]
    return [t.result() for t in tarefas]

# Tratamento de erro com ExceptionGroup
async def processar_robusto(itens: list[str]) -> tuple[list[str], list[Exception]]:
    resultados: list[str] = []
    erros: list[Exception] = []
    try:
        async with TaskGroup() as tg:
            for item in itens:
                tg.create_task(processar_item_seguro(item))
    except ExceptionGroup as eg:
        for exc in eg.exceptions:
            erros.append(exc)
    return resultados, erros
```

## Context managers async

```python
from typing import Self, Any
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

class ConexaoBancoAsync:
    def __init__(self, url: str) -> None:
        self.url = url
        self._conn: Any = None

    async def __aenter__(self) -> Self:
        self._conn = await conectar(self.url)
        return self

    async def __aexit__(self, exc_type: Any, exc_val: Any, exc_tb: Any) -> None:
        if self._conn:
            await self._conn.close()

    async def query(self, sql: str) -> list[dict[str, Any]]:
        if not self._conn:
            raise RuntimeError("Não conectado")
        return await self._conn.execute(sql)

# Com @asynccontextmanager
@asynccontextmanager
async def obter_sessao() -> AsyncIterator[Any]:
    sessao = await criar_sessao()
    try:
        yield sessao
        await sessao.commit()
    except Exception:
        await sessao.rollback()
        raise
    finally:
        await sessao.close()
```

## Geradores async

```python
from collections.abc import AsyncIterator

async def ler_linhas(caminho: str) -> AsyncIterator[str]:
    async with aiofiles.open(caminho) as f:
        async for linha in f:
            yield linha.strip()

async def processar_arquivo(caminho: str) -> int:
    contador = 0
    async for linha in ler_linhas(caminho):
        await processar_linha(linha)
        contador += 1
    return contador
```

## Comprehensions async

```python
async def buscar_usuarios(ids: list[int]) -> list[Usuario]:
    return [u async for u in buscar_stream(ids)]

async def mapear_usuarios(ids: list[int]) -> dict[int, Usuario]:
    return {u.id: u async for u in buscar_stream(ids)}
```

## Primitivas de sincronização

```python
import asyncio
from typing import Any

# Lock para seção crítica
class RecursoCompartilhado:
    def __init__(self) -> None:
        self._lock = asyncio.Lock()
        self._dados: dict[str, Any] = {}

    async def atualizar(self, chave: str, valor: Any) -> None:
        async with self._lock:
            atual = self._dados.get(chave, 0)
            await asyncio.sleep(0.1)
            self._dados[chave] = atual + valor

# Semaphore para rate limiting
class LimitadorTaxa:
    def __init__(self, max_concorrente: int) -> None:
        self._semaforo = asyncio.Semaphore(max_concorrente)

    async def processar(self, item: str) -> str:
        async with self._semaforo:
            return await operacao_custosa(item)

# Event para coordenação
class WorkerAsync:
    def __init__(self) -> None:
        self._pronto = asyncio.Event()
        self._parar = asyncio.Event()

    async def iniciar(self) -> None:
        await self._inicializar()
        self._pronto.set()
        await self._parar.wait()

    async def aguardar_pronto(self) -> None:
        await self._pronto.wait()

    def parar(self) -> None:
        self._parar.set()
```

## Fila async (produtor-consumidor)

```python
from asyncio import Queue, TaskGroup

async def produtor(fila: Queue[int], n: int) -> None:
    for i in range(n):
        await fila.put(i)

async def consumidor(fila: Queue[int], nome: str) -> None:
    while True:
        item = await fila.get()
        try:
            await processar_item(item)
        finally:
            fila.task_done()

async def executar_pipeline(total: int, workers: int) -> None:
    fila: Queue[int] = Queue(maxsize=10)
    async with TaskGroup() as tg:
        tg.create_task(produtor(fila, total))
        for i in range(workers):
            tg.create_task(consumidor(fila, f"worker-{i}"))
        await fila.join()
```

## Timeouts

```python
async def buscar_com_timeout(url: str, timeout: float) -> dict[str, Any]:
    try:
        async with asyncio.timeout(timeout):
            return await buscar_dados(url)
    except TimeoutError:
        return {"erro": "timeout"}
```

## Misturando sync e async

```python
from concurrent.futures import ThreadPoolExecutor
import functools
from typing import Any, Callable, Coroutine, TypeVar

T = TypeVar('T')

# Rodar sync em executor
async def rodar_em_executor(func: Callable[..., T], *args: Any) -> T:
    loop = asyncio.get_running_loop()
    return await loop.run_in_executor(None, func, *args)

# Wrapper async para função sync
def para_async(func: Callable[..., T]) -> Callable[..., Coroutine[None, None, T]]:
    @functools.wraps(func)
    async def wrapper(*args: Any, **kwargs: Any) -> T:
        loop = asyncio.get_running_loop()
        return await loop.run_in_executor(None, functools.partial(func, *args, **kwargs))
    return wrapper
```
