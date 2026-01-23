#!/usr/bin/env bash
set -euo pipefail

BASE_URL=${REWARDFLOW_BASE_URL:-http://127.0.0.1:8080}
SCENE=${REWARDFLOW_SCENE:-audio_play}

echo "Running k6 load test..."
echo "BASE_URL=$BASE_URL"
echo "SCENE=$SCENE"

# Using host network is the simplest on Linux.
docker run --rm -i --network host \
  -e REWARDFLOW_BASE_URL="$BASE_URL" \
  -e REWARDFLOW_SCENE="$SCENE" \
  -e REWARDFLOW_DEBUG_FAIL="${REWARDFLOW_DEBUG_FAIL:-}" \
  grafana/k6 run - < deploy/loadtest/k6/play_report.js
