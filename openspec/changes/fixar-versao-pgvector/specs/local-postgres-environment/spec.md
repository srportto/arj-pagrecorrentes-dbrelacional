## ADDED Requirements

### Requirement: Construção da imagem do PostgreSQL local é reprodutível

Toda extensão instalada por **compilação a partir do código-fonte** SHALL ser obtida por referência
imutável — tag de release ou commit — e NÃO SHALL ser obtida do branch padrão sem referência, cujo
conteúdo muda com o tempo.

A justificativa é que este ambiente existe também para demonstrar a construção de um PostgreSQL com
extensões auxiliares. Uma receita cujo resultado depende da data em que é executada não demonstra
construção reprodutível, e a divergência é silenciosa: a imagem é cacheada, então versões diferentes
só se manifestam quando alguém constrói do zero muito depois, com sintoma que não se parece com
"a versão mudou".

A versão instalada SHALL ser legível a partir dos metadados da imagem, sem exigir inspeção do banco
em execução.

#### Scenario: Extensão compilada da fonte tem referência imutável

- **WHEN** o `dockerfile` do PostgreSQL local é inspecionado
- **THEN** toda obtenção de código-fonte de extensão SHALL indicar tag ou commit explícito
- **AND** NÃO SHALL depender do conteúdo corrente do branch padrão

#### Scenario: Dois builds em datas diferentes produzem a mesma versão

- **WHEN** a imagem é construída do zero, sem cache, em dois momentos distintos
- **THEN** a versão da extensão compilada da fonte SHALL ser a mesma nos dois

#### Scenario: Versão legível nos metadados da imagem

- **WHEN** os metadados da imagem construída são inspecionados
- **THEN** a versão da extensão compilada da fonte SHALL estar declarada

### Requirement: Fontes que permanecem flutuantes são declaradas, não presumidas

Toda origem de software da imagem que permanece flutuante SHALL estar documentada como escolha
explícita, com a razão pela qual a flutuação é aceitável. Nem toda origem precisa ser fixada — mas
toda origem não fixada precisa ser declarada.

A documentação NÃO SHALL apresentar a imagem como integralmente reprodutível quando parte de suas
origens não é fixada. Declarar o que flutua é mais honesto — e mais útil ao diagnóstico — do que
sugerir determinismo que não existe.

#### Scenario: Origem flutuante tem razão registrada

- **WHEN** a documentação do ambiente PostgreSQL local é lida
- **THEN** cada origem de software não fixada SHALL estar nomeada
- **AND** SHALL ter registrada a razão pela qual sua flutuação é aceitável

#### Scenario: Atualização de extensão fixada é decisão consciente

- **WHEN** a documentação descreve como atualizar uma extensão compilada da fonte
- **THEN** SHALL deixar claro que a atualização exige alterar a referência no `dockerfile` e
  reconstruir a imagem
- **AND** SHALL deixar claro que reconstruir sem alterar a referência NÃO SHALL mudar a versão
