#!/bin/sh
# Configura a DLQ da fila de eventos do Realtime via API de gerenciamento do RabbitMQ.
# Idempotente: pode rodar a cada "docker compose up".
#
# Por que policy e não argumentos na fila: a fila market.realtime.events é declarada pelo
# Market Service sem argumentos. Declará-la de novo com x-dead-letter-exchange daria
# PRECONDITION_FAILED. Uma policy acrescenta o dead-lettering sem mudar a declaração.
set -eu

API="${RABBITMQ_API:-http://rabbitmq:15672/api}"
AUTH="${RABBITMQ_DEFAULT_USER:-stock_market}:${RABBITMQ_DEFAULT_PASS:-stock_market}"
VHOST="%2F"
QUEUE="market.realtime.events"
DLX="market.events.dlx"
DLQ="market.realtime.events.dlq"

call() {
  method="$1"; path="$2"; body="$3"
  code=$(curl -s -o /tmp/resp -w '%{http_code}' -u "$AUTH" -H 'content-type: application/json' \
    -X "$method" "$API$path" -d "$body")
  case "$code" in
    2??) echo "ok   $method $path" ;;
    *)   echo "erro $method $path -> HTTP $code: $(cat /tmp/resp)"; exit 1 ;;
  esac
}

echo "Aguardando API de gerenciamento em $API ..."
i=0
until curl -sf -u "$AUTH" "$API/overview" >/dev/null; do
  i=$((i + 1)); [ "$i" -ge 60 ] && { echo "RabbitMQ não respondeu"; exit 1; }
  sleep 2
done

call PUT  "/exchanges/$VHOST/$DLX" '{"type":"fanout","durable":true,"auto_delete":false}'
call PUT  "/queues/$VHOST/$DLQ"    '{"durable":true,"auto_delete":false}'
call POST "/bindings/$VHOST/e/$DLX/q/$DLQ" '{"routing_key":""}'
call PUT  "/policies/$VHOST/market-realtime-dlx" \
  "{\"pattern\":\"^market[.]realtime[.]events\$\",\"apply-to\":\"queues\",\"priority\":0,\"definition\":{\"dead-letter-exchange\":\"$DLX\"}}"

echo "DLQ configurada: $QUEUE -> $DLX -> $DLQ"
