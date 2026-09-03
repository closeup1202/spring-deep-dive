// spike: 트래픽이 순간적으로 튀는 상황. 티켓 오픈, 푸시 발송, 장애 복구 직후의 재접속 폭주.
// 관심사는 두 가지다. (1) 버티는가 (2) 부하가 빠진 뒤 스스로 회복하는가.
//
//   k6 run loadtest/k6/spike.js
//
// 회복하지 못하는 전형적인 이유:
//   - 스레드/커넥션 풀이 큐에 쌓인 요청을 계속 처리하느라 못 빠져나온다
//   - 스파이크 구간에 만들어진 객체가 Old 로 승격돼 Full GC 를 유발한다
//   - 재시도 로직이 부하를 스스로 증폭시킨다 (retry storm)
import http from 'k6/http';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  stages: [
    { duration: '30s', target: 20 },   // 평상시
    { duration: '10s', target: 400 },  // 급증
    { duration: '1m', target: 400 },   // 유지
    { duration: '10s', target: 20 },   // 급감
    { duration: '2m', target: 20 },    // 회복 관찰 구간 - 여기가 핵심이다
  ],
};

export default function () {
  http.post(`${BASE}/api/orders?items=20`);
}
