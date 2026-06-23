import { apiFetch } from '../api'
import type {
  InvestmentAccount,
  InvestmentAccountCloseResult,
  InvestmentAccountOpenVerification,
  InvestmentAccountUpdateResult,
} from '../types'

export async function getInvestmentAccounts(): Promise<InvestmentAccount[]> {
  return apiFetch<InvestmentAccount[]>('/api/v1/investment/accounts')
}

export async function getInvestmentAccount(accountId: string | number): Promise<InvestmentAccount> {
  return apiFetch<InvestmentAccount>(`/api/v1/investment/accounts/${accountId}`)
}

export async function issueInvestmentAccountOpenVerification(): Promise<InvestmentAccountOpenVerification> {
  return apiFetch<InvestmentAccountOpenVerification>('/api/v1/investment/accounts/open-verification', {
    method: 'POST',
  })
}

export async function createInvestmentAccount(data: {
  nickname?: string
  accountPassword: string
  verificationKey: string
  currencyCode: 'KRW'
}): Promise<InvestmentAccount> {
  return apiFetch<InvestmentAccount>('/api/v1/investment/accounts', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function updateInvestmentAccount(
  accountId: string | number,
  data: {
    pastPassword: string
    nickname?: string
    newPassword?: string
  },
): Promise<InvestmentAccountUpdateResult> {
  return apiFetch<InvestmentAccountUpdateResult>(`/api/v1/investment/accounts/${accountId}`, {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}

export async function closeInvestmentAccount(
  accountId: string | number,
  accountPassword: string,
): Promise<InvestmentAccountCloseResult> {
  return apiFetch<InvestmentAccountCloseResult>(`/api/v1/investment/accounts/${accountId}/close`, {
    method: 'POST',
    body: JSON.stringify({ accountPassword }),
  })
}
