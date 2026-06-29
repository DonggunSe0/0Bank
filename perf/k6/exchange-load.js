import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { baseUrl, jsonHeaders, login, pickAccountId } from './lib/banking.js';

// 환율 조회, 환전 견적 생성, 환전 주문 실행을 함께 검증하는 부하 테스트입니다.
// 환전 주문은 실제 계좌/외화 지갑 잔액을 변경할 수 있으므로 KRW_ACCOUNT_ID와 FX_WALLET_ID를 명시한 경우에만 실행합니다.
const targetVus = Number(__ENV.TARGET_VUS || 5);
const fromCurrencyCode = __ENV.EXCHANGE_FROM || 'KRW';
const toCurrencyCode = __ENV.EXCHANGE_TO || 'USD';
const fromAmount = Number(__ENV.EXCHANGE_AMOUNT || 1000);
const krwAccountId = __ENV.KRW_ACCOUNT_ID || pickAccountId();
const fxWalletId = __ENV.FX_WALLET_ID;
const runOrder = (__ENV.RUN_EXCHANGE_ORDER || 'false').toLowerCase() === 'true';

export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-vus',
      vus: 1,
      duration: '30s',
      gracefulStop: '10s',
    },
    sustained_load: {
      executor: 'ramping-vus',
      startTime: '30s',
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
    http_req_duration: ['p(95)<800'],
    checks: ['rate>0.99'],
    exchange_journey_failed: ['rate<0.01'],
  },
};

const quoteDuration = new Trend('exchange_quote_duration');
const orderDuration = new Trend('exchange_order_duration');
const journeyFailureRate = new Rate('exchange_journey_failed');

export function setup() {
  return { token: login({ api: 'setup-auth-login' }) };
}

export default function (data) {
  const authHeaders = { headers: jsonHeaders(data.token) };
  let checksPassed = true;

  const currencies = group('1. get currencies', () => http.get(
    `${baseUrl}/api/v1/exchanges/currencies`,
    { tags: { api: 'exchange-currencies' } },
  ));

  const rates = group('2. get rates', () => http.get(
    `${baseUrl}/api/v1/exchanges/rates`,
    { tags: { api: 'exchange-rates' } },
  ));

  const currencyRate = group('3. get currency rate', () => http.get(
    `${baseUrl}/api/v1/exchanges/currencies/${toCurrencyCode}`,
    { tags: { api: 'exchange-currency-rate' } },
  ));

  checksPassed = check(currencies, {
    'currencies status is 200': (r) => r.status === 200,
  }) && checksPassed;
  checksPassed = check(rates, {
    'rates status is 200': (r) => r.status === 200,
  }) && checksPassed;
  checksPassed = check(currencyRate, {
    'currency rate status is 200': (r) => r.status === 200,
  }) && checksPassed;

  const quoteStartedAt = Date.now();
  const quote = group('4. create exchange quote', () => http.post(
    `${baseUrl}/api/v1/exchanges/currencies/quotes`,
    JSON.stringify({ fromCurrencyCode, toCurrencyCode, fromAmount }),
    {
      ...authHeaders,
      tags: { api: 'exchange-quote' },
    },
  ));
  quoteDuration.add(Date.now() - quoteStartedAt);

  checksPassed = check(quote, {
    'quote status is 201': (r) => r.status === 201,
    'quote has exchangeQuoteId': (r) => Boolean(r.json('exchangeQuoteId')),
  }) && checksPassed;

  const exchangeQuoteId = quote.json('exchangeQuoteId');

  // 주문 실행은 멱등성 키와 실제 잔액 변경이 포함됩니다.
  // RUN_EXCHANGE_ORDER=true, KRW_ACCOUNT_ID, FX_WALLET_ID를 모두 넘긴 경우에만 실행합니다.
  if (runOrder && exchangeQuoteId && krwAccountId && fxWalletId) {
    const orderStartedAt = Date.now();
    const order = group('5. create exchange order', () => http.post(
      `${baseUrl}/api/v1/exchanges/currencies/orders`,
      JSON.stringify({
        exchangeQuoteId,
        krwAccountId: Number(krwAccountId),
        fxWalletId: Number(fxWalletId),
      }),
      {
        headers: {
          ...jsonHeaders(data.token),
          'Idempotency-Key': `exchange-${__VU}-${__ITER}-${Date.now()}`,
        },
        tags: { api: 'exchange-order' },
      },
    ));
    orderDuration.add(Date.now() - orderStartedAt);

    checksPassed = check(order, {
      'order status is 201': (r) => r.status === 201,
      'order has exchangeOrderId': (r) => Boolean(r.json('exchangeOrderId')),
    }) && checksPassed;
  }

  const orders = group('6. get exchange orders', () => http.get(
    `${baseUrl}/api/v1/exchanges/currencies/orders?page=0&size=10`,
    {
      ...authHeaders,
      tags: { api: 'exchange-orders-list' },
    },
  ));

  checksPassed = check(orders, {
    'orders status is 200': (r) => r.status === 200,
  }) && checksPassed;

  journeyFailureRate.add(checksPassed ? 0 : 1);
  sleep(Number(__ENV.SLEEP_SECONDS || 1));
}
