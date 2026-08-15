## ADDED Requirements

### Requirement: Afirmação sobre o build corresponde ao pom.xml

A documentação SHALL descrever o **build** de uma aplicação — flags de compilação, preview
features, módulos adicionados (`--add-modules`), plugins Maven, ferramentas de cobertura — em
correspondência com o que o `pom.xml` da app efetivamente declara.

Esta é a lacuna que permitiu o caso concreto: o `README.md` de `contratocommand` mantinha uma
seção instruindo a compilar com `--enable-preview` e `--add-modules=jdk.incubator.vector`, quando
nenhum `pom.xml` do monorepo declara preview features — e o comando ensinado
(`javac ... -jar target/app.jar`) nem era sintaxe válida. A capacidade já cobria infraestrutura,
versões e componentes, mas não o build.

#### Scenario: Preview features documentadas correspondem ao build

- **WHEN** a documentação de uma app afirma que o projeto usa preview features do Java
- **THEN** o `pom.xml` da app SHALL declarar `--enable-preview` na configuração do
  `maven-compiler-plugin`
- **AND** se não declarar, a afirmação SHALL ser removida

#### Scenario: Comando de build documentado é executável

- **WHEN** a documentação apresenta um comando de compilação ou execução
- **THEN** o comando SHALL ser sintaticamente válido e executável como escrito

#### Scenario: Plugin documentado existe no build

- **WHEN** a documentação instrui a rodar uma tarefa de plugin Maven (cobertura, verificação,
  geração)
- **THEN** o plugin correspondente SHALL estar declarado no `pom.xml` da app

## MODIFIED Requirements

### Requirement: Specs não se contradizem entre si

Duas capacidades NÃO SHALL especificar valores conflitantes para a mesma propriedade de
configuração ou para o mesmo comportamento. Havendo relação entre capacidades sobre a mesma
propriedade, a spec SHALL declarar explicitamente qual prevalece e por quê.

Uma spec NÃO SHALL contradizer decisão registrada e implementada que lhe seja posterior. Quando
uma decisão datada (registrada no `CLAUDE.md` da raiz ou no `design.md` de uma change) alterar
uma regra já escrita numa spec, a spec SHALL ser atualizada na mesma mudança que implementa a
decisão — e, se isso não ocorreu, a correção SHALL registrar a data e o racional da decisão que
prevaleceu, não apenas o valor novo.

#### Scenario: Valor de configuração especificado uma única vez

- **WHEN** duas capacidades tratam da mesma propriedade de configuração
- **THEN** SHALL declarar o mesmo valor
- **AND** a relação entre elas SHALL estar explícita

#### Scenario: Spec alinhada à decisão datada que prevaleceu

- **WHEN** uma spec afirma um comportamento diferente do que o código implementa por força de uma
  decisão posterior e registrada
- **THEN** a spec SHALL ser corrigida para o comportamento vigente
- **AND** SHALL citar a decisão (data e racional) que a alterou, para que a correção não seja
  revertida por quem leia apenas o valor

#### Scenario: Correção de spec é verificada contra o código antes de aplicada

- **WHEN** uma contradição entre spec e documentação é identificada
- **THEN** o comportamento real SHALL ser verificado por teste antes de decidir qual dos dois
  lados está errado
