import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { baseUrl, jsonHeaders, login } from './lib/banking.js';

// 주식 검색/목록/상세 조회와 보유종목 조회를 확인하는 읽기 전용 테스트입니다.
// 보유종목 조회는 인증과 투자 계좌 ID가 필요하므로 INVESTMENT_ACCOUNT_IDS를 넘긴 경우에만 실행합니다.
const targetVus = Number(__ENV.TARGET_VUS || 10);
const market = __ENV.STOCK_MARKET || 'KOSPI';
const keywordList = (__ENV.STOCK_KEYWORDS || '삼성,현대')
  .split(',')
  .map((keyword) => keyword.trim())
  .filter(Boolean);
const stockCodes = (__ENV.STOCK_CODES || '')
  .split(',')
  .map((code) => code.trim())
  .filter(Boolean);
const investmentAccountIds = (__ENV.INVESTMENT_ACCOUNT_IDS || __ENV.INVESTMENT_ACCOUNT_ID || '')
  .split(',')
  .map((id) => id.trim())
  .filter(Boolean);

export const options = {
  scenarios: {
    readonly_load: {
      executor: 'ramping-vus',
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
    http_req_duration: ['p(95)<500'],
    checks: ['rate>0.99'],
    investment_readonly_journey_failed: ['rate<0.01'],
  },
};

const journeyFailureRate = new Rate('investment_readonly_journey_failed');

export function setup() {
  if (investmentAccountIds.length === 0) {
    return { token: null };
  }

  return { token: login({ api: 'setup-auth-login' }) };
}

function pick(values) {
  return values[Math.floor(Math.random() * values.length)];
}

function firstStockCode(response) {
  try {
    const body = response.json();
    const rows = Array.isArray(body) ? body : body.content;
    return rows && rows[0] ? rows[0].stockCode : null;
  } catch (e) {
    return null;
  }
}

export default function (data) {
  let checksPassed = true;
  const keyword = pick(keywordList);

  const search = group('1. search stocks', () => http.get(
    `${baseUrl}/api/v1/investment/stocks/search?keyword=${encodeURIComponent(keyword)}&market=${market}`,
    { tags: { api: 'investment-stock-search' } },
  ));

  const stocks = group('2. get stock list', () => http.get(
    `${baseUrl}/api/v1/investment/stocks?market=${market}&status=ACTIVE&sort=STOCK_NAME&direction=ASC&page=0&size=20`,
    { tags: { api: 'investment-stock-list' } },
  ));

  checksPassed = check(search, {
    'stock search status is 200': (r) => r.status === 200,
  }) && checksPassed;
  checksPassed = check(stocks, {
    'stock list status is 200': (r) => r.status === 200,
  }) && checksPassed;

  // STOCK_CODES가 있으면 그 값을 우선 사용하고, 없으면 검색/목록 응답의 첫 종목을 상세 조회합니다.
  const stockCode = stockCodes.length > 0 ? pick(stockCodes) : firstStockCode(search) || firstStockCode(stocks);
  if (stockCode) {
    const detail = group('3. get stock detail', () => http.get(
      `${baseUrl}/api/v1/investment/stocks/${stockCode}`,
      { tags: { api: 'investment-stock-detail' } },
    ));

    checksPassed = check(detail, {
      'stock detail status is 200': (r) => r.status === 200,
    }) && checksPassed;
  }

  if (data.token && investmentAccountIds.length > 0) {
    const accountId = pick(investmentAccountIds);
    const holdings = group('4. get investment holdings', () => http.get(
      `${baseUrl}/api/v1/investment/accounts/${accountId}/holdings?page=0&size=20`,
      {
        headers: jsonHeaders(data.token),
        tags: { api: 'investment-holdings' },
      },
    ));

    checksPassed = check(holdings, {
      'holdings status is 200': (r) => r.status === 200,
    }) && checksPassed;
  }

  journeyFailureRate.add(checksPassed ? 0 : 1);
  sleep(Number(__ENV.SLEEP_SECONDS || 1));
}
