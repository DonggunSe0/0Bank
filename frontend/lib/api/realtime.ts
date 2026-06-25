import { apiFetch, API_BASE_URL } from '../api'
import { getAccessToken } from '../token'
import type { RealtimeOrderbookSnapshot, RealtimeOrderbookStreamCreated } from '../types'

export async function closeOrderbookStream(streamId: string): Promise<void> {
  return apiFetch<void>(`/api/v1/investment/realtime/orderbooks/streams/${streamId}`, {
    method: 'DELETE',
  })
}

export function getOrderbookStreamUrl(stockCode: string): string {
  return `${API_BASE_URL}/api/v1/investment/realtime/orderbooks/${stockCode}/stream`
}

export function getOrderbookStreamAuthHeaders(): Record<string, string> {
  const token = getAccessToken()
  const headers: Record<string, string> = { Accept: 'text/event-stream' }
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

export function parseStreamCreatedEvent(data: string): RealtimeOrderbookStreamCreated {
  return JSON.parse(data) as RealtimeOrderbookStreamCreated
}

export function parseOrderbookEvent(data: string): RealtimeOrderbookSnapshot {
  return JSON.parse(data) as RealtimeOrderbookSnapshot
}
