#!/usr/bin/env bash
# Orquestra uma execucao de baseline (change testes-de-carga-tps): roda o cenario Gatling,
# liga os kill switches automaticos por fora (design.md D2), e SEMPRE limpa a massa de teste
# ao final -- sucesso, abort por kill switch, ou falha (tasks.md 3.3/7.5).
#
# Uso: rodar-cenario.sh <SimulationClass> [--com-fila-lag]
#   --com-fila-lag: liga tambem o monitor de profundidade de fila/lag Kafka (so faz sentido
#                   para JornadaCompostaSimulation, que dispara o pipeline assincrono).
set -uo pipefail

SIMULATION="${1:?uso: rodar-cenario.sh <SimulationClass> [--com-fila-lag]}"
COM_FILA_LAG="${2:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${MODULO_DIR}/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
RELATORIO="${MODULO_DIR}/relatorios/${SIMULATION##*.}-${TIMESTAMP}.md"

mkdir -p "${MODULO_DIR}/relatorios"

echo "=== Preparando limites de recursos (design.md D5) ==="
if ! docker compose --project-directory "$REPO_ROOT" config 2>/dev/null | grep -q "cpus:"; then
  echo "AVISO: nao foi possivel confirmar deploy.resources.limits no compose -- resultado pode nao ser valido (D5)."
fi

echo "=== Rodando ${SIMULATION} (${MODULO_DIR}) ==="
cd "$MODULO_DIR"
mvn -q io.gatling:gatling-maven-plugin:test -Dgatling.simulationClass="$SIMULATION" &
GATLING_PID=$!
echo "PID do Gatling: $GATLING_PID"

"${SCRIPT_DIR}/kill-switch-monitor.sh" "$GATLING_PID" &
MONITOR_APP_PID=$!

MONITOR_FILA_PID=""
if [ "$COM_FILA_LAG" = "--com-fila-lag" ]; then
  "${SCRIPT_DIR}/fila-lag-monitor.sh" "$GATLING_PID" &
  MONITOR_FILA_PID=$!
fi

wait "$GATLING_PID"
GATLING_EXIT=$?

kill "$MONITOR_APP_PID" 2>/dev/null || true
[ -n "$MONITOR_FILA_PID" ] && kill "$MONITOR_FILA_PID" 2>/dev/null || true

echo "=== Limpando massa de teste (D6, sempre executa) ==="
docker exec -i postgres18-kiq psql -U "${DB_USER_NAME:-docker}" -d "${DB_NAME:-db-csp-postgres}" \
  < "${SCRIPT_DIR}/limpar-massa-teste.sql" || echo "AVISO: limpeza falhou -- rode manualmente depois."

{
  echo "# Relatorio de execucao: ${SIMULATION}"
  echo
  echo "- Data/hora: $(date -Iseconds)"
  echo "- Codigo de saida do Gatling: ${GATLING_EXIT} ($([ "$GATLING_EXIT" -eq 0 ] && echo 'concluido normalmente' || echo 'interrompido -- ver se foi kill switch'))"
  echo "- Ambiente: SO LOCAL (docker-compose + Floci) -- nao representa capacidade de producao"
  echo "  (Requirement \"Escopo de ambiente local sem extrapolacao para producao\")."
  echo "- Configuracao vigente (baseline, sem recalibrar -- ver proposal.md):"
  echo "  - DB_POOL_MAX_SIZE=${DB_POOL_MAX_SIZE:-10 (default)}"
  echo "  - MAX_CONCURRENT_MESSAGES (SQS listeners) = 10 (hardcoded em SqsListenerContainerFactoryConfig)"
  echo "  - Kafka consumer concurrency = 1 (default, sem override em KafkaConsumerConfig)"
  echo "- Limites de recursos por container (D5): ver deploy.resources.limits em compose.yaml/apps/docker-compose.yml/infra/local/*."
  echo "- Relatorio HTML detalhado do Gatling: testes-carga/target/gatling/ (pasta mais recente)."
} > "$RELATORIO"

echo "=== Relatorio salvo em: $RELATORIO ==="
exit "$GATLING_EXIT"
