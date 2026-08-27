# Grafo de Conhecimento (graphify)

> Opcional. Não é necessário para rodar, buildar ou desenvolver neste repositório — serve apenas
> para acelerar a navegação por IA (e por humanos) no monorepo. Este é o único arquivo desta pasta
> versionado no Git; todo o resto de `graphify-out/` (grafo, cache, HTML, relatório) é gerado
> localmente e ignorado (veja o [.gitignore](../.gitignore)).

Este repositório é grande o suficiente (5 microserviços Java + 1 Lambda Python) que ler todos os
arquivos do zero a cada pergunta gasta muito contexto/tokens. A skill graphify transforma o código
em um grafo de conhecimento persistente (nesta mesma pasta, `graphify-out/`), permitindo que um
agente de IA responda perguntas sobre arquitetura, fluxos e relação entre arquivos consultando o
grafo em vez de reler o repositório inteiro — **reduzindo bastante o consumo de tokens** em sessões
de IA.

Cada desenvolvedor que quiser esse benefício deve gerar o grafo localmente (não é compartilhado via
Git, pois pode ficar desatualizado e não agrega valor versionado).

## Instalação

Requer Python 3.10+ instalado.

### Windows (PowerShell)

```powershell
# Com uv (recomendado)
uv tool install graphifyy

# Ou com pip
pip install graphifyy
```

### macOS

```bash
# Com uv (recomendado)
uv tool install graphifyy

# Ou com pip
pip install graphifyy

# Ou via pipx
pipx install graphifyy
```

### Linux

```bash
# Com uv (recomendado)
uv tool install graphifyy

# Ou com pip
pip install graphifyy

# Ou via pipx
pipx install graphifyy
```

> `uv` pode ser instalado a partir de https://docs.astral.sh/uv/getting-started/installation/,
> disponível para Windows, macOS e Linux.

## Gerando o grafo

Na raiz deste repositório, com o Claude Code (ou outra ferramenta compatível com a skill graphify):

```
/graphify
```

Isso preenche esta pasta (`graphify-out/`) com o grafo (`graph.json`), visualização HTML
(`graph.html`) e um relatório em linguagem natural (`GRAPH_REPORT.md`). Para atualizar depois de
mudanças no código:

```
/graphify --update
```

## Consultando

```
/graphify query "<pergunta sobre o código>"
/graphify explain "<nome de classe/módulo>"
/graphify path "<origem>" "<destino>"
```

Veja o [CLAUDE.md](../CLAUDE.md) deste repositório: a orientação já embutida é usar o grafo (quando
`graphify-out/` existir) antes de ler arquivos diretamente.
