import { apiFetch } from '../api'
import type {
  DepositDetail,
  DepositSummary,
  InstallmentDetail,
  InstallmentSummary,
  InterestPreview,
  SavingOperationResult,
  SavingsProduct,
  SavingsType,
} from '../types'

export async function getDepositProducts(): Promise<SavingsProduct[]> {
  return apiFetch<SavingsProduct[]>('/api/v1/savings/deposit-products')
}

export async function getInstallmentProducts(): Promise<SavingsProduct[]> {
  return apiFetch<SavingsProduct[]>('/api/v1/savings/installment-products')
}

export async function getDepositProduct(productId: string | number): Promise<SavingsProduct> {
  return apiFetch<SavingsProduct>(`/api/v1/savings/deposit-products/${productId}`)
}

export async function getInstallmentProduct(productId: string | number): Promise<SavingsProduct> {
  return apiFetch<SavingsProduct>(`/api/v1/savings/installment-products/${productId}`)
}

export async function createDeposit(data: {
  productId: number
  withdrawAccountId: number
  amount: number
  accountPassword: string
}) {
  return apiFetch('/api/v1/savings/deposits', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function createInstallment(data: {
  productId: number
  withdrawAccountId: number
  monthlyAmount: number
  targetAmount: number
  autoTransferYn: boolean
  accountPassword: string
}) {
  return apiFetch('/api/v1/savings/installments', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function getDeposits(status?: string): Promise<DepositSummary[]> {
  const params = status ? `?status=${status}` : ''
  return apiFetch<DepositSummary[]>(`/api/v1/savings/deposits${params}`)
}

export async function getDeposit(depositId: string | number): Promise<DepositDetail> {
  return apiFetch<DepositDetail>(`/api/v1/savings/deposits/${depositId}`)
}

export async function getInstallments(status?: string): Promise<InstallmentSummary[]> {
  const params = status ? `?status=${status}` : ''
  return apiFetch<InstallmentSummary[]>(`/api/v1/savings/installments${params}`)
}

export async function getInstallment(installmentId: string | number): Promise<InstallmentDetail> {
  return apiFetch<InstallmentDetail>(`/api/v1/savings/installments/${installmentId}`)
}

export async function getInterestPreview(
  savingId: string | number,
  savingType: SavingsType,
): Promise<InterestPreview> {
  return apiFetch<InterestPreview>(
    `/api/v1/savings/${savingId}/interest-preview?savingType=${savingType}`,
  )
}

export async function cancelSaving(
  savingId: string | number,
  savingType: SavingsType,
): Promise<SavingOperationResult> {
  return apiFetch<SavingOperationResult>(`/api/v1/savings/${savingId}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ savingType }),
  })
}

export async function matureSaving(
  savingId: string | number,
  savingType: SavingsType,
): Promise<SavingOperationResult> {
  return apiFetch<SavingOperationResult>(`/api/v1/savings/${savingId}/maturity`, {
    method: 'POST',
    body: JSON.stringify({ savingType }),
  })
}
