---

name: python-pro
description: "Referência para Python 3.11+ production-ready — type hints com mypy strict, async/await com TaskGroup, dataclasses, pytest com fixtures e mocking, ruff/black. Use ao gerar ou revisar código Python, especialmente o `apps/expurgo-particao` (Lambda). Uso: sessão principal ou invocação manual via `/python-pro`; não carregar proativamente."
license: MIT
metadata:
  author: https://github.com/srportto/srportto
  version: "1.0.0"
  domain: language
  triggers: Python, type hints, mypy, pytest, async, dataclass, ruff, black, Lambda, expurgo-particao
  role: specialist
  scope: implementation
  output-format: code
  related-skills: qualidade-codigo-java, devops-cicd
---
---

# Python Pro

Especialista em Python 3.11+ focado em código **type-safe, async-first e production-ready**. No
monorepo, aplica-se ao `apps/expurgo-particao` (Lambda Python que fecha o ring buffer de expurgo)
e a qualquer script utilitário Python que venha a existir.

**Quando NÃO usar:** para código Java, use `qualidade-codigo-java`. Para pipeline/Docker/K8s de
qualquer serviço, use `devops-cicd`.

## Workflow

1. **Analise** — estrutura, dependências, cobertura de tipos e de testes existentes.
2. **Modele** — defina `Protocol`s, `dataclass`es e type aliases antes de implementar.
3. **Implemente** — código Pythonic com type hints completos e tratamento de erro explícito.
4. **Teste** — suite pytest com fixtures, `parametrize` e mocking; cobertura > 90% no caminho feliz.
5. **Valide** — `mypy --strict`, `black`, `ruff`. Se falhar: corrija e re-execute antes de prosseguir.

## Referências (carregue sob demanda)

| Tópico | Referência | Carregue quando |
|--------|-----------|-----------------|
| Sistema de tipos | `references/type-system.md` | Type hints, mypy, generics, `Protocol` |
| Padrões async | `references/async-patterns.md` | `async`/`await`, `asyncio.TaskGroup`, context managers async |
| Biblioteca padrão | `references/standard-library.md` | `pathlib`, `dataclasses`, `functools`, `itertools` |
| Testes | `references/testing.md` | pytest, fixtures, mocking, `parametrize` |
| Empacotamento | `references/packaging.md` | `pyproject.toml`, poetry/pip, distribuição |

## Regras

### FAÇA
- Type hints em **toda** assinatura pública e atributo de classe.
- `X | None` em vez de `Optional[X]` (Python 3.10+).
- `dataclass` em vez de `__init__` manual para estruturas de dados.
- `pathlib` em vez de `os.path`.
- Context managers (`with`) para qualquer recurso (arquivo, conexão, lock).
- `async`/`await` para operações I/O-bound; `asyncio.TaskGroup` para concorrência estruturada.
- Docstrings estilo Google em módulos, classes e funções públicas.

### NÃO FAÇA
- Omitir type annotations em API pública.
- Usar argumento default mutável (`def f(items: list = [])`).
- Misturar sync e async sem fronteira clara (`asyncio.run` no topo, nunca dentro de handler async).
- Ignorar erro do `mypy --strict` — corrija ou justifique com `# type: ignore` pontual e comentado.
- Usar `except:` nu — sempre `except SpecificError:`.
- Hardcodar segredo ou configuração — leia de env var / secret manager.

## Exemplos sucintos

### Função type-annotated com tratamento de erro

```python
from pathlib import Path

def ler_config(path: Path) -> dict[str, str]:
    """Lê configuração chave=valor de um arquivo.

    Raises:
        FileNotFoundError: se o arquivo não existir.
        ValueError: se uma linha não puder ser parseada.
    """
    config: dict[str, str] = {}
    with path.open() as f:
        for linha in f:
            chave, _, valor = linha.partition("=")
            if not chave.strip():
                raise ValueError(f"Linha inválida: {linha!r}")
            config[chave.strip()] = valor.strip()
    return config
```

### Dataclass com validação

```python
from dataclasses import dataclass, field

@dataclass
class ConfigApp:
    host: str
    porta: int
    debug: bool = False
    origens: list[str] = field(default_factory=list)

    def __post_init__(self) -> None:
        if not (1 <= self.porta <= 65535):
            raise ValueError(f"Porta inválida: {self.porta}")
```

### Async com TaskGroup (Python 3.11+)

```python
import asyncio
import httpx

async def buscar_todos(urls: list[str]) -> list[bytes]:
    """Busca múltiplas URLs concorrentemente."""
    async with httpx.AsyncClient() as client, asyncio.TaskGroup() as tg:
        tarefas = [tg.create_task(client.get(url)) for url in urls]
    return [t.result().content for t in tarefas]
```

### pytest com fixture e parametrize

```python
import pytest
from pathlib import Path

@pytest.fixture
def arquivo_config(tmp_path: Path) -> Path:
    cfg = tmp_path / "config.txt"
    cfg.write_text("host=localhost\nporta=8080\n")
    return cfg

@pytest.mark.parametrize("porta,valida", [(8080, True), (0, False), (99999, False)])
def test_validacao_porta(porta: int, valida: bool) -> None:
    if valida:
        ConfigApp(host="localhost", porta=porta)
    else:
        with pytest.raises(ValueError):
            ConfigApp(host="localhost", porta=porta)
```

### Configuração mypy strict (`pyproject.toml`)

```toml
[tool.mypy]
python_version = "3.11"
strict = true
warn_return_any = true
warn_unused_configs = true
disallow_untyped_defs = true
```

Saída limpa esperada: `Success: no issues found in N source files`. Qualquer erro reportado deve
ser resolvido antes de considerar a implementação concluída.

## Saída esperada ao implementar

1. Módulo com type hints completos.
2. Arquivo de teste com fixtures pytest.
3. Confirmação de que `mypy --strict` passa.
4. Breve explicação dos padrões Pythonic usados.

## Conhecimento de referência

Python 3.11+, `typing`, mypy, pytest, black, ruff, `dataclasses`, `async`/`await`,
`asyncio.TaskGroup`, `pathlib`, `functools`, `itertools`, `Protocol`, `contextlib`,
`collections.abc`.
