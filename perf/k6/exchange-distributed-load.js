import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { baseUrl, jsonHeaders, login } from './lib/banking.js';

// 여러 사용자/여러 계좌로 환전 견적과 실제 환전 주문을 분산 실행하는 테스트입니다.
// TEST_EMAILS, KRW_ACCOUNT_IDS, FX_WALLET_IDS는 같은 순서로 1:1 매핑됩니다.
const targetVus = Number(__ENV.TARGET_VUS || 5);
const fromCurrencyCode = __ENV.EXCHANGE_FROM || 'KRW';
const toCurrencyCode = __ENV.EXCHANGE_TO || 'USD';
const fromAmount = Number(__ENV.EXCHANGE_AMOUNT || 1000);
const runOrder = (__ENV.RUN_EXCHANGE_ORDER || 'false').toLowerCase() === 'true';

function parseList(value) {
  return (value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

const testEmails = parseList(__ENV.TEST_EMAILS || __ENV.TEST_EMAIL);
const krwAccountIds = parseList(__ENV.KRW_ACCOUNT_IDS || __ENV.KRW_ACCOUNT_ID);
const fxWalletIds = parseList(__ENV.FX_WALLET_IDS || __ENV.FX_WALLET_ID);

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
    http_req_duration: ['p(95)<1000'],
    checks: ['rate>0.99'],
    exchange_distributed_journey_failed: ['rate<0.01'],
  },
};

const quoteDuration = new Trend('exchange_distributed_quote_duration');
const orderDuration = new Trend('exchange_distributed_order_duration');
const journeyFailureRate = new Rate('exchange_distributed_journey_failed');

function jsonValue(response, path) {
  if (!response || response.error || !response.body) {
    return null;
  }

  try {
    return response.json(path);
  } catch (error) {
    return null;
  }
}

export function setup() {
  if (testEmails.length === 0) {
    throw new Error('TEST_EMAILS or TEST_EMAIL must be set for exchange-distributed-load.js.');
  }

  if (runOrder && (krwAccountIds.length < testEmails.length || fxWalletIds.length < testEmails.length)) {
    throw new Error('RUN_EXCHANGE_ORDER=true requires KRW_ACCOUNT_IDS and FX_WALLET_IDS for every TEST_EMAILS entry.');
  }

  return {
    actors: testEmails.map((email, index) => ({
      actorIndex: index + 1,
      email,
      token: login({ api: 'setup-auth-login' }, { email }),
      krwAccountId: krwAccountIds[index],
      fxWalletId: fxWalletIds[index],
    })),
  };
}

export default function (data) {
  const actor = data.actors[(__VU + __ITER) % data.actors.length];
  const authHeaders = { headers: jsonHeaders(actor.token) };
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
    'quote has exchangeQuoteId': (r) => Boolean(jsonValue(r, 'exchangeQuoteId')),
  }) && checksPassed;

  const exchangeQuoteId = jsonValue(quote, 'exchangeQuoteId');

  if (runOrder && exchangeQuoteId) {
    const orderStartedAt = Date.now();
    const order = group('5. create exchange order', () => http.post(
      `${baseUrl}/api/v1/exchanges/currencies/orders`,
      JSON.stringify({
        exchangeQuoteId,
        krwAccountId: Number(actor.krwAccountId),
        fxWalletId: Number(actor.fxWalletId),
      }),
      {
        headers: {
          ...jsonHeaders(actor.token),
          'Idempotency-Key': `exchange-distributed-${actor.actorIndex}-${__VU}-${__ITER}-${Date.now()}`,
        },
        tags: { api: 'exchange-order' },
      },
    ));
    orderDuration.add(Date.now() - orderStartedAt);

    checksPassed = check(order, {
      'order status is 201': (r) => r.status === 201,
      'order has exchangeOrderId': (r) => Boolean(jsonValue(r, 'exchangeOrderId')),
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
