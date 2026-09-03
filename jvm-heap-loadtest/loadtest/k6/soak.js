// soak(내구성): 낮은 부하를 오래 건다. 메모리 누수와 커넥션 누수는 이것으로만 잡힌다.
// 30분짜리 부하 테스트에서 멀쩡하던 서비스가 3일 뒤 새벽에 죽는 이유가 여기 있다.
//
//   k6 run loadtest/k6/soak.js
//   k6 run -e TARGET_PATH=/lab/leak?count=5&payloadKb=50 loadtest/k6/soak.js   # 누수 재현
//
// 같이 볼 것 (반드시 함께 봐야 한다):
//   watch -n 10 'curl -s localhost:8080/lab/heap | jq .used'
//   jcmd <pid> GC.heap_info
//   GC 로그의 Full GC 직후 Old 사용량 (= 저점). 저점이 우상향하면 누수다.
import http from 'k6/http';
import { sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const PATH = __ENV.TARGET_PATH || '/api/orders?items=20';

export const options = {
  stages: [
    { duration: '2m', target: 30 },
    { duration: '1h', target: 30 },   // 실습에서는 10m 정도로 줄여도 된다
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    // 시간이 지나면서 느려지는지가 핵심. 전체 p95 하나로는 잘 안 보이니
    // k6 요약보다 시계열(Grafana, 또는 --out json) 로 보는 것이 맞다.
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  http.post(`${BASE}${PATH}`);
  sleep(1);
}
