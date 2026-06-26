import { apiFetch } from '../api'
import type { InvestmentTradeResult, MarketOrderRequest } from '../types'

export async function createMarketOrder(
  data: MarketOrderRequest,
  idempotencyKey: string,
): Promise<InvestmentTradeResult> {
  return apiFetch<InvestmentTradeResult>('/api/v1/investment/trades/market-orders', {
    method: 'POST',
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
    body: JSON.stringify(data),
  })
}
