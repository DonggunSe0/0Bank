import { apiFetch } from '../api'
import type { SavingsProduct } from '../types'

export async function getDepositProducts(): Promise<SavingsProduct[]> {
  const products = await apiFetch<Omit<SavingsProduct, 'type' | 'minAmount'>[]>(
    '/api/v1/savings/deposit-products',
  )
  return products.map((product) => ({ ...product, type: 'DEPOSIT', minAmount: 0 }))
}

export async function getInstallmentProducts(): Promise<SavingsProduct[]> {
  const products = await apiFetch<Omit<SavingsProduct, 'type' | 'minAmount'>[]>(
    '/api/v1/savings/installment-products',
  )
  return products.map((product) => ({ ...product, type: 'INSTALLMENT', minAmount: 0 }))
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
}) {
  return apiFetch('/api/v1/savings/installments', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}
