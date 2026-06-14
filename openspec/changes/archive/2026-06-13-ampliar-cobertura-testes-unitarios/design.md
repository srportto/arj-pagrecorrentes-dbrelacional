## Context

`contratocommand` (48 classes) e `contratoquery` (29 classes) rodam Spring Boot 4.0.4 / Java 25, arquitetura hexagonal, Lombok + MapStruct. Hoje só há testes para 2 services por app (Mockito puro) e os `@SpringBootTest` de contexto estão `@Disabled` (exigem PostgreSQL). Não há JaCoCo nos `pom.xml`. O objetivo é elevar a cobertura de testes **unitários** das classes com lógica para perto de 100% (alvo pragmático ~90%+), com medição e gate no build, sem tocar em código de produção.

## Goals / Non-Goals

**Goals:**
- Medir cobertura (JaCoCo) e falhar o build abaixo de um mínimo, nas duas apps.
- Cobrir com testes unitários (JUnit 5 + Mockito) as classes que contêm lógica: orquestradores, validators, regras, use cases, mappers, services, utilitários, enums, converters, DTOs com `from()` e `ApiExceptionHandler`.
- Excluir do cálculo o que não é unit-testável ou é gerado (main, interfaces de repositório, código Lombok).

**Non-Goals:**
- Não escrever testes de integração nem habilitar os `@SpringBootTest` de contexto (exigem banco; fora de "unitário").
- Não alterar código de produção (salvo, se estritamente necessário, refator mínimo para testabilidade — a ser sinalizado).
- Não buscar 100% literal incluindo getters/`equals` do Lombok ou `main()`.
- Não cobrir caminhos que dependem de banco real (queries JPA são exercidas via mocks do repositório).

## Decisions

### 1. Exclusão de código Lombok via `lombok.config` (`@Generated`)

**Decisão:** adicionar, na raiz de cada app, um `lombok.config` com `lombok.addLombokGeneratedAnnotation = true`. O Lombok passa a anotar o código gerado com `@lombok.Generated`, e o JaCoCo (≥ 0.8) **ignora automaticamente** membros/classes assim anotados.

**Rationale:** evita enumerar manualmente dezenas de entidades/DTOs Lombok nas exclusões. Getters/setters/`equals`/`hashCode`/builders param de contar contra a cobertura; sobra apenas a lógica escrita à mão (ex.: `from()`, `inicializaCriacao`).

### 2. Exclusões explícitas no JaCoCo

**Decisão:** excluir por padrão de classe: `**/*Application.class` (entry points Spring com `main`) e `**/*Repository.class` (interfaces JPA — sem corpo executável a testar unitariamente). Demais exclusões ficam a cargo do mecanismo Lombok (decisão 1).

### 3. Gate de cobertura abaixo do alvo

**Decisão:** configurar `jacoco:check` com regra de BUNDLE, contador `LINE`, mínimo **0.80**. O alvo prático dos testes é **≥ 0.90**, mas o gate fica em 0.80 de propósito, dando margem para que pequenas mudanças não quebrem o build por flutuação de cobertura. O gate pode ser elevado depois.

**Alternativa considerada:** gate em 0.90 (igual ao alvo) — descartado por fragilizar o build a cada PR. Gate só de branch — descartado por gerar ruído; usar `LINE` como métrica principal.

### 4. JUnit 5 + Mockito, unitário e determinístico

**Decisão:** seguir o padrão existente (`@ExtendWith(MockitoExtension.class)`, asserts JUnit 5). Padrões por tipo de classe:
- **Orquestradores** (`List<Service>` injetada): construir com mocks de strategy; verificar seleção do primeiro suportado e `BusinessException` quando nenhum suporta.
- **Validators** (`List<Rule>`): exercitar com regras reais/mocadas; caminho válido e violação.
- **Regras** (`*Rule`): testar `aceita` + `validar` para casos limite (ex.: data no passado, valor acima do limite, metadado inválido).
- **Use cases** (`@Transactional`): mockar repositório/mapper/validator; verificar orquestração e `save`.
- **Mappers MapStruct**: instanciar o impl gerado (`new PixAutoMapperImpl()`) e validar mapeamento + efeitos do `@AfterMapping`.
- **DTOs**: exercitar `from()`/mapeamento de `status` e `metadado`.
- **Utilitários/enums**: cobrir conversões, ramos e exceções.

### 5. Sem habilitar testes de contexto

**Decisão:** manter `ContratocommandApplicationTests` e `ContratoqueryApplicationTests` com `@Disabled`. Eles sobem o contexto Spring e exigem PostgreSQL — não são unitários e degradariam o build offline.

## Risks / Trade-offs

| Risco | Mitigação |
|---|---|
| **JaCoCo pode não instrumentar bytecode Java 25** (class file v69) se a versão do plugin for antiga | Usar a versão mais recente do `jacoco-maven-plugin`; validar logo no primeiro `mvn verify`. Se ainda não houver suporte a Java 25, ver Open Questions |
| Gate alto fragiliza o build | Gate em 0.80 (abaixo do alvo 0.90); elevar gradualmente |
| MapStruct impl gerado pode invocar lógica de domínio com efeitos (UUID/partição aleatórios) | Testar propriedades estruturais e invariantes (não valores aleatórios) |
| Buscar cobertura pode tentar testar trivialidades | Excluir Lombok (decisão 1) e focar em ramos com lógica |
| Refator mínimo para testabilidade pode ser necessário em alguma classe | Sinalizar no apply antes de mexer em produção |

## Migration Plan

1. Adicionar `jacoco-maven-plugin` (prepare-agent, report, check) e `lombok.config` aos dois apps.
2. Rodar `mvn verify` para obter a baseline de cobertura e **confirmar que o JaCoCo instrumenta Java 25**.
3. Escrever testes por camada, app a app, subindo a cobertura até ≥ 0.90 nas classes não-excluídas.
4. Ajustar o gate (`check`) para 0.80 e confirmar build verde em ambos.

**Rollback:** remover plugin/config e testes (`git revert`); sem impacto em produção.

## Open Questions

- **Suporte do JaCoCo ao Java 25:** se a versão mais recente do `jacoco-maven-plugin` ainda não instrumentar class files v69, o gate fica inviável no curto prazo. Fallback possível: (a) compilar testes/medição com `--release 24` não é viável (o projeto é 25), ou (b) adiar o gate e manter apenas os testes unitários (cobertura medida quando o suporte chegar). Decidir no passo 2 do plano, com base no resultado real do `mvn verify`.
