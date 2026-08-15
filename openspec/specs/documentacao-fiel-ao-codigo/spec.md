# documentacao-fiel-ao-codigo Specification

## Purpose

Garantir que `CLAUDE.md`, `AGENTS.md`, `README.md` e as specs descrevam o comportamento que o
código, o build e a infraestrutura efetivamente têm — cobrindo fidelidade das afirmações,
paridade dos espelhos `CLAUDE.md`/`AGENTS.md`, ausência de contradição entre capacidades e
uniformidade de localização de classes espelhadas.

## Requirements
### Requirement: Documentação descreve o comportamento real

Os arquivos `CLAUDE.md`, `AGENTS.md`, `README.md` e as specs SHALL descrever o comportamento que o
código e a infraestrutura efetivamente têm. Documentação que afirme comportamento inexistente é
tratada como defeito, não como imprecisão — ela suprime a desconfiança de quem a lê justamente para
descobrir armadilhas.

#### Scenario: Afirmação sobre infraestrutura corresponde à configuração

- **WHEN** um `CLAUDE.md` afirma um comportamento do ambiente (criação de tópico, timeout, valor de
  configuração)
- **THEN** a afirmação SHALL corresponder ao que os arquivos de infraestrutura declaram

#### Scenario: Versões documentadas correspondem ao build

- **WHEN** um `CLAUDE.md` lista versões de dependência
- **THEN** SHALL corresponder às versões declaradas no `pom.xml` do app

#### Scenario: Componentes documentados correspondem ao código

- **WHEN** um `CLAUDE.md` enumera regras, rules ou classes de um fluxo
- **THEN** a enumeração SHALL incluir todas as que executam nesse fluxo

#### Scenario: Contagem de artefatos espelhados é exata

- **WHEN** a documentação da raiz descreve quantas cópias de um schema espelhado existem e em quais
  apps
- **THEN** a descrição SHALL corresponder aos arquivos existentes no repositório

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

### Requirement: Espelhos CLAUDE.md e AGENTS.md permanecem idênticos

Em cada aplicação, `CLAUDE.md` e `AGENTS.md` SHALL ter conteúdo idêntico. Alteração em um SHALL ser
replicada no outro na mesma mudança.

#### Scenario: Pares idênticos em todas as aplicações

- **WHEN** os pares `CLAUDE.md`/`AGENTS.md` das quatro aplicações são comparados
- **THEN** cada par SHALL ser idêntico

#### Scenario: Alteração replicada

- **WHEN** um dos arquivos do par é alterado
- **THEN** o outro SHALL receber a mesma alteração antes da conclusão da mudança

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

#### Scenario: Código cumpre todas as specs aplicáveis

- **WHEN** a configuração efetiva de uma aplicação é comparada às capacidades que a especificam
- **THEN** SHALL ser possível cumprir todas simultaneamente

### Requirement: Localização de classe segue a spec em todas as aplicações

Todas as aplicações SHALL cumprir a camada ou pacote que uma spec determine para uma classe
espelhada entre elas. Divergência de localização entre apps para a mesma classe SHALL ser tratada
como defeito.

#### Scenario: TipoEventoAutorizacao em domain/enums nas quatro aplicações

- **WHEN** o pacote de `TipoEventoAutorizacao` é inspecionado nas quatro aplicações
- **THEN** em todas SHALL residir em `domain/enums/`
- **AND** nenhuma SHALL mantê-lo em `application/eventos/`

