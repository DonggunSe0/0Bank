import { apiFetch } from '../api'
import type { PageResponse, Transaction, TransactionFilter } from '../types'

export async function getTransactions(
  accountId: string | number,
  filter: TransactionFilter = {},
): Promise<PageResponse<Transaction>> {
  const params = new URLSearchParams()
  params.set('page', String(filter.page ?? 0))
  params.set('sortDirection', filter.sortDirection ?? 'DESC')
  if (filter.startDate) params.set('startDate', filter.startDate)
  if (filter.endDate) params.set('endDate', filter.endDate)
  if (filter.direction) params.set('direction', filter.direction)
  if (filter.minAmount != null) params.set('minAmount', String(filter.minAmount))
  if (filter.maxAmount != null) params.set('maxAmount', String(filter.maxAmount))
  if (filter.counterpartyName) params.set('counterpartyName', filter.counterpartyName)

  const page = await apiFetch<PageResponse<{
    transactionHistoryId: number
    type?: string
    direction: Transaction['direction']
    amount: number
    balanceAfter?: number
    counterpartyName?: string
    memo?: string
    transactedAt: string
  }>>(
    `/api/v1/accounts/${accountId}/transactions?${params.toString()}`,
  )

  return {
    ...page,
    content: page.content.map((transaction) => ({
      id: transaction.transactionHistoryId,
      accountId: Number(accountId),
      type: transaction.type,
      direction: transaction.direction,
      amount: transaction.amount,
      balanceAfter: transaction.balanceAfter,
      counterpartyName: transaction.counterpartyName,
      memo: transaction.memo,
      createdAt: transaction.transactedAt,
    })),
  }
}

export async function getTransaction(
  accountId: string | number,
  transactionId: string | number,
): Promise<Transaction> {
  const transaction = await apiFetch<{
    transactionHistoryId: number
    type?: string
    direction: Transaction['direction']
    amount: number
    balanceAfter?: number
    counterpartyName?: string
    memo?: string
    transactedAt: string
  }>(
    `/api/v1/accounts/${accountId}/transactions/${transactionId}`,
  )

  return {
    id: transaction.transactionHistoryId,
    accountId: Number(accountId),
    type: transaction.type,
    direction: transaction.direction,
    amount: transaction.amount,
    balanceAfter: transaction.balanceAfter,
    counterpartyName: transaction.counterpartyName,
    memo: transaction.memo,
    createdAt: transaction.transactedAt,
  }
}
