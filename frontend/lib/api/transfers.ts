import { apiFetch } from '../api'
import type { TransferRequest, TransferResult } from '../types'

export async function transfer(
  data: TransferRequest,
  idempotencyKey: string,
): Promise<TransferResult> {
  const response = await apiFetch<{
    transferId: number
    status: string
    senderAccountId: number
    senderAccountNumber: string
    receiverAccountNumber: string
    amount: number
    senderBalanceAfter: number
    memo?: string
    transferredAt: string
  }>('/api/v1/transfers', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(data),
  })

  return {
    success: response.status === 'SUCCESS',
    transferId: response.transferId,
    status: response.status,
    senderAccountId: response.senderAccountId,
    senderAccountNumber: response.senderAccountNumber,
    receiverAccountNumber: response.receiverAccountNumber,
    amount: response.amount,
    senderBalanceAfter: response.senderBalanceAfter,
    memo: response.memo,
    createdAt: response.transferredAt,
  }
}
