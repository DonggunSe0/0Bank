import { apiFetch } from '../api'
import type { Account } from '../types'

export async function getAccounts(): Promise<Account[]> {
  return apiFetch<Account[]>('/api/v1/accounts')
}

export async function getAccount(accountId: string | number): Promise<Account> {
  return apiFetch<Account>(`/api/v1/accounts/${accountId}`)
}

export async function createAccount(
  data: { nickname: string; accountType: string },
): Promise<Account> {
  return apiFetch<Account>('/api/v1/accounts', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function updateAccountNickname(
  accountId: string | number,
  nickname: string,
): Promise<Account> {
  return apiFetch<Account>(
    `/api/v1/accounts/${accountId}/nickname`,
    { method: 'PATCH', body: JSON.stringify({ nickname }) },
  )
}

export async function closeAccount(accountId: string | number): Promise<Account> {
  return apiFetch<Account>(`/api/v1/accounts/${accountId}/close`, {
    method: 'POST',
  })
}
