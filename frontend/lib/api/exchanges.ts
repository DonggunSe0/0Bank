import { apiFetch } from '../api'
import type { PageResponse } from '../types'

export type ExchangeCurrencyCode =
  | 'KRW'
  | 'USD'
  | 'JPY'
  | 'EUR'
  | 'CNY'
  | 'GBP'
  | 'CAD'
  | 'AUD'
  | 'HKD'
  | 'SGD'

export type ExchangeDirection = 'KRW_TO_FOREIGN' | 'FOREIGN_TO_KRW'
export type ExchangeOrderStatus = 'REQUESTED' | 'COMPLETED' | 'FAILED' | 'CANCELED'
export type ExchangeCurrencyStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED'
export type FxWalletStatus = 'ACTIVE' | 'SUSPENDED' | 'CLOSED'

export interface ExchangeCurrency {
  currencyId: number
  currencyCode: ExchangeCurrencyCode
  currencyName: string
  countryName: string
  decimalPlaces: number
  status: ExchangeCurrencyStatus
}

export interface ExchangeRate {
  exchangeRateId: number
  currencyCode: ExchangeCurrencyCode
  basePrice: number
  currencyUnit: number
  rateAt: string
}

export interface ExchangeQuoteRequest {
  fromCurrencyCode: ExchangeCurrencyCode
  toCurrencyCode: ExchangeCurrencyCode
  fromAmount: number
}

export interface ExchangeQuote {
  exchangeQuoteId: number
  fromCurrencyCode: ExchangeCurrencyCode
  toCurrencyCode: ExchangeCurrencyCode
  fromAmount: number
  rate: number
  feeRate: number
  fee: number
  expectedToAmount: number
  expiredAt: string
  createdAt: string
}

export interface ExchangeOrderRequest {
  exchangeQuoteId: number
  krwAccountId: number
  fxWalletId: number
}

export interface ExchangeOrder {
  exchangeOrderId: number
  exchangeQuoteId: number
  direction: ExchangeDirection
  status: ExchangeOrderStatus
  krwAccountId: number | null
  fxWalletId: number | null
  fromAmount: number
  toAmount: number
  appliedRate: number
  feeRate: number
  fee: number
  createdAt: string
  completedAt: string | null
}

export interface FxWalletCreateRequest {
  currencyCode: ExchangeCurrencyCode
}

export interface FxWallet {
  walletId: number
  currencyCode: ExchangeCurrencyCode
  balance: number
  status: FxWalletStatus
  createdAt: string
  updatedAt: string
}

export function getExchangeCurrencies(): Promise<ExchangeCurrency[]> {
  return apiFetch<ExchangeCurrency[]>('/api/v1/exchanges/currencies')
}

export function getExchangeRates(): Promise<ExchangeRate[]> {
  return apiFetch<ExchangeRate[]>('/api/v1/exchanges/rates')
}

export function getExchangeRate(currencyCode: ExchangeCurrencyCode): Promise<ExchangeRate> {
  return apiFetch<ExchangeRate>(`/api/v1/exchanges/currencies/${currencyCode}`)
}

export function createExchangeQuote(data: ExchangeQuoteRequest): Promise<ExchangeQuote> {
  return apiFetch<ExchangeQuote>('/api/v1/exchanges/currencies/quotes', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function getExchangeOrders(page = 0, size = 10): Promise<PageResponse<ExchangeOrder>> {
  const params = new URLSearchParams()
  params.set('page', String(page))
  params.set('size', String(size))
  return apiFetch<PageResponse<ExchangeOrder>>(
    `/api/v1/exchanges/currencies/orders?${params.toString()}`,
  )
}

export function getExchangeOrder(exchangeOrderId: string | number): Promise<ExchangeOrder> {
  return apiFetch<ExchangeOrder>(`/api/v1/exchanges/currencies/orders/${exchangeOrderId}`)
}

export function createFxWallet(data: FxWalletCreateRequest): Promise<FxWallet> {
  return apiFetch<FxWallet>('/api/v1/fx-wallets', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function getFxWallets(): Promise<FxWallet[]> {
  return apiFetch<FxWallet[]>('/api/v1/fx-wallets')
}

export function getFxWallet(fxWalletId: string | number): Promise<FxWallet> {
  return apiFetch<FxWallet>(`/api/v1/fx-wallets/${fxWalletId}`)
}

export function closeFxWallet(fxWalletId: string | number): Promise<FxWallet> {
  return apiFetch<FxWallet>(`/api/v1/fx-wallets/${fxWalletId}/close`, {
    method: 'POST',
  })
}

export function createExchangeOrder(
  data: ExchangeOrderRequest,
  idempotencyKey: string,
): Promise<ExchangeOrder> {
  return apiFetch<ExchangeOrder>('/api/v1/exchanges/currencies/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(data),
  })
}
