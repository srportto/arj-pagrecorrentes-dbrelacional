## Context

`infra/local/postgres/dockerfile` obtém três software de três origens, com três graus de
determinismo diferentes — e nenhum deles está registrado como escolha:

| Origem | Como é obtida | Determinismo |
|---|---|---|
| Imagem base | `FROM postgres:18` | flutua **dentro** da major 18 |
| `pg_partman`, `pg_cron` | `apt` do repositório PGDG | versão curada pela distribuição para a major |
| `pgvector` | `git clone --depth 1` do branch padrão | **commit arbitrário na data do build** |

Só o terceiro é problema de verdade. `--depth 1` sem `--branch` traz o último commit do branch padrão
no instante do build: dois builds em datas diferentes produzem extensões diferentes, sem que nada no
repositório registre qual foi usada. O `LABEL` da imagem diz apenas "PostgreSQL 18 com pg_cron,
pg_partman e pgvector", sem versão.

O comentário do `dockerfile` explica **por que** compila da fonte ("para nao depender da
disponibilidade do pacote apt para o PG 18") — decisão correta e bem justificada. O que falta é a
consequência: compilar da fonte transfere para quem compila a responsabilidade de dizer *qual* fonte.

A capability `local-postgres-environment` (criada pela change `documentar-postgres-local-extensoes`)
estabelece que este ambiente existe também como demonstração de construção de PostgreSQL com
extensões. É isso que eleva o problema de descuido comum a defeito da demonstração.

## Goals / Non-Goals

**Goals:**

- Build determinístico da extensão compilada da fonte.
- Versão instalada legível sem inspecionar banco em execução.
- Registrar honestamente o que continua flutuando, em vez de sugerir determinismo integral.

**Non-Goals:**

- Fixar a imagem base por digest. Ver D2.
- Fixar os pacotes PGDG por versão exata. Ver D2.
- Atualizar o `pgvector` para uma versão mais nova do que a instalada hoje — a fixação deve, se
  possível, **preservar** o que já está no ar. Ver D3.
- Qualquer alteração em `pg_partman`, `pg_cron` ou `shared_preload_libraries`.

## Decisions

**D1. Fixar por tag de release, não por commit SHA.**

Ambos são referências imutáveis. A tag vence por legibilidade: `--branch v0.8.0` diz a versão a quem
lê o `dockerfile`, enquanto um SHA exige consultar o repositório remoto para saber o que é. A
objeção usual a tags — que podem ser movidas — não se aplica na prática a um projeto de extensão
publicada, onde mover tag de release quebraria todo mundo.

O ganho de legibilidade importa aqui mais que em geral, porque o `dockerfile` é material didático por
propósito declarado: quem o lê está aprendendo a receita, não só executando-a.

**D2. Só o `pgvector` é fixado; base e pacotes PGDG continuam flutuando, agora declaradamente.**

Fixar tudo seria coerente com "reprodutibilidade" levada ao limite, e foi considerado. Rejeitado
porque os três casos têm naturezas diferentes:

- **Imagem base `postgres:18`** — a flutuação dentro da major é justamente o mecanismo de receber
  correção de segurança sem intervenção. Fixar por digest transferiria para este repositório a
  responsabilidade de acompanhar releases do PostgreSQL, que ninguém vai exercer. O custo supera o
  ganho num ambiente local.
- **Pacotes PGDG** — não são commits arbitrários; são versões curadas pelo repositório da
  distribuição para aquela major. O grau de surpresa é ordens de grandeza menor.
- **`pgvector` da fonte** — é o único que pode trazer código de um commit que ninguém revisou, sequer
  numa release.

A saída é declarar a assimetria em vez de escondê-la. O segundo requisito da spec existe exatamente
para isso: a documentação **não pode** apresentar a imagem como integralmente reprodutível quando
não é. Declarar o que flutua é mais útil ao diagnóstico do que sugerir determinismo inexistente.

**D3. Descobrir a versão instalada hoje antes de escolher a tag.**

A tag deve ser escolhida a partir do que já está no ar, não do que é mais recente. Se a fixação
também atualizar a extensão silenciosamente, a change deixa de ser "tornar determinístico" e vira
"tornar determinístico **e** atualizar" — duas coisas, uma delas não anunciada.

Se a versão instalada não corresponder a nenhuma tag (porque veio de um commit entre releases), a
decisão de qual release adotar SHALL ser registrada, não presumida. Como o `pgvector` não tem
consumidor no monorepo, o risco prático de qualquer escolha é nulo — mas o hábito de anunciar o que
se está mudando não depende do risco.

## Risks / Trade-offs

**Fixar significa não receber correção automaticamente.** É a contrapartida inerente de qualquer
pinning: a versão para de avançar sozinha, inclusive quando avançar seria bom. *Mitigação:* o
`pgvector` não tem consumidor no monorepo, então não há superfície de exploração; e o procedimento de
atualização passa a estar documentado, o que torna a atualização deliberada barata.

**A imagem cacheada mascara o efeito.** Quem já construiu continua com a versão que baixou, e a
mudança só se manifesta no próximo build sem cache. Alguém pode concluir que a change "não fez nada".
*Mitigação:* a task de verificação exige build sem cache e comparação explícita da versão antes e
depois.

**Aceito conscientemente:** a imagem continua não sendo bit-a-bit reprodutível, porque base e pacotes
flutuam. A change entrega reprodutibilidade da **versão da extensão**, não da imagem inteira — e a
spec obriga a documentação a dizer isso com essas palavras.

## Migration Plan

1. Determinar a versão do `pgvector` instalada hoje no ambiente local no ar.
2. Escolher a tag correspondente (ou registrar a decisão, se não houver correspondência exata).
3. Alterar o `git clone` e o `LABEL`; reconstruir sem cache; confirmar que a versão bate.
4. Documentar, na seção de extensões criada pela change irmã, o procedimento de atualização e a
   declaração do que permanece flutuante.

Reversão: reverter o commit. Como o `pgvector` não tem consumidor, nenhuma aplicação é afetada em
nenhuma direção.

**Ordem entre changes:** aplicar depois de `documentar-postgres-local-extensoes`, que cria a
capability e a seção de README onde a documentação desta change se encaixa.

## Open Questions

- **A versão instalada hoje corresponde a alguma tag de release?** Só é respondível com o banco no ar
  (`SELECT extversion FROM pg_extension WHERE extname = 'pgvector'`), e a resposta decide se a
  fixação preserva ou atualiza. É a primeira task por isso.
- **O `LABEL` deve listar a versão das três extensões ou só da fixada?** Listar as três é mais útil a
  quem lê, mas as duas do `apt` exigiriam consultá-las em tempo de build para não mentir. Fica a
  decisão de fazer o barato (só a fixada) ou o completo.
