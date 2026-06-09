# Arquitetura do Sistema de Pagamentos Recorrentes: Núcleo de Gestão de Autorizações e Contratos

A **Parte 01** deste sistema concentra-se no núcleo de gestão da entidade de autorizações e contratos, desenhado para suportar alta escalabilidade em sistemas de agendamento massivo, como o Pix Automático. A arquitetura aqui descrita é a implementação direta da prova de conceito (POC) validada de **Particionamento com Buffer Ring e UUID-v7 Reversível**.

Esta fase engloba cinco pilares fundamentais:

1. **Modelagem de Dados**: Estruturar a base de dados e as aplicações para gerir a entidade de autorização, resolvendo gargalos de crescimento de dados (*Hot Partition Problem*).
2. **Aplicações CQRS**: Criar aplicações separadas de *Query* e *Command* para realizar ações na entidade (incluir, alterar, consultar e eliminar).
3. **Gateway**: Expor e proteger as rotas de acesso e o controlo da entidade.
4. **Gestão de Expurgo (Buffer Ring)**: Controlar a limpeza da base de dados através da eliminação (`DROP`) e recriação de partições de forma contínua e previsível.
5. **Mensageria e Eventos**: Produzir e propagar eventos de estado da entidade para servir de forma resiliente as camadas:
* **Data-driven**: Sensibilizar e garantir a entrega no tópico Kafka de "eventos".
* **Comunicação**: Sensibilizar e garantir a entrega no tópico Kafka de "comunicações".



---

## 🛠️ Aplicações e Serviços

A maioria das aplicações foi desenhada numa stack focada em performance (*Cloud-Native*), utilizando **Java 25**, encapsuladas em contentores **ECS** na AWS e suportadas pelo servidor web embutido **Undertow**.

> ⚠️ **Nota de implementação (Spring Boot 4.0):** o **Undertow foi REMOVIDO no Spring Boot 4.0** — o BOM 4.0.x gerencia apenas **Tomcat** e **Jetty** para web MVC (mais `reactor-netty` para reativo). Como a stack fixada do projeto é Spring Boot **4.0.4**, as aplicações **não podem** usar Undertow sem downgrade para a linha 3.x. O substituto leve recomendado nesta stack é o **Jetty** (ex.: `arj-contratoquery` usa Jetty). Mantenha esta referência ao Undertow apenas como intenção arquitetural histórica até que a stack seja reavaliada.

### 1. `contratocommand`

* **Tipo**: Aplicação Java (25) em contentor ECS com Undertow.
* **Contexto**: Responsável por receber e processar as ações de mutação de dados:
* Criar autorização
* Alterar autorização
* Cancelar autorização


* **Enriquecimento Arquitetural (POC)**: Aquando da criação de uma autorização, esta aplicação calcula o *hash* do UUID da conta (ex: `idUnicoContaContratante`) e aplica uma operação de módulo para determinar qual a partição ativa que receberá o registo. No momento em que uma autorização transita para o estado "cancelado", a aplicação atualiza a chave primária, fazendo com que o PostgreSQL mova o registo de forma totalmente automática das partições ativas para a zona do *Ring Buffer* de expurgo.

### 2. `contratoquery`

* **Tipo**: Aplicação Java (25) em contentor ECS com Undertow.
* **Contexto**: Desenhada exclusivamente para operações de leitura otimizada, permitindo:
* Consulta de listas de autorizações.
* Consulta detalhada de uma autorização específica.



### 3. `limpezadb`

* **Tipo**: Função Lambda em Python.
* **Contexto**: Responsável pelo motor de expurgo a "custo zero" de I/O. As suas ações incluem:
* Consultar o estado das partições de expurgo.
* Efetuar o *drop* de partições obsoletas.
* Recriar imediatamente as partições eliminadas para fecharem o ciclo.


* **Enriquecimento Arquitetural (POC)**: Esta aplicação materializa a mecânica do *Buffer Ring*. Em vez de executar operações lentas de `DELETE` (que bloqueiam a tabela e geram *dead tuples*), a Lambda avalia qual a partição da janela temporal antiga (com um ciclo de segurança de aproximadamente 100 semanas / 2 anos) e executa um `DROP TABLE PARTITION`. Isto liberta instantaneamente o espaço em disco na infraestrutura, sem impactar a performance das tabelas "quentes".

### 4. `contratoeventos-producer`

* **Tipo**: Aplicação Java (25) em contentor ECS com Undertow.
* **Contexto**: Serve como ponte para a camada analítica e de estado da entidade. O seu papel é:
* Consumir os eventos em fila originados pela máquina de estados (`sqs-contrato-maq-eventos`).
* Publicar a consolidação deste evento de estado diretamente no tópico Kafka "eventos".



### 5. `comunicacao-producer`

* **Tipo**: Aplicação Java (25) em contentor ECS com Undertow.
* **Contexto**: Ponte focada na interação com o cliente final. Ações:
* Consumir os eventos de notificação retidos na fila `sqs-contrato-comunicacoes`.
* Publicar as ordens de aviso e alertas no tópico Kafka "comunicações".



### 6. `eventos-consumer`

* **Tipo**: Aplicação Java (25) em contentor ECS com Undertow.
* **Contexto**: Destinado a alimentar projeções ou sistemas dependentes de dados:
* Consumir os eventos consolidados do tópico Kafka "eventos".
* Processar e registar os logs (auditoria/sucesso) de consumo.



### 7. `comunicacoes-consumer`

* **Tipo**: Aplicação Java (25) em contentor ECS com Undertow.
* **Contexto**: O efetuador final de notificações:
* Consumir as ordens do tópico Kafka "comunicações".
* Processar o envio da comunicação adequada e registar o sucesso da operação.



---

## 📨 Estrutura de Mensageria

A base assíncrona foi estruturada para suportar um fluxo contínuo e escalável de processamento de contratos:

* **Tópicos**:
* `sns-contrato-estados`: Tópico SNS (padrão) para dispersão rápida (pub/sub) das transições de estado.
* `eventos`: Tópico Kafka dedicado à cronologia e estado (Data-Driven).
* `comunicações`: Tópico Kafka exclusivo para eventos que exijam contacto ou notificação ao pagador.


* **Filas**:
* `sqs-contrato-maq-eventos`: Fila SQS padrão para retenção elástica antes da passagem a Kafka.
* `sqs-contrato-comunicacoes`: Fila SQS padrão.



---

## 🗄️ Base de Dados

* **Tecnologia**: **PostgreSQL** (AWS RDS v16).
* **Estratégia de Organização (Particionamento LIST)**:
Com base na arquitetura homologada, a tabela física de autorizações no PostgreSQL deixa de estar estrangulada numa única grande tabela. O modelo adota uma topologia de listas (`LIST`) assente num identificador de partição, distribuída em dois grandes blocos:
1. **889 Partições Ativas** (Range `0` a `888`): Recebem as novas autorizações e toda a carga de vigência e consultas ativas. A distribuição é uniforme, quebrando totalmente a dependência de um único disco físico ou limite de CPU.
2. **100 Partições de Expurgo / Ring Buffer** (Range `900` a `999`): Reservadas em exclusivo para guardar dados frios (cancelados). É este segmento isolado que a aplicação `limpezadb` vai limpar recorrentemente, evitando qualquer degradação no ecossistema central do RDS.