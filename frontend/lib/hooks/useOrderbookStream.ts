'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import {
  closeOrderbookStream,
  getOrderbookStreamAuthHeaders,
  getOrderbookStreamUrl,
  parseOrderbookEvent,
  parseStreamCreatedEvent,
} from '@/lib/api/realtime'
import type { RealtimeOrderbookSnapshot } from '@/lib/types'

export type OrderbookStreamStatus = 'idle' | 'connecting' | 'connected' | 'error' | 'closed'

interface UseOrderbookStreamResult {
  streamId: string | null
  orderbook: RealtimeOrderbookSnapshot | null
  status: OrderbookStreamStatus
  error: string | null
}

export function useOrderbookStream(stockCode: string | null): UseOrderbookStreamResult {
  const [streamId, setStreamId] = useState<string | null>(null)
  const [orderbook, setOrderbook] = useState<RealtimeOrderbookSnapshot | null>(null)
  const [status, setStatus] = useState<OrderbookStreamStatus>('idle')
  const [error, setError] = useState<string | null>(null)

  const streamIdRef = useRef<string | null>(null)
  const abortControllerRef = useRef<AbortController | null>(null)

  const cleanupStream = useCallback(async () => {
    const currentStreamId = streamIdRef.current
    streamIdRef.current = null

    if (currentStreamId) {
      try {
        await closeOrderbookStream(currentStreamId)
      } catch {
        // 페이지 이탈 시 연결 해지 실패는 무시
      }
    }

    abortControllerRef.current?.abort()
    abortControllerRef.current = null
  }, [])

  useEffect(() => {
    if (!stockCode) {
      setStatus('idle')
      return
    }

    let active = true
    const abortController = new AbortController()
    abortControllerRef.current = abortController

    setStreamId(null)
    setOrderbook(null)
    setError(null)
    setStatus('connecting')
    streamIdRef.current = null

    void fetchEventSource(getOrderbookStreamUrl(stockCode), {
      signal: abortController.signal,
      headers: getOrderbookStreamAuthHeaders(),
      openWhenHidden: true,
      async onopen(response) {
        if (!response.ok) {
          throw new Error(`실시간 호가 연결 실패 (${response.status})`)
        }
        if (active) setStatus('connected')
      },
      onmessage(event) {
        if (!active) return

        if (event.event === 'stream-created') {
          const created = parseStreamCreatedEvent(event.data)
          streamIdRef.current = created.streamId
          setStreamId(created.streamId)
          return
        }

        if (event.event === 'orderbook-updated') {
          setOrderbook(parseOrderbookEvent(event.data))
        }
      },
      onclose() {
        if (active) setStatus('closed')
      },
      onerror(err) {
        if (abortController.signal.aborted) return
        if (active) {
          setError('실시간 호가 연결이 끊어졌습니다.')
          setStatus('error')
        }
        throw err
      },
    }).catch(() => {
      if (!abortController.signal.aborted && active) {
        setError('실시간 호가 연결에 실패했습니다.')
        setStatus('error')
      }
    })

    return () => {
      active = false
      void cleanupStream()
      setStreamId(null)
      setStatus('closed')
    }
  }, [stockCode, cleanupStream])

  return { streamId, orderbook, status, error }
}
