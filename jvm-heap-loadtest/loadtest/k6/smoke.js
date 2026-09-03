// smoke: "부하 테스트가 돌아가는지" 를 확인하는 테스트. 성능 측정이 아니다.
// VU 1~2명, 1분 이하. 배포 파이프라인에 항상 넣어둔다.
//
//   k6 run loadtest/k6/smoke.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    // 스모크에서 실패하면 나머지 시나리오는 돌릴 가치가 없다. abortOnFail 로 즉시 중단.
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true }],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const res = http.post(`${BASE}/api/orders?items=20`);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'has id': (r) => r.json('id') !== undefined,
  });
  sleep(1);
}
