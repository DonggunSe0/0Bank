import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { baseUrl, jsonHeaders } from './lib/banking.js';

// 청년정책 목록/검색/상세 조회와 RAG 추천 API를 확인하는 테스트입니다.
// 추천 API는 LLM/RAG 호출 비용과 지연이 클 수 있으므로 TARGET_VUS를 작게 시작하는 것을 권장합니다.
const targetVus = Number(__ENV.TARGET_VUS || 2);

const recommendCases = [
  {
    age: 25,
    region: '서울',
    category: '금융･복지･문화',
    keyword: '대출',
    query: '대학생인데 월세나 전세자금 대출을 지원받고 싶어요.',
  },
  {
    age: 29,
    region: '경기',
    category: '일자리',
    keyword: '취업',
    query: '취업 준비 중인데 면접 준비나 구직 활동 지원 정책을 알고 싶어요.',
  },
  {
    age: 24,
    region: '부산',
    category: '주거',
    keyword: '월세',
    query: '사회초년생인데 월세 부담을 줄일 수 있는 청년 주거 정책을 찾고 있어요.',
  },
];

const policyIds = (__ENV.POLICY_IDS || '')
    .split(',')
    .map((id) => id.trim())
    .filter(Boolean);

export const options = {
  scenarios: {
    policy_recommend: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: targetVus },
        { duration: '1m', target: targetVus },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<35000'],
    checks: ['rate>0.98'],
    policy_journey_failed: ['rate<0.02'],
  },
};

const recommendDuration = new Trend('policy_recommend_duration');
const journeyFailureRate = new Rate('policy_journey_failed');
const fallbackRate = new Rate('policy_recommend_fallback');

function pick(values) {
  return values[Math.floor(Math.random() * values.length)];
}

function firstPolicyId(response) {
  try {
    const body = response.json();
    const rows = Array.isArray(body) ? body : body.content;
    return rows && rows[0] ? rows[0].id || rows[0].policyId : null;
  } catch (e) {
    return null;
  }
}

function isFallbackResponse(response) {
  try {
    const body = response.body || '';
    return body.includes('AI API 연동 대기 중')
        || body.includes('Gemini API')
        || body.includes('필터링 기반 추천');
  } catch (e) {
    return false;
  }
}

export default function () {
  let checksPassed = true;
  const selectedCase = pick(recommendCases);

  const list = group('1. get policy list', () => http.get(
      `${baseUrl}/api/v1/youth-policies`,
      { tags: { api: 'policy-list' } },
  ));

  const search = group('2. search policies', () => http.get(
      `${baseUrl}/api/v1/youth-policies/search?age=${selectedCase.age}&region=${encodeURIComponent(selectedCase.region)}&category=${encodeURIComponent(selectedCase.category)}&keyword=${encodeURIComponent(selectedCase.keyword)}&page=0&size=10`,
      { tags: { api: 'policy-search' } },
  ));

  checksPassed = check(list, {
    'policy list status is 200': (r) => r.status === 200,
  }) && checksPassed;

  checksPassed = check(search, {
    'policy search status is 200': (r) => r.status === 200,
  }) && checksPassed;

  const policyId = policyIds.length > 0 ? pick(policyIds) : firstPolicyId(search) || firstPolicyId(list);
  if (policyId) {
    const detail = group('3. get policy detail', () => http.get(
        `${baseUrl}/api/v1/youth-policies/${policyId}`,
        { tags: { api: 'policy-detail' } },
    ));

    checksPassed = check(detail, {
      'policy detail status is 200': (r) => r.status === 200,
    }) && checksPassed;
  }

  const startedAt = Date.now();
  const recommend = group('4. recommend policies', () => http.post(
      `${baseUrl}/api/v1/youth-policies/recommend`,
      JSON.stringify({
        age: selectedCase.age,
        region: selectedCase.region,
        category: selectedCase.category,
        query: selectedCase.query,
      }),
      {
        headers: jsonHeaders(),
        tags: { api: 'policy-recommend' },
      },
  ));
  recommendDuration.add(Date.now() - startedAt);

  checksPassed = check(recommend, {
    'policy recommend status is 200': (r) => r.status === 200,
  }) && checksPassed;

  fallbackRate.add(isFallbackResponse(recommend));
  journeyFailureRate.add(checksPassed ? 0 : 1);

  sleep(Number(__ENV.SLEEP_SECONDS || 1));
}