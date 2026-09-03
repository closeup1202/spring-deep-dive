// load: 예상되는 평상시 트래픽을 재현한다. "이 정도는 문제없다" 를 증명하는 테스트.
// 목표 TPS 를 정하고, SLO 를 threshold 로 박아둔다. threshold 를 넘기면 k6 는 exit 1 을 준다.
//
//   k6 run loadtest/k6/load.js
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  // ramp-up 을 두는 이유: 처음부터 최대 부하를 때리면 JIT 예열 전 응답이 통계를 오염시킨다.
  stages: [
    { duration: '30s', target: 50 },   // ramp-up
    { duration: '2m', target: 50 },    // 이 구간이 측정 대상
    { duration: '30s', target: 0 },    // ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<300', 'p(99)<800'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = http.post(`${BASE}/api/orders?items=20`);
  check(res, { 'status is 200': (r) => r.status === 200 });
}
