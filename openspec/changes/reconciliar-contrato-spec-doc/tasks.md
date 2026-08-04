## 1. Drifts objetivos de documentação (independentes, sem impacto em contrato)

- [ ] 1.1 Corrigir o `CLAUDE.md` da raiz: `AutorizacaoEventoPayload` existe em 2 apps (`arj-contratocommand` e `autorizacaostatus-producer`), não 3; `EventoAutorizacao.avsc` existe em 2 apps (`autorizacaostatus-producer` e `eventos-consumer`)
- [ ] 1.2 Atualizar a tabela de versões do `CLAUDE.md`/`AGENTS.md` do `autorizacaostatus-producer` para bater com o `pom.xml` (Avro 1.11.4, kafka-clients 3.9.2 — confirmar os valores atuais antes de escrever)
- [ ] 1.3 Corrigir o comentário de proveniência em `KafkaProducerClientConfig` que cita visibility timeout de 30s; o valor real é 60s conforme `infra/envs/local-messaging/variables.tf`
- [ ] 1.4 Adicionar `ProdutoSuportadoCancelamento` à seção de regras de cancelamento do `CLAUDE.md`/`AGENTS.md` do `arj-contratocommand`, indicando que roda antes de `TipoProdutoCancelamento`
- [ ] 1.5 Verificar se cada par `CLAUDE.md`/`AGENTS.md` das quatro aplicações está idêntico; alinhar os que divergirem
- [ ] 1.6 Varrer os quatro `CLAUDE.md` por outras afirmações sobre infraestrutura, versões ou componentes e conferir cada uma contra a realidade

## 2. Reconciliação de specs

- [ ] 2.1 Confirmar o valor real de `maximum-pool-size` no `application.yaml` das duas aplicações antes de fixar o texto da spec
- [ ] 2.2 Aplicar o delta de `db-connection-pool-config` (padrão 10, com a precedência de `virtual-threads-config` explícita)
- [ ] 2.3 Verificar se há outras contradições entre specs sobre a mesma propriedade ou comportamento
- [ ] 2.4 Decidir o item 10 (contrato de deduplicação): implementar no consumidor ou reescrever a spec — recomendação é reescrever (D5)
- [ ] 2.5 Aplicar o delta de `publicacao-eventos-kafka` conforme a decisão de 2.4

## 3. Alinhamento de código à spec

- [ ] 3.1 Mover `TipoEventoAutorizacao` de `application/eventos/` para `domain/enums/` no `arj-contratocommand`, alinhando às outras três aplicações e à spec `maquina-estados-autorizacao`
- [ ] 3.2 Atualizar imports e testes afetados pelo move
- [ ] 3.3 Confirmar que o enum permanece sem import de `org.springframework.*`, `jakarta.*` ou `lombok.*`, conforme a spec exige
- [ ] 3.4 Atualizar o mapa de pacotes no `CLAUDE.md`/`AGENTS.md` do `arj-contratocommand`

## 4. Decisões de contrato (registrar antes de codificar)

- [ ] 4.1 Decidir se o versionamento de API será adotado — **bloqueia as tasks 6 e 7**; sem versionamento, as mudanças de contrato não devem ser aplicadas (D1)
- [ ] 4.2 Decidir a convenção de status HTTP: adotar 400 para `@Valid` e 422 para negócio (D3), ou manter 422 para tudo e alinhar a documentação ao código
- [ ] 4.3 Confirmar os nomes canônicos de campo propostos em D2 (`valor`, `dataHoraInclusao`, `dataHoraUltimaAtualizacao`, `status` como nome do enum)
- [ ] 4.4 Definir o prazo de convivência entre versões da API
- [ ] 4.5 Registrar as decisões de 4.1 a 4.4 no `design.md` antes de qualquer alteração de código

## 5. OpenAPI

- [ ] 5.1 Adicionar `springdoc-openapi` aos dois serviços REST
- [ ] 5.2 Anotar controllers e DTOs de forma que o contrato gerado reflita rotas, parâmetros, corpos e status de erro
- [ ] 5.3 Confirmar que o contrato gerado corresponde ao comportamento atual, antes de qualquer mudança de contrato — o OpenAPI da situação vigente é a linha de base
- [ ] 5.4 Avaliar publicação do artefato gerado e verificação em CI contra o contrato aprovado (D4)

## 6. Versionamento (condicionado à decisão 4.1)

- [ ] 6.1 Implementar a estratégia de versionamento escolhida nos dois serviços
- [ ] 6.2 Garantir que os endpoints atuais permaneçam acessíveis na versão vigente, sem alteração de comportamento
- [ ] 6.3 Testes confirmando que clientes da versão anterior não são afetados

## 7. Mudanças de contrato (condicionadas às tasks 4.1 e 6)

- [ ] 7.1 Padronizar `status` como nome do enum no `AutorizacaoCompletaResponseDto` do `arj-contratocommand`
- [ ] 7.2 Alinhar os nomes de campo entre os DTOs dos dois serviços conforme 4.3
- [ ] 7.3 Aplicar a convenção de status HTTP decidida em 4.2 nos handlers dos dois serviços
- [ ] 7.4 Se 4.2 adotar 400 para `@Valid`, revisar os pontos introduzidos por `integridade-fluxo-escrita` e `blindar-superficie-leitura`, que seguiram a convenção 422 vigente — alinhá-los à nova convenção
- [ ] 7.5 Atualizar `README.md` e `CLAUDE.md`/`AGENTS.md` dos dois serviços com os status e nomes efetivos
- [ ] 7.6 Testes de contrato para os DTOs alterados nos dois serviços

## 8. Validação e comunicação

- [ ] 8.1 Rodar a suíte completa dos quatro apps
- [ ] 8.2 Revisar os cenários dos 4 specs desta mudança e confirmar cobertura
- [ ] 8.3 Comparar os DTOs dos dois serviços lado a lado e confirmar que nenhum dado equivalente usa nome ou formato distinto
- [ ] 8.4 Comunicar a quem integra: nova versão da API, mudanças de campo e prazo de descontinuação da anterior
- [ ] 8.5 Avaliar verificação automatizada de igualdade entre os pares `CLAUDE.md`/`AGENTS.md` (D6) — fecha a categoria de drift documental em vez de apenas corrigir as ocorrências atuais
