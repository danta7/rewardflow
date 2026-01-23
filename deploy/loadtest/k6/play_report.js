import http from 'k6/http';
import { check, sleep } from 'k6';

// Run with:
//   docker run --rm -i --network host \
//     -e REWARDFLOW_BASE_URL=http://127.0.0.1:8080 \
//     -e REWARDFLOW_SCENE=audio_play \
//     grafana/k6 run - < deploy/loadtest/k6/play_report.js

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '2m', target: 200 },
    { duration: '2m', target: 200 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

const base = __ENV.REWARDFLOW_BASE_URL || 'http://127.0.0.1:8080';
const scene = __ENV.REWARDFLOW_SCENE || 'audio_play';
const fixedUserRatio = 0.2;
const fixedUserPoolSize = 50;

function randInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

export default function () {
  const useFixed = Math.random() < fixedUserRatio;
  const userId = useFixed
    ? `u${randInt(1, fixedUserPoolSize)}`
    : `u${__VU}-${__ITER % 10000}`;
  const soundId = `s${randInt(1, 1000)}`;
  const duration = randInt(1, 60);
  const syncTime = Date.now();

  const url = `${base}/api/v1/play/report`;
  const payload = JSON.stringify({ userId, soundId, duration, syncTime, scene });

  const res = http.post(url, payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-Request-Id': `${__VU}-${__ITER}`,
    },
  });

  const okStatus = res.status === 200;
  let okCode = false;
  try {
    const j = res.json();
    okCode = j && j.code === 0;
  } catch (e) {
    okCode = false;
  }

  if (__ENV.REWARDFLOW_DEBUG_FAIL === '1' && (!okStatus || !okCode)) {
    const body = (res.body || '').slice(0, 512);
    console.error(
      `k6_fail status=${res.status} vu=${__VU} iter=${__ITER} body=${body}`
    );
  }

  check(res, {
    'status is 200': (r) => r.status === 200,
    'code is 0': (r) => {
      try {
        const j = r.json();
        return j && j.code === 0;
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.1);
}
