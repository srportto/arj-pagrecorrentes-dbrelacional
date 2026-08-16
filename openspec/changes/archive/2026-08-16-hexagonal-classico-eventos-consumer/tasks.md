## 1. Linha de base antes de mover qualquer arquivo

- [x] 1.1 Rodar `mvn test` em `apps/eventos-consumer` e **registrar a contagem exata** de testes executados/pulados — é o critério de aceite da mudança inteira
- [x] 1.2 Confirmar que `EventosConsumerApplication` está em `br.com.srportto.eventosconsumer` (raiz de todos os pacotes novos), garantindo que o component scan alcance a árvore reorganizada
- [x] 1.3 Confirmar que nenhum `application.yaml`/`application-*.yaml` referencia classe Java por nome totalmente qualificado

## 2. Domínio

- [x] 2.1 Criar `domain/port/in/ProcessarEventoAutorizacaoUseCase.java` como **interface** Java pura, com o método `processar(EventoAutorizacao)`
- [x] 2.2 Confirmar que a interface não importa nada de `org.springframework.*`
- [x] 2.3 Deixar `domain/enums/StatusAutorizacao` e `domain/enums/TipoEventoAutorizacao` onde estão (D3) — nenhuma alteração

## 3. Application

- [x] 3.1 Mover `application/eventos/ProcessarEventoAutorizacaoUseCase.java` para `application/usecase/ProcessarEventoAutorizacaoService.java`, renomeando a classe (D1)
- [x] 3.2 Fazer a classe declarar `implements ProcessarEventoAutorizacaoUseCase` e anotar o método com `@Override`
- [x] 3.3 Manter `@Service` e o logger; nenhuma mudança de corpo de método
- [x] 3.4 Remover o pacote `application/eventos/`, agora vazio

## 4. Infrastructure

- [x] 4.1 Mover `entrypoint/kafka/EventoAutorizacaoKafkaListener.java` para `infrastructure/messaging/`
- [x] 4.2 Trocar o tipo do campo injetado no listener de classe concreta para a porta `ProcessarEventoAutorizacaoUseCase` (D2)
- [x] 4.3 Mover `shared/config/KafkaConsumerConfig.java` e `shared/config/KafkaProperties.java` para `infrastructure/config/` (D4)
- [x] 4.4 Remover os pacotes `entrypoint/` e `shared/`, agora vazios
- [x] 4.5 Rodar a skill `remover-imports-nao-usados` na app e conferir que nenhum `import` ficou órfão

## 5. Testes

- [x] 5.1 Mover `application/eventos/ProcessarEventoAutorizacaoUseCaseTest` para `application/usecase/ProcessarEventoAutorizacaoServiceTest`, ajustando nome da classe e imports
- [x] 5.2 Mover `entrypoint/kafka/EventoAutorizacaoKafkaListenerTest` para `infrastructure/messaging/`, mockando agora a **porta** e não a classe concreta
- [x] 5.3 Mover `shared/config/KafkaConsumerConfigTest` para `infrastructure/config/`
- [x] 5.4 Deixar `domain/enums/StatusAutorizacaoTest` e `TipoEventoAutorizacaoTest` onde estão
- [x] 5.5 Deixar `EventosConsumerApplicationTests` na raiz do pacote da app
- [x] 5.6 Confirmar que nenhum teste foi adicionado nem removido (D5)

## 6. Verificação

- [x] 6.1 `mvn clean compile` sem erros nem warnings novos
- [x] 6.2 `mvn test` verde, com a **mesma contagem** registrada em 1.1 — divergência bloqueia a entrega até ser explicada
- [x] 6.3 Confirmar por inspeção que nenhuma classe de `domain/` importa `org.springframework.*` nem `org.apache.kafka.*`
- [x] 6.4 Confirmar por inspeção que nenhuma classe de `application/` importa de `infrastructure/`
- [x] 6.5 Confirmar que `infrastructure/messaging/EventoAutorizacaoKafkaListener` depende da interface da porta, não da classe `...Service`
- [x] 6.6 Subir a app localmente (`infra/local/kafka/`) e confirmar que ela consome uma mensagem do tópico e loga o `tipoEvento` — a reorganização não pode ter quebrado a resolução do `containerFactory` por nome

## 7. Documentação

- [x] 7.1 Atualizar a seção de arquitetura de `apps/eventos-consumer/CLAUDE.md` com a árvore de pacotes nova
- [x] 7.2 Replicar **idêntico** em `apps/eventos-consumer/AGENTS.md` (os dois arquivos são espelhos)
- [x] 7.3 Conferir se algum link de "Comece por aqui" nesses arquivos aponta para caminho que deixou de existir
- [x] 7.4 Registrar no `design.md` qualquer divergência entre o que foi decidido em D1–D5 e o que a implementação realmente fez — as outras cinco mudanças herdam essas decisões
