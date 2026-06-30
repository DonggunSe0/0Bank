import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { baseUrl, jsonHeaders, login } from './lib/banking.js';

// 계좌 이체 API의 동시성과 멱등성 동작을 확인하는 테스트입니다.
// 실제 송금 데이터가 생성되므로 반드시 테스트 전용 계좌와 작은 금액으로만 실행하세요.
const targetVus = Number(__ENV.TARGET_VUS || 2);
const testEmails = (__ENV.TEST_EMAILS || __ENV.TEST_EMAIL || '')
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean);
const senderAccountIds = (__ENV.SENDER_ACCOUNT_IDS || __ENV.SENDER_ACCOUNT_ID || '')
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean);
const receiverAccountNumbers = (__ENV.RECEIVER_ACCOUNT_NUMBERS || __ENV.RECEIVER_ACCOUNT_NUMBER || '')
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean);
const accountPassword = __ENV.ACCOUNT_PASSWORD;
const amount = Number(__ENV.TRANSFER_AMOUNT || 1);
const memo = __ENV.TRANSFER_MEMO || 'k6 transfer load test';
const reuseIdempotencyKey = (__ENV.REUSE_IDEMPOTENCY_KEY || 'false').toLowerCase() === 'true';
const fixedIdempotencyKey = __ENV.IDEMPOTENCY_KEY || `transfer-fixed-${Date.now()}`;

export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-vus',
      vus: 1,
      duration: '20s',
      gracefulStop: '10s',
    },
    sustained_load: {
      executor: 'ramping-vus',
      startTime: '20s',
      stages: [
        { duration: '1m', target: targetVus },
        { duration: '2m', target: targetVus },
        { duration: '1m', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    checks: ['rate>0.99'],
    transfer_journey_failed: ['rate<0.01'],
  },
};

const transferDuration = new Trend('transfer_duration');
const journeyFailureRate = new Rate('transfer_journey_failed');

export function setup() {
  const missing = [];
  if (testEmails.length === 0) missing.push('TEST_EMAIL or TEST_EMAILS');
  if (senderAccountIds.length === 0) missing.push('SENDER_ACCOUNT_ID or SENDER_ACCOUNT_IDS');
  if (receiverAccountNumbers.length === 0) missing.push('RECEIVER_ACCOUNT_NUMBER or RECEIVER_ACCOUNT_NUMBERS');
  if (!accountPassword) missing.push('ACCOUNT_PASSWORD');

  if (missing.length > 0) {
    throw new Error(`${missing.join(', ')} must be set for transfer-load.js.`);
  }

  return {
    actors: testEmails.map((testEmail, index) => ({
      token: login({ api: 'setup-auth-login' }, { email: testEmail }),
      senderAccountId: senderAccountIds[index % senderAccountIds.length],
    })),
    receiverAccountNumbers,
  };
}

function idempotencyKey() {
  if (reuseIdempotencyKey) {
    return fixedIdempotencyKey;
  }

  return `transfer-${__VU}-${__ITER}-${Date.now()}`;
}

export default function (data) {
  const actor = data.actors[(__VU + __ITER) % data.actors.length];
  const receiverAccountNumber = data.receiverAccountNumbers[(__VU + __ITER) % data.receiverAccountNumbers.length];
  const startedAt = Date.now();
  const transfer = group('1. create transfer', () => http.post(
    `${baseUrl}/api/v1/transfers`,
    JSON.stringify({
      senderAccountId: Number(actor.senderAccountId),
      receiverAccountNumber,
      accountPassword,
      amount,
      memo,
    }),
    {
      headers: {
        ...jsonHeaders(actor.token),
        'Idempotency-Key': idempotencyKey(),
      },
      tags: { api: 'transfer-create' },
    },
  ));
  transferDuration.add(Date.now() - startedAt);

  // 일반 부하 테스트는 매 요청마다 다른 멱등성 키를 사용합니다.
  // REUSE_IDEMPOTENCY_KEY=true일 때는 같은 키를 반복 사용해 중복 요청 처리 경로를 확인합니다.
  const checksPassed = check(transfer, {
    'transfer status is 200': (r) => r.status === 200,
  });

  journeyFailureRate.add(checksPassed ? 0 : 1);
  sleep(Number(__ENV.SLEEP_SECONDS || 1));
}
