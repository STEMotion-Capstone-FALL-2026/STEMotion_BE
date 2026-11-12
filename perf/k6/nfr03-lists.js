import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:18080/api/v1';
const DURATION = __ENV.DURATION || '5m';

const failureRate = new Rate('nfr03_failure_rate');
const listDuration = new Trend('nfr03_list_duration');

export const options = {
  stages: [
    { duration: '30s', target: 200 },
    { duration: DURATION, target: 200 },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<3000'],
    http_req_failed: ['rate<0.01'],
    nfr03_list_duration: ['p(95)<3000'],
  },
};

export function setup() {
  const tokens = [];
  const tokenCount = parseInt(__ENV.TOKEN_COUNT || '50', 10);
  for (let i = 1; i <= tokenCount; i++) {
    const pad = String(i).padStart(3, '0');
    const username = `user${pad}`;
    const payload = JSON.stringify({
      username: username,
      password: 'LoadTest-fixture-123',
    });
    const res = http.post(`${BASE_URL}/auth/login`, payload, {
      headers: { 'Content-Type': 'application/json' },
    });
    if (res.status === 200) {
      const body = res.json();
      if (body && body.accessToken) {
        tokens.push(body.accessToken);
      }
    }
  }
  if (tokens.length === 0) {
    throw new Error('Failed to obtain any auth tokens in setup()');
  }
  return { tokens };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const params = {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  };

  // 1. GET /workspaces/mine
  let res = http.get(`${BASE_URL}/workspaces/mine`, params);
  let ok = check(res, { 'workspaces status is 200': (r) => r.status === 200 });
  failureRate.add(!ok);
  listDuration.add(res.timings.duration);

  // 2. GET /scripts/mine
  res = http.get(`${BASE_URL}/scripts/mine`, params);
  ok = check(res, { 'scripts status is 200': (r) => r.status === 200 });
  failureRate.add(!ok);
  listDuration.add(res.timings.duration);

  // 3. GET /scripts/review-queue
  res = http.get(`${BASE_URL}/scripts/review-queue`, params);
  ok = check(res, { 'review-queue status is 200': (r) => r.status === 200 });
  failureRate.add(!ok);
  listDuration.add(res.timings.duration);

  // 4. GET /library/clips
  res = http.get(`${BASE_URL}/library/clips`, params);
  ok = check(res, { 'library clips status is 200': (r) => r.status === 200 });
  failureRate.add(!ok);
  listDuration.add(res.timings.duration);

  sleep(1);
}
