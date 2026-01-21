#!/usr/bin/env bash
set -euo pipefail

# Publish feature switches to Nacos
# Requires: curl
# Env:
#   NACOS_ADDR (default localhost:8848)
#   NACOS_GROUP (default DEFAULT_GROUP)
#   NACOS_NAMESPACE (optional)
#   NACOS_USER / NACOS_PASS (optional)

ADDR="${NACOS_ADDR:-localhost:8848}"
GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
NAMESPACE="${NACOS_NAMESPACE:-}"
USER="${NACOS_USER:-}"
PASS="${NACOS_PASS:-}"

DATA_ID="rewardflow-feature-switches.json"
CONTENT_FILE="$(cd "$(dirname "$0")" && pwd)/rewardflow-feature-switches.json"

echo "Publishing ${DATA_ID} to Nacos ${ADDR}, group=${GROUP}, namespace=${NAMESPACE}"

AUTH_ARGS=()
if [[ -n "${USER}" ]]; then
  AUTH_ARGS+=("-u" "${USER}:${PASS}")
fi

curl -sS "${AUTH_ARGS[@]}" -X POST "http://${ADDR}/nacos/v1/cs/configs" \
  --data-urlencode "dataId=${DATA_ID}" \
  --data-urlencode "group=${GROUP}" \
  --data-urlencode "tenant=${NAMESPACE}" \
  --data-urlencode "type=json" \
  --data-urlencode "content@${CONTENT_FILE}" \
  | cat

echo
echo "Done."
