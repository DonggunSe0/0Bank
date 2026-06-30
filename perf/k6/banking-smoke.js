import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import {
  baseUrl,
  buildAccountActors,
  jsonHeaders,
  login as requestLogin,
  pick,
} from './lib/banking.js';

// 로그인 이후 실제 인증 API를 짧게 확인하는 스모크 테스트입니다.
// 테스트 계정이 필요하며, 계좌 ID를 넘기면 거래내역 조회까지 포함합니다.
//
// 테스트 API 흐름:
// 1. POST /api/v1/auth/login
// 2. GET  /api/v1/users/me
// 3. GET  /api/v1/accounts
// 4. GET  /api/v1/accounts/{accountId}/transactions (ACCOUNT_ID 또는 ACCOUNT_IDS가 있을 때)
export const options = {
  vus: Number(__ENV.VUS || 1),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    // 인증 플로우에서는 health보다 약간 여유 있게 p95 500ms를 기준으로 둡니다.
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
    checks: ['rate>0.99'],
  },
};

// 로그인 요청만 따로 측정해서 전체 여정 지연과 분리해서 볼 수 있게 합니다.
const loginTrend = new Trend('banking_login_duration');
// 여정 단위 실패율입니다. HTTP 개별 요청 실패율과 별도로 사용자 플로우 실패를 봅니다.
const journeyFailureRate = new Rate('banking_journey_failed');

const actors = buildAccountActors();

function login(actor) {
  const startedAt = Date.now();
  const token = requestLogin({ api: 'auth-login' }, actor ? { email: actor.email } : {});
  loginTrend.add(Date.now() - startedAt);

  return token;
}

export default function () {
  const actor = actors.length > 0 ? actors[(__VU + __ITER) % actors.length] : null;
  // k6의 각 iteration은 "로그인 -> 내 정보 조회 -> 계좌 목록 조회" 순서로 실행됩니다.
  const token = group('1. login', () => login(actor));
  const authHeaders = { headers: jsonHeaders(token) };

  const me = group('2. get current user', () => http.get(
    `${baseUrl}/api/v1/users/me`,
    {
      ...authHeaders,
      tags: { api: 'users-me' },
    },
  ));

  const accounts = group('3. get accounts', () => http.get(
    `${baseUrl}/api/v1/accounts`,
    {
      ...authHeaders,
      tags: { api: 'accounts-list' },
    },
  ));

  let checksPassed = check(me, {
    'me status is 200': (r) => r.status === 200,
  }) && check(accounts, {
    'accounts status is 200': (r) => r.status === 200,
  });

  // ACCOUNT_ID 또는 ACCOUNT_IDS를 넘긴 경우에만 거래내역 조회를 추가합니다.
  // 테스트 데이터가 없을 때도 기본 smoke가 가능하도록 선택 처리합니다.
  const accountId = actor ? pick(actor.accountIds) : null;
  if (accountId) {
    const transactions = group('4. get account transactions', () => http.get(
      `${baseUrl}/api/v1/accounts/${accountId}/transactions?page=0&sortDirection=DESC`,
      {
        ...authHeaders,
        tags: { api: 'account-transactions' },
      },
    ));

    checksPassed = check(transactions, {
      'transactions status is 200': (r) => r.status === 200,
    }) && checksPassed;
  }

  journeyFailureRate.add(checksPassed ? 0 : 1);
  // 실제 사용자가 연속 클릭하지 않는 상황을 흉내 내기 위한 대기 시간입니다.
  sleep(Number(__ENV.SLEEP_SECONDS || 1));
}
