import http from 'k6/http';
import { check } from 'k6';

export const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
export const email = __ENV.TEST_EMAIL;
export const password = __ENV.TEST_PASSWORD;
export const testEmails = (__ENV.TEST_EMAILS || __ENV.TEST_EMAIL || '')
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean);
export const accountIds = (__ENV.ACCOUNT_IDS || __ENV.ACCOUNT_ID || '')
  .split(',')
  .map((id) => id.trim())
  .filter(Boolean);
const accountsPerUser = Number(__ENV.ACCOUNTS_PER_USER || 2);

export function jsonHeaders(token) {
  const headers = {
    'Content-Type': 'application/json;charset=UTF-8',
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  return headers;
}

export function pickAccountId() {
  if (accountIds.length === 0) {
    return null;
  }

  return accountIds[Math.floor(Math.random() * accountIds.length)];
}

export function pick(values) {
  if (!values || values.length === 0) {
    return null;
  }

  return values[Math.floor(Math.random() * values.length)];
}

export function buildAccountActors() {
  return testEmails.map((testEmail, index) => ({
    email: testEmail,
    accountIds: accountIds.slice(index * accountsPerUser, (index + 1) * accountsPerUser),
  }));
}

export function login(tags = { api: 'auth-login' }, credentials = {}) {
  const loginEmail = credentials.email || email;
  const loginPassword = credentials.password || password;

  if (!loginEmail || !loginPassword) {
    throw new Error('TEST_EMAIL and TEST_PASSWORD are required.');
  }

  const res = http.post(
    `${baseUrl}/api/v1/auth/login`,
    JSON.stringify({ email: loginEmail, password: loginPassword }),
    {
      headers: jsonHeaders(),
      tags,
    },
  );

  const ok = check(res, {
    'login status is 200': (r) => r.status === 200,
    'login has accessToken': (r) => Boolean(r.json('accessToken')),
  });

  if (!ok) {
    throw new Error(`Login failed with status ${res.status}: ${res.body}`);
  }

  return res.json('accessToken');
}
