#!/usr/bin/env bash
# Kill switch de nivel fila/lag (change testes-de-carga-tps, design.md D2) -- so relevante
# para o cenario de jornada composta (JornadaCompostaSimulation), que dispara o pipeline
# assincrono. "Colapso" aqui nao e erro HTTP -- e atraso acumulado (profundidade de fila SQS
# crescente, lag de consumer group Kafka crescente), por isso precisa de monitor proprio,
# separado do kill-switch-monitor.sh (que cobre so o lado sincrono).
#
# Uso: fila-lag-monitor.sh <pid-do-processo-gatling>
set -euo pipefail

PID="${1:?uso: fila-lag-monitor.sh <pid-do-gatling>}"
INTERVALO_SEGUNDOS="${LOADTEST_MONITOR_INTERVALO:-5}"
LEITURAS_SUSTENTADAS="${LOADTEST_MONITOR_LEITURAS_SUSTENTADAS:-3}"
LIMITE_FILA_SQS="${LOADTEST_LIMITE_FILA_SQS:-1000}"
LIMITE_LAG_KAFKA="${LOADTEST_LIMITE_LAG_KAFKA:-1000}"

FLOCI_ENDPOINT="${FLOCI_ENDPOINT:-http://localhost:4566}"
AWS_REGION="${AWS_REGION:-us-east-1}"
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"

SQS_EVENTOS_URL="http://localhost:4566/000000000000/SQS-eventos-autorizacao"
SQS_TEMPORIZACAO_URL="http://localhost:4566/000000000000/SQS-temporizacao-autorizacao"
KAFKA_CONTAINER="kafka-eventos-autorizacao"
KAFKA_GROUP="eventos-consumer"

contador=0

ler_profundidade_fila() {
  local url="$1"
  aws --endpoint-url "$FLOCI_ENDPOINT" --region "$AWS_REGION" sqs get-queue-attributes \
    --queue-url "$url" --attribute-names ApproximateNumberOfMessages \
    --query 'Attributes.ApproximateNumberOfMessages' --output text 2>/dev/null || echo 0
}

ler_lag_kafka_total() {
  docker exec "$KAFKA_CONTAINER" kafka-consumer-groups \
    --bootstrap-server localhost:9092 --describe --group "$KAFKA_GROUP" 2>/dev/null \
    | awk 'NR>1 && $6 ~ /^[0-9]+$/ {sum += $6} END {print sum+0}'
}

echo "[fila-lag-monitor] observando PID $PID (fila SQS > ${LIMITE_FILA_SQS}, lag Kafka > ${LIMITE_LAG_KAFKA})"

while kill -0 "$PID" 2>/dev/null; do
  fila_eventos="$(ler_profundidade_fila "$SQS_EVENTOS_URL")"
  fila_temporizacao="$(ler_profundidade_fila "$SQS_TEMPORIZACAO_URL")"
  lag_kafka="$(ler_lag_kafka_total)"

  maior_fila=$(( fila_eventos > fila_temporizacao ? fila_eventos : fila_temporizacao ))

  echo "[fila-lag-monitor] fila-eventos=$fila_eventos fila-temporizacao=$fila_temporizacao lag-kafka=$lag_kafka"

  if [ "$maior_fila" -gt "$LIMITE_FILA_SQS" ] || [ "$lag_kafka" -gt "$LIMITE_LAG_KAFKA" ]; then
    contador=$((contador + 1))
    echo "[fila-lag-monitor] limiar ultrapassado -- leitura $contador/$LEITURAS_SUSTENTADAS"
  else
    contador=0
  fi

  if [ "$contador" -ge "$LEITURAS_SUSTENTADAS" ]; then
    echo "[fila-lag-monitor] ABORT: profundidade de fila ou lag sustentado acima do limite -- matando PID $PID (D2)"
    kill -9 "$PID" 2>/dev/null || true
    exit 1
  fi

  sleep "$INTERVALO_SEGUNDOS"
done

echo "[fila-lag-monitor] PID $PID encerrado normalmente -- nenhum kill switch acionado."
