export function formatCurrency(amount: number): string {
  return new Intl.NumberFormat('ko-KR', {
    style: 'currency',
    currency: 'KRW',
  }).format(amount)
}

export function formatNumber(amount: number): string {
  return new Intl.NumberFormat('ko-KR').format(amount)
}

export function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  }).format(date)
}

export function formatDateTime(dateStr: string): string {
  const date = new Date(dateStr)
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

export function formatAccountNumber(accountNumber: string): string {
  return accountNumber
}

export function maskAccountNumber(accountNumber: string): string {
  const parts = accountNumber.split('-')
  if (parts.length < 2) return accountNumber
  const last = parts[parts.length - 1]
  const masked = last.slice(-4).padStart(last.length, '*')
  return [...parts.slice(0, -1), masked].join('-')
}
