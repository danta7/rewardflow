#!/usr/bin/env bash
set -euo pipefail

# 把奖励规则发布到 Nacos 配置中心
#
# Default creds are nacos/nacos (commonly used in local docker setups).

NACOS_ADDR="${NACOS_ADDR:-localhost:8848}"
NACOS_USER="${NACOS_USERNAME:-nacos}"
NACOS_PASS="${NACOS_PASSWORD:-nacos}"
GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
DATA_ID="${DATA_ID:-rewardflow-award-rules.json}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONTENT_FILE="${SCRIPT_DIR}/rewardflow-award-rules.json"

if [[ ! -f "${CONTENT_FILE}" ]]; then
  echo "rule file not found: ${CONTENT_FILE}" >&2
  exit 1
fi

CONTENT="$(cat "${CONTENT_FILE}")"

echo "Publishing rules to Nacos... server=${NACOS_ADDR} dataId=${DATA_ID} group=${GROUP}"

# Nacos openapi: /nacos/v1/cs/configs
# Some builds require user/pass.
curl -fsS -X POST "http://${NACOS_ADDR}/nacos/v1/cs/configs" \
  --data-urlencode "dataId=${DATA_ID}" \
  --data-urlencode "group=${GROUP}" \
  --data-urlencode "type=TEXT" \
  --data-urlencode "username=${NACOS_USER}" \
  --data-urlencode "password=${NACOS_PASS}" \
  --data-urlencode "content=${CONTENT}" \
  > /dev/null

echo "Done. You can now query /api/v1/rules/config to verify loaded config (may take a few seconds)."
