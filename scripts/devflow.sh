#!/usr/bin/env bash
set -euo pipefail

ORQUESTRADOR=${DEVFLOW_URL:-http://localhost:8070}
CAMUNDA=${CAMUNDA_REST_URL:-http://localhost:8180}

uso() {
  cat <<'TXT'
uso:
  devflow.sh nova <repositorio> "<descrição>" [branch-base] [tarefa]
  devflow.sh pendentes
  devflow.sh aguardar <processInstanceKey>
  devflow.sh concluir <userTaskKey> '<variáveis em JSON>'
  devflow.sh incidentes
TXT
  exit 1
}

post() {
  curl -sf -X POST "$1" -H 'Content-Type: application/json' -d "$2"
}

pendentes() {
  post "$CAMUNDA/v2/user-tasks/search" "{\"filter\":{\"state\":\"CREATED\"${1:+,\"processInstanceKey\":\"$1\"}}}" |
    python3 -c 'import json,sys
for t in json.load(sys.stdin)["items"]:
    print(t["userTaskKey"], t["elementId"], t["processInstanceKey"], sep="\t")'
}

incidentes() {
  post "$CAMUNDA/v2/incidents/search" "{\"filter\":{\"state\":\"ACTIVE\"${1:+,\"processInstanceKey\":\"$1\"}}}" |
    python3 -c 'import json,sys
for i in json.load(sys.stdin)["items"]:
    print(i["incidentKey"], i["elementId"], i["errorMessage"][:300], sep="\t")'
}

case "${1:-}" in
  nova)
    [ $# -ge 3 ] || uso
    python3 - "$2" "$3" "${4:-main}" "${5:-}" <<'PY' | curl -sf -X POST "$ORQUESTRADOR/tarefas" -H 'Content-Type: application/json' -d @-
import json, sys
pedido = {"repositorio": sys.argv[1], "descricao": sys.argv[2], "branchBase": sys.argv[3]}
if sys.argv[4]:
    pedido["tarefa"] = sys.argv[4]
print(json.dumps(pedido))
PY
    echo
    ;;
  pendentes) pendentes ;;
  incidentes) incidentes ;;
  aguardar)
    [ $# -eq 2 ] || uso
    while true; do
      sleep 10
      tarefas=$(pendentes "$2")
      falhas=$(incidentes "$2")
      if [ -n "$tarefas$falhas" ]; then
        [ -n "$tarefas" ] && echo "$tarefas"
        [ -n "$falhas" ] && echo "incidente: $falhas"
        break
      fi
      estado=$(curl -sf "$CAMUNDA/v2/process-instances/$2" | python3 -c 'import json,sys; print(json.load(sys.stdin)["state"])')
      [ "$estado" != "ACTIVE" ] && { echo "processo $estado"; break; }
    done
    ;;
  concluir)
    [ $# -eq 3 ] || uso
    post "$CAMUNDA/v2/user-tasks/$2/completion" "{\"variables\":$3}" && echo "ok"
    ;;
  *) uso ;;
esac
