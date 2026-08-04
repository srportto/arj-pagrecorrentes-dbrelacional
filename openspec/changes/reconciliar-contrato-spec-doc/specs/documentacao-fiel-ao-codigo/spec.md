## ADDED Requirements

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

#### Scenario: Valor de configuração especificado uma única vez

- **WHEN** duas capacidades tratam da mesma propriedade de configuração
- **THEN** SHALL declarar o mesmo valor
- **AND** a relação entre elas SHALL estar explícita

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
