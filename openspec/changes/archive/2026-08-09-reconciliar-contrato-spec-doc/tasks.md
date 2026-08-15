## 1. Drifts objetivos de documentação (independentes, sem impacto em contrato)

  - [x] 1.1 Corrigir o `CLAUDE.md` da raiz: `AutorizacaoEventoPayload` existe em 2 apps (`contratocommand` e `autorizacaostatus-producer`), não 3; `EventoAutorizacao.avsc` existe em 2 apps (`autorizacaostatus-producer` e `eventos-consumer`)
  - [x] 1.2 Atualizar a tabela de versões do `CLAUDE.md`/`AGENTS.md` do `autorizacaostatus-producer` para bater com o `pom.xml` (Avro 1.11.4, kafka-clients 3.9.2 — confirmar os valores atuais antes de escrever)
  - [x] 1.3 Corrigir o comentário de proveniência em `KafkaProducerClientConfig` que cita visibility timeout de 30s; o valor real é 60s conforme `infra/envs/local-messaging/variables.tf`
  - [x] 1.4 Adicionar `ProdutoSuportadoCancelamento` à seção de regras de cancelamento do `CLAUDE.md`/`AGENTS.md` do `contratocommand`, indicando que roda antes de `TipoProdutoCancelamento`
  - [x] 1.5 Verificar se cada par `CLAUDE.md`/`AGENTS.md` das quatro aplicações está idêntico; alinhar os que divergirem
  - [x] 1.6 Varrer os quatro `CLAUDE.md` por outras afirmações sobre infraestrutura, versões ou componentes e conferir cada uma contra a realidade
  - [x] 1.7 Atualizar `apps/contratocommand/README.md`: adicionar a rota `PATCH /{id}/decisao` (introduzida por `temporizacao-jornada-01-pix-auto`, ausente do README) e remover a menção ao endpoint inexistente `GET /api/autorizacoes/listar` — achado em auditoria de 2026-08-09
  - [x] 1.8 Corrigir os nomes de status nos `README.md` do `contratocommand` e do `contratoquery`: campo `statusAutorizacao` → `status`; valores `ATIVO`/`CANCELADO` → `ATIVA`/`CANCELADA` (achado em auditoria de 2026-08-09)

## 2. Reconciliação de specs

- [x] 2.1 Confirmar o valor real de `maximum-pool-size` no `application.yaml` das duas aplicações antes de fixar o texto da spec
- [x] 2.2 Aplicar o delta de `db-connection-pool-config` (padrão 10, com a precedência de `virtual-threads-config` explícita)
- [x] 2.3 Verificar se há outras contradições entre specs sobre a mesma propriedade ou comportamento
- [x] 2.4 Decidir o item 10 (contrato de deduplicação): implementar no consumidor ou reescrever a spec — recomendação é reescrever (D5)
- [x] 2.5 Aplicar o delta de `publicacao-eventos-kafka` conforme a decisão de 2.4

## 3. Alinhamento de código à spec

  - [x] 3.1 Mover `TipoEventoAutorizacao` de `application/eventos/` para `domain/enums/` no `contratocommand`, alinhando às outras três aplicações e à spec `maquina-estados-autorizacao`
  - [x] 3.2 Atualizar imports e testes afetados pelo move
  - [x] 3.3 Confirmar que o enum permanece sem import de `org.springframework.*`, `jakarta.*` ou `lombok.*`, conforme a spec exige
  - [x] 3.4 Atualizar o mapa de pacotes no `CLAUDE.md`/`AGENTS.md` do `contratocommand`

## 4. Decisões de contrato (registrar antes de codificar)

- [x] 4.1 Decidir se o versionamento de API será adotado — **Resolvido em 2026-08-09 como Opção C (sem versionar agora). Ver D1 no `design.md`. Itens 2 e 3 viram dívida documentada. Section 6 e 7 saem desta change.**
- [x] 4.2 Decidir a convenção de status HTTP: adotar 400 para `@Valid` e 422 para negócio (D3), ou manter 422 para tudo e alinhar a documentação ao código
- [x] 4.3 Registrar no `CLAUDE.md` da raiz e na spec `listar-autorizacoes` a dívida aceita: command e query têm representações distintas por design (D2 revisada)
- [x] 4.4 Definir o prazo de convivência entre versões da API — **N/A: D1=C, não há v2 planejada**
- [x] 4.5 Registrar as decisões de 4.1 a 4.4 no `design.md` antes de qualquer alteração de código

## 5. OpenAPI (gera insumo barato para versionamento futuro)

- [x] 5.1 Adicionar `springdoc-openapi` aos dois serviços REST
- [x] 5.2 Anotar controllers e DTOs de forma que o contrato gerado reflita rotas, parâmetros, corpos e status de erro
- [x] 5.3 Confirmar que o contrato gerado corresponde ao comportamento atual, antes de qualquer mudança de contrato — o OpenAPI da situação vigente é a linha de base
- [x] 5.4 Avaliar publicação do artefato gerado e verificação em CI contra o contrato aprovado (D4)

> Sections 6 e 7 foram **removidas** após a decisão D1=C (Opção C do explore de 2026-08-09). Versionamento e renomeação de contrato serão tratados em change dedicada se/quando algum dos gatilhos da D1 ocorrer.

## 8. Validação e comunicação

- [x] 8.1 Rodar a suíte completa dos quatro apps — `contratocommand` 160 (0 falhas, 2 skips) e `contratoquery` 59 (0 falhas, 1 skip), BUILD SUCCESS em ambos; apps não-REST não rodaram (sem mudança de código que os afete). Saída consolidada em `RESULTADO-VALIDACAO.md` §8.1
- [x] 8.2 Revisar os cenários dos 4 specs desta mudança e confirmar cobertura — `db-connection-pool-config` e `publicacao-eventos-kafka` são deltas documentais sem teste novo; `maquina-estados-autorizacao` coberta por `TipoEventoAutorizacaoTest` (3 testes verdes, já na suíte de 8.1); `listar-autorizacoes` é nota de dívida aceita sem teste novo. Tabela em `RESULTADO-VALIDACAO.md` §8.2
- [x] 8.3 Comparar os DTOs dos dois serviços lado a lado e confirmar que nenhum dado equivalente usa nome ou formato distinto — divergência confirmada por grep: `command.status` é `Integer` (linha 28 do `AutorizacaoCompletaResponseDto`); `query.status` é `String` (linha 32 dos dois DTOs). Nomes divergentes (`valorAutorizacao`/`dataHoraInclusao`/`dataHoraUltimaAtualizacao` no command; `valor`/`dataCriacao`/`dataAtualizacao` no query). Tipos idênticos. Dívida aceita registrada no `AGENTS.md` da raiz (espelho do `CLAUDE.md`) desde 4.3, reproduzida em `RESULTADO-VALIDACAO.md` §8.3
- [x] 8.4 Comunicar a quem integra: nova versão da API, mudanças de campo e prazo de descontinuação da anterior — **N/A com D1=C** (sem versionamento nesta change, não há breaking change de contrato para comunicar). Dívida command vs query documentada em `AGENTS.md` da raiz e na spec `listar-autorizacoes`. Task será reativada na change dedicada se algum gatilho da D1 ocorrer. Justificativa completa em `RESULTADO-VALIDACAO.md` §8.4
- [x] 8.5 Avaliar verificação automatizada de igualdade entre os pares `CLAUDE.md`/`AGENTS.md` (D6) — recomendação registrada como follow-up explícito: teste/script que rode `diff -q CLAUDE.md AGENTS.md` em cada par (raiz + 4 apps) e falhe o build se divergir. **Fora do escopo desta change** (sem CI ainda, conforme `rotacionar-segredo-versionado` 5.1). Justificativa completa em `RESULTADO-VALIDACAO.md` §8.5
