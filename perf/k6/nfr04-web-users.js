import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:18080/api/v1';
const DURATION = __ENV.DURATION || '10m';

const failureRate = new Rate('nfr04_failure_rate');
const workflowDuration = new Trend('nfr04_workflow_duration');

export const options = {
  stages: [
    { duration: '30s', target: 200 },
    { duration: DURATION, target: 200 },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<3000'],
    http_req_failed: ['rate<0.01'],
  },
};

const vuTokens = {};

export default function () {
  const start = Date.now();
  let ok;
  const vuId = ((__VU - 1) % 200) + 1;
  const username = `user${String(vuId).padStart(3, '0')}`;
  const password = 'LoadTest-fixture-123';

  // 1. User logs in if session is not yet active for this VU
  if (!vuTokens[__VU]) {
    const loginRes = http.post(
      `${BASE_URL}/auth/login`,
      JSON.stringify({ username, password }),
      { headers: { 'Content-Type': 'application/json' } }
    );
    ok = check(loginRes, {
      'login status is 200': (r) => r.status === 200,
      'has token': (r) => {
        try {
          return !!r.json('accessToken');
        } catch (e) {
          return false;
        }
      },
    });
    failureRate.add(!ok);

    try {
      vuTokens[__VU] = loginRes.json('accessToken');
    } catch (e) {}
  }

  const token = vuTokens[__VU];
  if (!token) {
    sleep(1);
    return;
  }

  const authParams = {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  };

  // Think time after login
  sleep(1);

  // 2. Open workspace list
  const wsRes = http.get(`${BASE_URL}/workspaces/mine`, authParams);
  ok = check(wsRes, { 'workspaces status is 200': (r) => r.status === 200 });
  failureRate.add(!ok);

  sleep(1);

  // 3. Open user scripts
  const scriptsRes = http.get(`${BASE_URL}/scripts/mine`, authParams);
  ok = check(scriptsRes, { 'scripts status is 200': (r) => r.status === 200 });
  failureRate.add(!ok);

  let scriptId = null;
  try {
    const scripts = scriptsRes.json();
    if (Array.isArray(scripts) && scripts.length > 0) {
      scriptId = scripts[0].id;
    }
  } catch (e) {}

  sleep(1);

  // 4. Open project/script detail
  if (scriptId) {
    const detailRes = http.get(`${BASE_URL}/scripts/${scriptId}`, authParams);
    ok = check(detailRes, { 'script detail status is 200': (r) => r.status === 200 });
    failureRate.add(!ok);
  }

  workflowDuration.add(Date.now() - start);
  sleep(1);
}
