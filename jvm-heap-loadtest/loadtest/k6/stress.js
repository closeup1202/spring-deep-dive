// stress: 한계점을 찾는다. 목표는 통과가 아니라 "어디서 어떻게 무너지는가" 를 관찰하는 것.
// TPS 가 더 이상 오르지 않는 지점(saturation point)과, 그 이후의 붕괴 양상을 본다.
//
//   k6 run loadtest/k6/stress.js
//
// 관찰 포인트:
//   - TPS 는 평평한데 응답시간만 늘어난다 -> 스레드/커넥션 큐 대기
//   - 응답시간과 GC pause 가 동시에 튄다 -> 힙 압박
//   - 에러가 timeout 부터 난다 -> 스레드 고갈 / 커넥션 풀 고갈
import http from 'k6/http';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  stages: [
    { duration: '1m', target: 50 },
    { duration: '1m', target: 150 },
    { duration: '1m', target: 300 },
    { duration: '1m', target: 500 },
    { duration: '1m', target: 0 },
  ],
  // stress 에서는 threshold 를 느슨하게 둔다. 깨지는 것을 보는 게 목적이기 때문이다.
  thresholds: {
    http_req_failed: ['rate<0.30'],
  },
};

export default function () {
  http.get(`${BASE}/lab/blocking?millis=300&bufferKb=512`);
}
