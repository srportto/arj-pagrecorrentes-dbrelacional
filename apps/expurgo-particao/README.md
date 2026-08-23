# expurgo-particao

Lambda Python agendada que fecha o ring buffer de expurgo do `contratocommand`: a cada 30 minutos
calcula a partição de expurgo permitida do ciclo, verifica seu estado e a esvazia (`TRUNCATE`)
quando contém dado do ciclo anterior — nunca sobre dado ainda protegido pela retenção.

Diferente das cinco apps Java, esta não é um serviço de vida longa nem expõe porta HTTP: é uma
função invocada por EventBridge Scheduler, empacotada como imagem de Lambda.

Para as decisões de desenho (por que `TRUNCATE`, por que offset +2, por que sem fase de dry-run,
etc.) e as armadilhas ao mexer neste código, veja [CLAUDE.md](CLAUDE.md) — este README cobre apenas
como rodar os testes e como a Lambda é invocada.

## Pré-requisitos

- **Python 3.13** (mesma versão da imagem base `public.ecr.aws/lambda/python:3.13`)
- **Postgres local no ar**, com a migration `v1.0.7.-cria-tabela-registro-expurgo-particao.sql` já
  aplicada — ver [infra/local/postgres/README.md](../../infra/local/postgres/README.md)
- **`EXPURGO_PARTICAO_TEST_DSN`** definida para rodar os testes de integração (sem valor padrão —
  falha explícita nomeando a variável ausente é preferível a um default com credencial, mesma
  convenção de `gestao-de-segredos`)

## Instalação e testes

```bash
cd apps/expurgo-particao
pip install -r requirements-dev.txt

# testes de integração exigem o Postgres local no ar
export EXPURGO_PARTICAO_TEST_DSN="postgresql://docker:<sua-senha>@localhost:5432/db-csp-postgres"
pytest
```

Não há servidor para subir localmente — a rotina (`expurgo_particao.rotina.executar`) é chamada
diretamente pelos testes ou pelo `handler.lambda_handler`, nunca por um processo residente.

## Variáveis de ambiente

| Variável | Obrigatória | Descrição |
|---|---|---|
| `DB_HOST` | sim | Host do Postgres. Na Lambda local (Floci), `host.docker.internal` — ver CLAUDE.md |
| `DB_PORT` | não (default `5432`) | Porta do Postgres |
| `DB_NAME` | sim | Nome do banco |
| `DB_USER_NAME` | sim | Usuário do Postgres |
| `DB_PASSWORD` | sim | Senha — sem default, mesma convenção de `gestao-de-segredos` |
| `LOG_LEVEL` | não (default `INFO`) | Nível de log Python |
| `EXPURGO_PARTICAO_DESARMAR_TRUNCATE` | não | Interruptor operacional: quando `1`/`true`/`yes`/`on`, a rotina continua calculando e registrando, mas não executa o `TRUNCATE` |

## Evento de invocação

Todos os campos são opcionais:

```json
{
  "data_referencia": "2028-04-20",
  "modo_consulta": true
}
```

- `data_referencia` (`AAAA-MM-DD`): data usada no lugar da data corrente (UTC) para o cálculo da
  partição alvo. Capacidade permanente, não um modo de teste temporário — é o que permite perguntar
  "o que a rotina faria em 2028-04-20?" sem esperar o calendário chegar lá.
- `modo_consulta` (`bool`, default `false`): quando `true`, a rotina calcula e classifica
  normalmente, mas nunca executa `TRUNCATE`, mesmo que o estado da partição seja
  `DADO_CICLO_ANTERIOR`.

A resposta (`statusCode: 200`, `body`) sempre registra o que foi calculado — inclusive quando a
partição está vazia e nada é feito. É esse registro, gravado também na tabela
`expurgo_particao_registro`, que torna uma execução sem efeito distinguível de uma rotina quebrada.

## Documentação relacionada

- [reclamacao-particao-expurgo](../../openspec/specs/reclamacao-particao-expurgo/spec.md) — contrato
  vigente da reclamação periódica
- [infra/local/postgres/README.md](../../infra/local/postgres/README.md) — como subir o Postgres
  local com a migration desta app aplicada
- CLAUDE.md deste diretório — armadilhas e decisões de desenho

## Licença

MIT — veja [LICENSE](../../LICENSE) na raiz do repositório.
