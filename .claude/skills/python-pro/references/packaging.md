# Empacotamento Python — Referência

## Estrutura de projeto

```
meuprojeto/
├── pyproject.toml
├── README.md
├── .gitignore
├── .python-version
├── src/
│   └── meuprojeto/
│       ├── __init__.py
│       ├── py.typed
│       ├── core.py
│       └── utils.py
├── tests/
│   ├── __init__.py
│   ├── conftest.py
│   └── test_core.py
└── docs/
    └── index.md
```

## pyproject.toml (setuptools/hatchling)

```toml
[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

[project]
name = "meuprojeto"
version = "0.1.0"
description = "Um projeto Python"
readme = "README.md"
requires-python = ">=3.11"
license = {text = "MIT"}
authors = [{name = "Seu Nome", email = "voce@exemplo.com"}]
dependencies = [
    "requests>=2.31.0",
    "pydantic>=2.5.0",
]

[project.optional-dependencies]
dev = [
    "pytest>=7.4.0",
    "pytest-cov>=4.1.0",
    "mypy>=1.7.0",
    "black>=23.11.0",
    "ruff>=0.1.6",
]

[tool.black]
line-length = 100
target-version = ["py311"]

[tool.ruff]
line-length = 100
target-version = "py311"
select = ["E", "W", "F", "I", "B", "C4", "UP"]

[tool.mypy]
python_version = "3.11"
strict = true
warn_return_any = true
warn_unused_configs = true
disallow_untyped_defs = true

[[tool.mypy.overrides]]
module = "third_party.*"
ignore_missing_imports = true

[tool.pytest.ini_options]
minversion = "7.0"
addopts = ["-ra", "--strict-markers", "--strict-config", "--cov=meuprojeto", "--cov-report=term-missing"]
testpaths = ["tests"]
pythonpath = ["src"]
```

## Poetry

```toml
[tool.poetry]
name = "meuprojeto"
version = "0.1.0"
description = "Um projeto Python"
authors = ["Seu Nome <voce@exemplo.com>"]
readme = "README.md"
license = "MIT"
packages = [{include = "meuprojeto", from = "src"}]

[tool.poetry.dependencies]
python = "^3.11"
requests = "^2.31.0"
pydantic = "^2.5.0"

[tool.poetry.group.dev.dependencies]
pytest = "^7.4.0"
pytest-cov = "^4.1.0"
mypy = "^1.7.0"
black = "^23.11.0"
ruff = "^0.1.6"

[build-system]
requires = ["poetry-core"]
build-backend = "poetry.core.masonry.api"
```

```bash
poetry init
poetry add requests
poetry add --group dev pytest
poetry install
poetry run pytest
poetry build
poetry export -f requirements.txt --output requirements.txt
```

## Ambientes virtuais

```bash
# venv
python -m venv .venv
source .venv/bin/activate   # Linux/Mac
.venv\Scripts\activate      # Windows

# pip editável
pip install -e .
pip install -e ".[dev]"

# pyenv
pyenv install 3.11.6
pyenv local 3.11.6
```

## __init__.py

```python
"""MeuProjeto — Um pacote Python."""

from meuprojeto.core import funcao_principal, ClasseCore
from meuprojeto.utils import funcao_auxiliar

__version__ = "0.1.0"
__all__ = ["funcao_principal", "ClasseCore", "funcao_auxiliar"]

import logging
logger = logging.getLogger(__name__)
logger.addHandler(logging.NullHandler())
```

## py.typed

Arquivo vazio em `src/meuprojeto/py.typed` indica que o pacote inclui type hints (PEP 561).

## CLI

```python
# src/meuprojeto/cli.py
import sys
from typing import NoReturn

def main() -> NoReturn:
    """Ponto de entrada CLI."""
    print("MeuProjeto CLI")
    sys.exit(0)

if __name__ == "__main__":
    main()
```

## requirements.txt

```txt
# requirements.txt — produção
requests>=2.31.0,<3.0.0
pydantic>=2.5.0,<3.0.0

# requirements-dev.txt — desenvolvimento
-r requirements.txt
pytest>=7.4.0
pytest-cov>=4.1.0
mypy>=1.7.0
black>=23.11.0
ruff>=0.1.6
```

## Build e distribuição

```bash
python -m build
twine check dist/*
twine upload dist/*
```

## Boas práticas de dependências

- Aplicações: versões pinadas (`requests==2.31.0`).
- Bibliotecas: ranges (`requests>=2.31.0,<3.0.0`).
- Lock files: `poetry.lock` ou `requirements-lock.txt` gerado com `pip freeze`.

## CI/CD (exemplo GitHub Actions)

```yaml
name: Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        python-version: ["3.11", "3.12"]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: ${{ matrix.python-version }}
      - run: pip install -e ".[dev]"
      - run: pytest
```
