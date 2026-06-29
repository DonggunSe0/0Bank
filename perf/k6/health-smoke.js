import http from 'k6/http';
import { check, sleep } from 'k6';

// 가장 가벼운 연결 확인용 테스트입니다.
// Spring Boot 앱이 살아 있고 Actuator health endpoint가 정상인지 확인합니다.
export const options = {
  // 환경변수로 VUS/DURATION을 넘기면 로컬 기본값을 덮어쓸 수 있습니다.
  vus: Number(__ENV.VUS || 1),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    // 요청 실패율은 1% 미만이어야 합니다.
    http_req_failed: ['rate<0.01'],
    // 전체 요청 중 95%가 300ms 안에 끝나야 합니다.
    http_req_duration: ['p(95)<300'],
    // check 성공률은 99%를 넘어야 합니다.
    checks: ['rate>0.99'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // 인증 없이 호출 가능한 health endpoint만 확인합니다.
  const res = http.get(`${baseUrl}/actuator/health`);

  check(res, {
    'health status is 200': (r) => r.status === 200,
    'health is UP': (r) => r.json('status') === 'UP',
  });

  // 너무 촘촘하게 호출하지 않도록 각 반복 사이에 잠깐 쉽니다.
  sleep(Number(__ENV.SLEEP_SECONDS || 1));
}
