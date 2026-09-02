## ADDED Requirements

### Requirement: Esteira da app Python verifica tipos e lint além de testes

A esteira de `expurgo-particao` SHALL executar verificação estática de tipos em modo estrito e
verificação de lint, além da execução de testes, e SHALL falhar quando qualquer uma delas apontar
problema. A configuração das ferramentas SHALL viver em arquivo versionado do próprio módulo, de
modo que a verificação executada no runner seja a mesma que o desenvolvedor executa localmente.

A app SHALL ser instalável a partir dessa configuração, sem depender de manipulação de caminho de
importação por variável de ambiente na esteira.

#### Scenario: Anotação de tipo ausente reprova a esteira

- **WHEN** um pull request introduz função sem anotação de tipo em `apps/expurgo-particao/src`
- **THEN** a verificação estrita de tipos SHALL apontar o problema
- **AND** a esteira SHALL falhar

#### Scenario: Verificação local e de esteira são a mesma

- **WHEN** o desenvolvedor executa as ferramentas de verificação localmente no módulo
- **THEN** elas SHALL usar a mesma configuração versionada que a esteira usa
- **AND** o resultado SHALL ser o mesmo para o mesmo código

#### Scenario: Importação não depende de variável de ambiente

- **WHEN** a esteira prepara o ambiente da app Python
- **THEN** os testes SHALL importar o pacote da app sem que a esteira precise definir caminho de
  importação por variável de ambiente

### Requirement: Árvore de decisão do expurgo é coberta sem depender de Postgres

A esteira de `expurgo-particao` SHALL exercitar a decisão que leva ao esvaziamento — e a cada uma
das recusas — sem exigir banco de dados disponível no runner. O teste que exige Postgres real
permanece excluído da esteira, e por isso NÃO SHALL ser a única cobertura da lógica que decide
destruir dado.

Os caminhos cobertos SHALL incluir, no mínimo: partição vazia, dado do ciclo anterior com
esvaziamento aplicado, dado recente recusado, modo de consulta sobre dado esvaziável, desarme
operacional sobre dado esvaziável, e esgotamento do limite de espera por lock.

#### Scenario: Decisão de esvaziamento é verificada sem banco

- **WHEN** a esteira de `expurgo-particao` é executada num runner sem Postgres disponível
- **THEN** os testes que exercitam a decisão de esvaziar ou recusar SHALL ser executados
- **AND** a esteira SHALL falhar caso qualquer um desses caminhos regrida

#### Scenario: Ponto de entrada da Lambda é coberto

- **WHEN** a esteira é executada
- **THEN** os testes SHALL cobrir a montagem da string de conexão a partir das variáveis de
  ambiente, incluindo credencial com caractere reservado de URI
- **AND** SHALL cobrir a interpretação do evento recebido, incluindo data de referência inválida e
  ausência de variável de ambiente obrigatória
