import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { baseUrl, jsonHeaders, login, pickAccountId } from './lib/banking.js';

// 로그인 비용을 제외하고 인증 이후 조회 API만 반복 호출하는 부하 테스트입니다.
//
// setup 단계:
// 1. POST /api/v1/auth/login
//
// 본 테스트 API 흐름:
// 1. GET /api/v1/users/me
// 2. GET /api/v1/accounts
// 3. GET /api/v1/accounts/{accountId}/transactions (ACCOUNT_ID 또는 ACCOUNT_IDS가 있을 때)
export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-vus',
      vus: Number(__ENV.WARMUP_VUS || 2),
      duration: __ENV.WARMUP_DURATION || '30s',
      gracefulStop: '10s',
    },
    sustained_load: {
      executor: 'ramping-vus',
      startTime: __ENV.LOAD_START_TIME || '30s',
      stages: [
        { duration: __ENV.RAMP_UP || '1m', target: Number(__ENV.TARGET_VUS || 20) },
        { duration: __ENV.HOLD || '3m', target: Number(__ENV.TARGET_VUS || 20) },
        { duration: __ENV.RAMP_DOWN || '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<300'],
    checks: ['rate>0.99'],
    banking_readonly_journey_failed: ['rate<0.01'],
  },
};

const readonlyJourneyFailureRate = new Rate('banking_readonly_journey_failed');

export function setup() {
  // 전체 테스트 시작 전에 한 번만 로그인합니다.
  // 조회 API 병목을 보기 위한 스크립트라 iteration마다 로그인하지 않습니다.
  return {
    token: login({ api: 'setup-auth-login' }),
  };
}

export default function (data) {
  const authHeaders = { headers: jsonHeaders(data.token) };

  const me = group('1. get current user', () => http.get(
    `${baseUrl}/api/v1/users/me`,
    {
      ...authHeaders,
      tags: { api: 'users-me' },
    },
  ));

  const accounts = group('2. get accounts', () => http.get(
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

  const accountId = pickAccountId();
  if (accountId) {
    const transactions = group('3. get account transactions', () => http.get(
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

  readonlyJourneyFailureRate.add(checksPassed ? 0 : 1);
  sleep(Number(__ENV.SLEEP_SECONDS || 1));
}
