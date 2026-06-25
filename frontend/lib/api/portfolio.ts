import { apiFetch } from '../api'
import type { InvestmentHolding, PageResponse } from '../types'

export async function getHoldings(
  accountId: string | number,
  page = 0,
  size = 20,
): Promise<PageResponse<InvestmentHolding>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  })
  return apiFetch<PageResponse<InvestmentHolding>>(
    `/api/v1/investment/accounts/${accountId}/holdings?${params.toString()}`,
  )
}
