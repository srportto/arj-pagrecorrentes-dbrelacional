## ADDED Requirements

### Requirement: Módulo de serviço parametrizável

O módulo `ecs-service` SHALL ser parametrizável, aceitando no mínimo: imagem do
container, porta da aplicação, variáveis de ambiente, requisitos de CPU e memória, e o
caminho do health check. O mesmo módulo SHALL poder ser instanciado múltiplas vezes com
parâmetros diferentes.

#### Scenario: Instanciação parametrizada
- **WHEN** o módulo é instanciado com imagem, porta, CPU/memória e env específicos
- **THEN** a task definition e o service resultantes refletem exatamente esses parâmetros

### Requirement: Tasks em Fargate nas subnets privadas

O módulo `ecs-service` SHALL executar suas tasks em Fargate, posicionadas nas subnets
privadas da VPC, registrando-as em um target group associado ao ALB do módulo
`ecs-cluster`.

#### Scenario: Task em subnet privada
- **WHEN** o serviço é aplicado
- **THEN** as tasks são agendadas em Fargate dentro das subnets privadas

#### Scenario: Registro no ALB
- **WHEN** o serviço é aplicado
- **THEN** as tasks são registradas em um target group vinculado ao listener do ALB

### Requirement: Health check da aplicação

O módulo `ecs-service` SHALL configurar o health check do target group para o caminho
`/actuator/health` na porta da aplicação, marcando a task como saudável somente quando
esse endpoint responder com sucesso.

#### Scenario: Health check em /actuator/health
- **WHEN** o serviço é aplicado
- **THEN** o target group verifica a saúde da task via `GET /actuator/health`

### Requirement: Injeção de configuração via ambiente

O módulo `ecs-service` SHALL injetar nas tasks a variável `SPRING_PROFILES_ACTIVE` e as
credenciais de acesso ao banco via variáveis de ambiente. As credenciais NÃO SHALL ser
fixadas em código do módulo, sendo recebidas como parâmetros de entrada.

#### Scenario: Perfil e credenciais injetados
- **WHEN** o serviço é aplicado com um perfil e credenciais de banco informados
- **THEN** o container recebe `SPRING_PROFILES_ACTIVE` e as variáveis de credenciais de
  banco correspondentes

### Requirement: Serviços contratocommand e contratoquery

O ambiente SHALL instanciar o módulo `ecs-service` duas vezes: uma para
`contratocommand` expondo a porta `8080`, e outra para `contratoquery` expondo a
porta `8081`, cada uma consumindo a imagem construída a partir do respectivo `Dockerfile`
em `apps/`.

#### Scenario: contratocommand na porta 8080
- **WHEN** a composição do ambiente é aplicada
- **THEN** existe um ECS Service para `contratocommand` cujo container escuta na
  porta `8080`

#### Scenario: contratoquery na porta 8081
- **WHEN** a composição do ambiente é aplicada
- **THEN** existe um ECS Service para `contratoquery` cujo container escuta na
  porta `8081`
