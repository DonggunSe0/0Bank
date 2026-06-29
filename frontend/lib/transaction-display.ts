import type { Transaction } from './types'

export function getTransactionDisplayName(transaction: Transaction) {
  return transaction.displayName ?? transaction.counterpartyName ?? '거래'
}

export function getTransactionCategoryLabel(transaction: Transaction) {
  if (
    transaction.type === 'SAVING_DEPOSIT_SIGNUP' ||
    transaction.type === 'SAVING_INSTALLMENT_SIGNUP' ||
    transaction.type === 'SAVING_CANCEL_REFUND' ||
    transaction.type === 'SAVING_MATURITY' ||
    transaction.type === 'INSTALLMENT_PAYMENT'
  ) {
    return '예적금'
  }

  if (transaction.type === 'EXCHANGE') return '환전'
  if (transaction.type === 'PAYMENT') return '결제'

  return transaction.direction === 'IN' ? '입금' : '출금'
}
