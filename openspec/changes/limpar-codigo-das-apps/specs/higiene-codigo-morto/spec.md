## ADDED Requirements

### Requirement: Nenhum arquivo Java contém import sem uso

Nenhum arquivo `.java` das cinco aplicações de `apps/` — em `src/main` **ou** `src/test` — SHALL
declarar um `import` cujo tipo não seja referenciado no arquivo.

O escopo é deliberadamente maior que o de `higiene-codigo-morto` original (que cobre apenas
`arj-contratocommand` e `arj-contratoquery`): import sem uso é defeito de mesma natureza nas cinco
apps, e o custo de verificar é o mesmo.

#### Scenario: Import sem uso não existe em nenhuma app

- **WHEN** os arquivos `.java` de `apps/arj-contratocommand`, `apps/arj-contratoquery`,
  `apps/autorizacaostatus-producer`, `apps/eventos-consumer` e `apps/temporiza-autorizacao` são
  inspecionados
- **THEN** todo `import` declarado tem ao menos uma referência ao tipo importado no corpo do
  arquivo

#### Scenario: Código de teste tem o mesmo critério

- **WHEN** um arquivo de `src/test` declara um import que deixou de ser usado após uma refatoração
  do teste
- **THEN** o import é removido na mesma mudança que o tornou inútil

### Requirement: Parâmetros de método sem uso são removidos ou justificados

Um parâmetro de método em `src/main` que não é referenciado no corpo do método SHALL ser removido,
**exceto** quando exigido pela assinatura de um contrato externo.

São exceções legítimas, que MAY permanecer sem uso:
- parâmetro de método `@ExceptionHandler` do Spring MVC;
- parâmetro de callback de listener (SQS, Kafka) exigido pela assinatura do container;
- parâmetro de método que implementa interface ou sobrescreve método de superclasse;
- parâmetro exigido por assinatura de biblioteca (`main(String[] args)`).

Quando o parâmetro permanece por uma dessas razões e isso não é óbvio pela leitura, a razão SHALL
estar registrada — em javadoc ou comentário de uma linha, conforme `higiene-comentarios-codigo`.

#### Scenario: Parâmetro morto de método próprio é removido

- **WHEN** um método definido pela própria aplicação declara um parâmetro nunca referenciado no
  corpo, e nenhuma assinatura externa o exige
- **THEN** o parâmetro é removido, junto de todas as chamadas que o passavam

#### Scenario: Parâmetro exigido por framework permanece

- **WHEN** um método `@ExceptionHandler` declara `HttpServletRequest req` e não o usa no corpo
- **THEN** o parâmetro permanece, porque removê-lo quebra o binding do Spring MVC
- **AND** a remoção não é proposta como "código morto"

#### Scenario: Varredura usa o compilador, não busca textual

- **WHEN** a ausência de parâmetro morto é verificada
- **THEN** a verificação SHALL usar os avisos do compilador (`-Xlint`), não busca por padrão de
  texto, porque a determinação exige análise de fluxo

### Requirement: Marcação TODO exige custo concreto identificado

Um comentário `// TODO` que sinaliza oportunidade de refatoração SHALL satisfazer ao menos um
destes gatilhos:

1. uma medição registrada (latência, contagem, consumo de recurso);
2. um bloqueio externo nomeado (limitação de ferramenta, versão de biblioteca pendente);
3. uma change OpenSpec aberta que o endereça, citada pelo nome.

`TODO` que não satisfaz nenhum gatilho MUST NOT existir no código. A oportunidade percebida sem
custo mensurado é registro de backlog, não comentário — e comentário que não explica um porquê não
óbvio já é vedado por `higiene-comentarios-codigo`.

Cada `TODO` SHALL caber em uma linha e nomear a causa, não o sintoma.

#### Scenario: TODO com medição é aceito

- **WHEN** um trecho tem custo medido e registrado (ex.: 148 ms de planejamento por chamada na
  listagem do `arj-contratoquery`)
- **THEN** um `// TODO` de uma linha nomeando a medição e a change que a endereça é aceito

#### Scenario: TODO genérico é removido

- **WHEN** existe um `// TODO: otimizar`, `// TODO: refatorar` ou equivalente, sem medição, sem
  bloqueio nomeado e sem change citada
- **THEN** esse comentário não existe no código

#### Scenario: TODO nomeia causa, não sintoma

- **WHEN** um `// TODO` é escrito
- **THEN** ele descreve o que causa o problema e o que o desbloqueia
- **AND** não se limita a nomear o sintoma percebido
