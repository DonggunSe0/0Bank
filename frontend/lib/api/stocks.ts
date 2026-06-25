import { apiFetch } from '../api'
import type { PageResponse, StockDetail, StockSortType, StockSummary } from '../types'

export async function searchStocks(keyword: string): Promise<StockSummary[]> {
  const params = new URLSearchParams({ keyword, market: 'KOSPI' })
  return apiFetch<StockSummary[]>(`/api/v1/investment/stocks/search?${params.toString()}`)
}

export async function getStocks(options: {
  page?: number
  size?: number
  sort?: StockSortType
  direction?: 'ASC' | 'DESC'
} = {}): Promise<PageResponse<StockSummary>> {
  const params = new URLSearchParams({
    market: 'KOSPI',
    status: 'ACTIVE',
    sort: options.sort ?? 'MARKET_CAP',
    direction: options.direction ?? 'DESC',
    page: String(options.page ?? 0),
    size: String(options.size ?? 20),
  })
  return apiFetch<PageResponse<StockSummary>>(`/api/v1/investment/stocks?${params.toString()}`)
}

export async function getStock(stockCode: string): Promise<StockDetail> {
  return apiFetch<StockDetail>(`/api/v1/investment/stocks/${stockCode}`)
}

export async function getWatchlists(): Promise<StockSummary[]> {
  return apiFetch<StockSummary[]>('/api/v1/investment/watchlists')
}

export async function addWatchlist(stockId: number): Promise<StockSummary> {
  return apiFetch<StockSummary>(`/api/v1/investment/watchlists/${stockId}`, {
    method: 'POST',
  })
}

export async function removeWatchlist(stockId: number): Promise<void> {
  return apiFetch<void>(`/api/v1/investment/watchlists/${stockId}`, {
    method: 'DELETE',
  })
}
