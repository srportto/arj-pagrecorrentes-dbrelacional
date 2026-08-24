#!/usr/bin/env bash
# Kill switch de nivel aplicacao e nivel host (change testes-de-carga-tps, design.md D2).
# Roda em paralelo a um cenario Gatling em execucao, monitorando:
#   - hikaricp_connections_pending do contratocommand e do contratoquery (sinal D1 -- mais
#     precoce que taxa de erro HTTP, ver /actuator/prometheus habilitado no profile local)
#   - uso agregado de CPU/memoria dos containers do compose (nivel host)
# Se qualquer limiar for ultrapassado de forma sustentada (N leituras seguidas), mata o
# processo do Gatling automaticamente -- nenhum criterio de parada aqui e manual (D2).
#
# Uso: kill-switch-monitor.sh <pid-do-processo-gatling>
set -euo pipefail

PID="${1:?uso: kill-switch-monitor.sh <pid-do-gatling>}"
INTERVALO_SEGUNDOS="${LOADTEST_MONITOR_INTERVALO:-5}"
LEITURAS_SUSTENTADAS="${LOADTEST_MONITOR_LEITURAS_SUSTENTADAS:-3}"
LIMITE_HIKARI_PENDING="${LOADTEST_LIMITE_HIKARI_PENDING:-0}"
LIMITE_CPU_HOST_PCT="${LOADTEST_LIMITE_CPU_HOST_PCT:-90}"

CONTRATOCOMMAND_URL="${CONTRATOCOMMAND_BASE_URL:-http://localhost:8080}/actuator/prometheus"
CONTRATOQUERY_URL="${CONTRATOQUERY_BASE_URL:-http://localhost:8081}/actuator/prometheus"

contador_pending=0
contador_cpu=0

ler_hikari_pending() {
  local url="$1"
  curl -s --max-time 2 "$url" 2>/dev/null \
    | grep '^hikaricp_connections_pending' \
    | grep -o '[0-9][0-9.]*$' \
    | head -1
}

ler_cpu_host_pct() {
  # Soma o %CPU de todos os containers do compose local (aproximacao de saturacao do host,
  # ja que nenhum container tem isolamento de rede/IO, so cpu/mem -- ver design.md D5).
  docker stats --no-stream --format "{{.CPUPerc}}" 2>/dev/null \
    | tr -d '%' \
    | awk '{sum += $1} END {print sum+0}'
}

echo "[kill-switch-monitor] observando PID $PID (hikari pending > ${LIMITE_HIKARI_PENDING}, cpu host > ${LIMITE_CPU_HOST_PCT}%)"

while kill -0 "$PID" 2>/dev/null; do
  pending_command="$(ler_hikari_pending "$CONTRATOCOMMAND_URL" || echo 0)"
  pending_query="$(ler_hikari_pending "$CONTRATOQUERY_URL" || echo 0)"
  pending_command="${pending_command:-0}"
  pending_query="${pending_query:-0}"

  maior_pending="$pending_command"
  if (( $(echo "$pending_query > $pending_command" | bc -l 2>/dev/null || echo 0) )); then
    maior_pending="$pending_query"
  fi

  if (( $(echo "$maior_pending > $LIMITE_HIKARI_PENDING" | bc -l 2>/dev/null || echo 0) )); then
    contador_pending=$((contador_pending + 1))
    echo "[kill-switch-monitor] hikaricp_connections_pending acima do limite ($maior_pending) -- leitura $contador_pending/$LEITURAS_SUSTENTADAS"
  else
    contador_pending=0
  fi

  cpu_host="$(ler_cpu_host_pct || echo 0)"
  if (( $(echo "$cpu_host > $LIMITE_CPU_HOST_PCT" | bc -l 2>/dev/null || echo 0) )); then
    contador_cpu=$((contador_cpu + 1))
    echo "[kill-switch-monitor] CPU agregada do host acima do limite (${cpu_host}%) -- leitura $contador_cpu/$LEITURAS_SUSTENTADAS"
  else
    contador_cpu=0
  fi

  if [ "$contador_pending" -ge "$LEITURAS_SUSTENTADAS" ]; then
    echo "[kill-switch-monitor] ABORT: hikaricp_connections_pending sustentado acima do limite -- matando PID $PID (D1/D2)"
    kill -9 "$PID" 2>/dev/null || true
    exit 1
  fi

  if [ "$contador_cpu" -ge "$LEITURAS_SUSTENTADAS" ]; then
    echo "[kill-switch-monitor] ABORT: CPU do host sustentada acima do limite -- matando PID $PID (nivel host, D2)"
    kill -9 "$PID" 2>/dev/null || true
    exit 1
  fi

  sleep "$INTERVALO_SEGUNDOS"
done

echo "[kill-switch-monitor] PID $PID encerrado normalmente -- nenhum kill switch acionado."
