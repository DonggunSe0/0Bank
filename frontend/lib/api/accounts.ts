import { apiFetch } from '../api'
import type { Account } from '../types'

export async function getAccounts(): Promise<Account[]> {
  return apiFetch<Account[]>('/api/v1/accounts')
}

export async function getClosedAccounts(): Promise<Account[]> {
  return apiFetch<Account[]>('/api/v1/accounts/closed')
}

export async function getAccount(accountId: string | number): Promise<Account> {
  return apiFetch<Account>(`/api/v1/accounts/${accountId}`)
}

export async function createAccount(
  data: { nickname: string; accountType: string; accountPassword: string },
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

export async function closeAccount(
  accountId: string | number,
  accountPassword: string,
): Promise<Account> {
  return apiFetch<Account>(`/api/v1/accounts/${accountId}/close`, {
    method: 'POST',
    body: JSON.stringify({ accountPassword }),
  })
}

export async function setAccountPassword(
  accountId: string | number,
  accountPassword: string,
): Promise<{ accountId: number; passwordSet: boolean }> {
  return apiFetch<{ accountId: number; passwordSet: boolean }>(`/api/v1/accounts/${accountId}/password`, {
    method: 'POST',
    body: JSON.stringify({ accountPassword }),
  })
}

export async function changeAccountPassword(
  accountId: string | number,
  currentPassword: string,
  newPassword: string,
): Promise<{ accountId: number; passwordSet: boolean }> {
  return apiFetch<{ accountId: number; passwordSet: boolean }>(`/api/v1/accounts/${accountId}/password`, {
    method: 'PATCH',
    body: JSON.stringify({ currentPassword, newPassword }),
  })
}

// ──────────────────────────────────────────────
// External Account Connections (CODEF API)
// ──────────────────────────────────────────────

export interface ExternalConnectionRequest {
  organization: string
  businessType: string
  clientType: string
  loginType: string
  loginId: string
  password: string
  birthDate: string
}

export interface ExternalConnectionResponse {
  organization: string
  status: string
}

export interface ExternalCandidate {
  index: number
  organization: string
  accountNoMasked: string
  accountName: string
  accountAlias?: string
  assetType: string
  balance: number
  withdrawableAmount?: number
  openedAt?: string
  maturityAt?: string
  lastTransactionAt?: string
  linked: boolean
}

export interface ExternalAccount {
  id: number
  organization: string
  accountNoMasked: string
  accountName: string
  accountAlias?: string
  assetType: string
  balance: number
  withdrawableAmount?: number
  openedAt?: string
  maturityAt?: string
  lastTransactionAt?: string
  status: string
}

export interface ExternalAccountTransaction {
  id: number
  exAccountId: number
  transactedAt: string
  direction: 'IN' | 'OUT'
  amount: number
  balanceAfter?: number
  counterpartyName?: string
  memo?: string
  rawCategory?: string
}

export interface ExternalAccountDetail {
  account: ExternalAccount
  transactions: ExternalAccountTransaction[]
}

export interface ExternalCandidateListResponse {
  candidateToken: string
  expiresInSeconds: number
  accounts: ExternalCandidate[]
}

export interface ExternalLinkRequest {
  candidateToken: string
  selectedIndexes: number[]
}

export interface ExternalTransactionRefreshResult {
  requestedCount: number
  createdCount: number
  updatedCount: number
  detail: ExternalAccountDetail
}

/**
 * 1단계: 외부 금융기관과의 계정 연결 등록 (connectedId 발급 및 저장)
 */
export async function connectExternalBank(
  data: ExternalConnectionRequest,
): Promise<ExternalConnectionResponse> {
  return apiFetch<ExternalConnectionResponse>('/api/v1/external-accounts/connections', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

/**
 * 2단계: 실시간 외부 계좌 후보군 및 일회용 세션 토큰 조회
 */
export async function getExternalCandidates(
  organization: string,
): Promise<ExternalCandidateListResponse> {
  return apiFetch<ExternalCandidateListResponse>(
    `/api/v1/external-accounts/connections/${organization}/candidates`,
  )
}

/**
 * 연동 완료된 외부 계좌 목록 조회
 */
export async function getExternalAccounts(): Promise<ExternalAccount[]> {
  return apiFetch<ExternalAccount[]>('/api/v1/external-accounts/accounts')
}

/**
 * 연동 완료된 외부 계좌 상세 및 해당 계좌 거래내역 조회
 */
export async function getExternalAccount(
  accountId: string | number,
): Promise<ExternalAccountDetail> {
  return apiFetch<ExternalAccountDetail>(`/api/v1/external-accounts/accounts/${accountId}`)
}

/**
 * 3단계: 일회용 토큰과 인덱스를 전송하여 실제 계좌 영속화 연동
 */
export async function linkExternalAccounts(
  data: ExternalLinkRequest,
): Promise<ExternalAccount[]> {
  return apiFetch<ExternalAccount[]>('/api/v1/external-accounts/link', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

/**
 * 저장된 기관 연결 정보로 외부 계좌 스냅샷을 다시 조회하고 현재 계좌 정보를 갱신한다.
 */
export async function refreshExternalAccountInfo(
  account: ExternalAccount,
): Promise<ExternalAccount> {
  const candidates = await getExternalCandidates(account.organization)
  const matched = candidates.accounts.find(
    (candidate) => candidate.accountNoMasked === account.accountNoMasked,
  )

  if (!matched) {
    throw new Error('외부기관 조회 결과에서 현재 계좌를 찾을 수 없습니다.')
  }

  const refreshed = await linkExternalAccounts({
    candidateToken: candidates.candidateToken,
    selectedIndexes: [matched.index],
  })

  return refreshed.find((item) => item.id === account.id) ?? refreshed[0]
}

/**
 * 저장된 외부기관 연결 정보로 해당 외부 계좌 거래내역을 직접 조회하고 갱신한다.
 */
export async function refreshExternalAccountTransactions(
  accountId: string | number,
): Promise<ExternalTransactionRefreshResult> {
  return apiFetch<ExternalTransactionRefreshResult>(
    `/api/v1/external-accounts/accounts/${accountId}/transactions/refresh/provider`,
    { method: 'POST' },
  )
}
