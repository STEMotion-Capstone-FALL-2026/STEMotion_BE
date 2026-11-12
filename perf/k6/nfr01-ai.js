import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:18080/api/v1';
const DURATION = __ENV.DURATION || '3m';

const failureRate = new Rate('nfr01_failure_rate');
const aiDuration = new Trend('nfr01_ai_duration');

export const options = {
  stages: [
    { duration: '15s', target: 20 },
    { duration: DURATION, target: 50 },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<15000'],
    http_req_failed: ['rate<0.01'],
    nfr01_ai_duration: ['p(95)<15000'],
  },
};

export function setup() {
  const tokens = [];
  const tokenCount = parseInt(__ENV.TOKEN_COUNT || '20', 10);
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

  const payload = JSON.stringify({
    action: 'terms',
    scriptText: 'Trong tế bào thực vật, lục lạp chứa diệp lục để thực hiện quá trình quang hợp biến đổi năng lượng mặt trời thành năng lượng hoá học dưới dạng glucose và giải phóng oxy.',
    subject: 'Biology',
    gradeLevel: '8',
  });

  const res = http.post(`${BASE_URL}/ai/script/action`, payload, params);
  const ok = check(res, {
    'ai action status is 200': (r) => r.status === 200,
    'ai action returned result': (r) => {
      try {
        const body = r.json();
        return body && body.action === 'terms';
      } catch (e) {
        return false;
      }
    },
  });

  failureRate.add(!ok);
  aiDuration.add(res.timings.duration);

  sleep(1);
}
