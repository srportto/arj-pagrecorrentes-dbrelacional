# local-postgres-environment

## Purpose

Descreve o ambiente PostgreSQL local como receita reaproveitável de construção de um banco com
extensões auxiliares — imagem própria em vez de imagem oficial pronta, os dois caminhos de
instalação de extensão (pacote PGDG e compilação da fonte), o critério de quando o preload em
`shared_preload_libraries` é exigido, a verificação de cada etapa, e a natureza deliberada de
extensões carregadas sem consumidor no monorepo.

**Fronteira com `orquestracao-local-unificada`:** aquela capability especifica que o serviço
PostgreSQL tem definição única, que as migrations são aplicadas por qualquer caminho de subida, que
há healthcheck, e que `shared_preload_libraries` é declarada numa única diretiva `-c`. Esta
capability NÃO redeclara nada disso — ela descreve a **construção da imagem e a receita de
extensões**.

## Requirements

### Requirement: PostgreSQL local é construído a partir de imagem própria, para admitir extensões auxiliares

O ambiente local SHALL prover o PostgreSQL a partir de uma **imagem construída no repositório**, e
NÃO a partir de uma imagem oficial pronta consumida diretamente. A capacidade de somar extensões ao
banco é objetivo declarado do ambiente local, e é ela que justifica o custo da construção.

A imagem SHALL exercitar os **dois** caminhos de instalação de extensão que o projeto se propõe a
demonstrar:

1. **pacote pré-compilado** do repositório PGDG, já configurado na imagem oficial base, instalado via
   gerenciador de pacotes;
2. **compilação a partir do código-fonte**, para o caso em que não há pacote disponível para a versão
   do PostgreSQL em uso.

A imagem SHALL remover as dependências de compilação após o build, de modo que elas não permaneçam
na imagem de runtime.

#### Scenario: Imagem construída, não consumida pronta

- **WHEN** a definição do serviço PostgreSQL local é inspecionada
- **THEN** ela SHALL construir a imagem a partir de um `dockerfile` do repositório
- **AND** esse `dockerfile` SHALL partir de uma imagem oficial do PostgreSQL como base

#### Scenario: Os dois caminhos de instalação estão representados

- **WHEN** o `dockerfile` do PostgreSQL local é inspecionado
- **THEN** SHALL haver ao menos uma extensão instalada por pacote do repositório PGDG
- **AND** SHALL haver ao menos uma extensão compilada a partir do código-fonte

#### Scenario: Dependências de compilação não sobrevivem ao build

- **WHEN** a imagem construída é inspecionada
- **THEN** os pacotes usados apenas para compilar extensões NÃO SHALL estar presentes

### Requirement: Receita de adição de extensão documentada de ponta a ponta

O `README.md` de `infra/local/postgres/` SHALL documentar, como procedimento completo e ordenado, o
que é preciso fazer para que uma extensão desejada fique disponível no banco local — cobrindo as três
etapas possíveis:

1. **instalar** a extensão na imagem, por pacote ou por compilação;
2. **declarar** a biblioteca em `shared_preload_libraries`, **quando a extensão exigir**;
3. **criar** a extensão no banco via `CREATE EXTENSION`, em migration.

A documentação SHALL deixar explícito que essas três etapas vivem em **três arquivos diferentes**
(`dockerfile`, o compose do serviço e a migration), e que a receita só funciona quando as três são
consideradas juntas — a leitura de qualquer uma isolada não revela o procedimento.

A documentação SHALL registrar a armadilha já especificada em `orquestracao-local-unificada`: a mesma
GUC passada em duas diretivas `-c` separadas não acumula, o último valor prevalece, e a primeira
declaração é descartada sem erro, sem aviso e sem entrada de log.

#### Scenario: Procedimento completo encontrável em um lugar

- **WHEN** uma pessoa abre `infra/local/postgres/README.md` querendo somar uma extensão nova
- **THEN** SHALL encontrar as três etapas na ordem em que devem ser executadas
- **AND** SHALL encontrar em qual arquivo cada etapa é feita

#### Scenario: Ambos os caminhos de instalação documentados com exemplo real

- **WHEN** a seção de extensões é lida
- **THEN** SHALL apresentar o caminho por pacote PGDG e o caminho por compilação da fonte
- **AND** cada um SHALL apontar a extensão do próprio repositório que o exemplifica

### Requirement: Critério explícito de quando o preload é exigido

A documentação SHALL estabelecer o critério que distingue a extensão que exige declaração em
`shared_preload_libraries` e reinício do servidor daquela para a qual `CREATE EXTENSION` basta.

O critério SHALL ser enunciado por **natureza da extensão**, não por enumeração das extensões
atualmente presentes: extensão que registra background worker ou se engancha em ponto de extensão do
servidor precisa ser carregada na inicialização do processo; extensão que só acrescenta tipos,
funções, operadores ou índices não precisa.

A justificativa é evitar tentativa e erro: sem o critério, a única forma de descobrir se uma extensão
nova exige preload é adicioná-la, falhar, e investigar.

#### Scenario: Critério enunciado por natureza, não por lista

- **WHEN** a seção de extensões é lida
- **THEN** SHALL apresentar a regra que permite decidir, para uma extensão **ainda não usada** no
  repositório, se ela exigirá preload
- **AND** NÃO SHALL depender apenas de listar as extensões hoje presentes

#### Scenario: Extensões atuais classificadas pelo critério

- **WHEN** a seção de extensões é lida
- **THEN** cada extensão presente no ambiente local SHALL estar classificada como "exige preload" ou
  "basta `CREATE EXTENSION`", coerentemente com o critério enunciado

### Requirement: Extensão carregada sem consumidor é deliberada, não dívida técnica

O ambiente PostgreSQL local SHALL poder carregar extensões que nenhuma aplicação do monorepo consome,
e essa condição NÃO SHALL ser tratada como código morto, sobra de refactor ou dívida a ser saldada.

A razão é que o ambiente local tem dois propósitos simultâneos: servir as aplicações **e** demonstrar
a construção de um PostgreSQL com extensões auxiliares. O segundo propósito é satisfeito pela
presença da extensão, independentemente de haver consumidor.

Nenhuma change de higiene de código, auditoria de dependências ou limpeza de configuração SHALL
remover extensão do `dockerfile` ou entrada de `shared_preload_libraries` tendo como justificativa
apenas a ausência de uso. A remoção SHALL exigir decisão explícita de que a demonstração daquela
extensão não é mais desejada.

A documentação SHALL registrar essa intenção de forma encontrável a partir do próprio ambiente local,
para que a pergunta "por que isto está aqui se ninguém usa?" tenha resposta escrita.

#### Scenario: Extensão sem consumidor não constitui violação

- **WHEN** uma auditoria identifica extensão carregada no ambiente local sem nenhuma aplicação que a
  utilize
- **THEN** isso NÃO SHALL ser reportado como defeito, código morto ou dívida
- **AND** a intenção SHALL estar registrada na documentação do ambiente local

#### Scenario: Remoção exige decisão explícita

- **WHEN** uma change propõe remover extensão do ambiente local
- **THEN** a justificativa NÃO SHALL ser a ausência de consumidor
- **AND** SHALL declarar que a demonstração daquela extensão deixou de ser desejada

### Requirement: Verificação de cada etapa da receita

A documentação SHALL prover os comandos que confirmam o resultado de cada etapa, e SHALL distinguir
os dois estados independentes em que uma extensão pode se encontrar: **biblioteca carregada no
processo** e **extensão criada no banco**.

A distinção importa porque os dois estados são independentes — uma extensão pode estar no preload sem
ter sido criada em banco algum, e pode estar criada sem exigir preload. Diagnosticar "a extensão não
funciona" sem separar os dois estados leva a investigar o arquivo errado.

#### Scenario: Confirmação de que a biblioteca foi carregada

- **WHEN** o ambiente local está no ar e a documentação é seguida
- **THEN** SHALL haver comando que exibe as bibliotecas efetivamente carregadas pelo servidor
- **AND** o resultado esperado SHALL estar documentado

#### Scenario: Confirmação de que a extensão foi criada

- **WHEN** o ambiente local está no ar e a documentação é seguida
- **THEN** SHALL haver comando que lista as extensões criadas no banco, com suas versões
- **AND** a documentação SHALL deixar claro que esse resultado é independente do preload

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
