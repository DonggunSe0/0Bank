'use client'

import { useEffect, useRef, useState } from 'react'
import { useParams, useRouter, useSearchParams } from 'next/navigation'
import { ArrowLeft, BarChart3 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { getStock } from '@/lib/api/stocks'
import { getInvestmentAccounts } from '@/lib/api/investments'
import { createMarketOrder } from '@/lib/api/trades'
import { ApiRequestError } from '@/lib/api'
import { createIdempotencyKey } from '@/lib/idempotency'
import { useOrderbookStream } from '@/lib/hooks/useOrderbookStream'
import { formatCurrency, formatDate, formatNumber, maskAccountNumber } from '@/lib/format'
import type { InvestmentAccount, InvestmentTradeResult, StockDetail } from '@/lib/types'

export default function StockDetailPage() {
  const { stockCode } = useParams<{ stockCode: string }>()
  const router = useRouter()
  const searchParams = useSearchParams()
  const defaultAccountId = searchParams.get('accountId')

  const [stock, setStock] = useState<StockDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const { streamId, orderbook, status: streamStatus, error: streamError } = useOrderbookStream(stockCode)

  useEffect(() => {
    getStock(stockCode)
      .then(setStock)
      .catch(() => setError('종목 정보를 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [stockCode])

  return (
    <div className="flex flex-col gap-5 max-w-2xl">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="sm" onClick={() => router.back()} className="-ml-2">
          <ArrowLeft data-icon="inline-start" />
          뒤로
        </Button>
      </div>

      {loading ? (
        <div className="flex flex-col gap-4">
          <Skeleton className="h-28 w-full rounded-lg" />
          <Skeleton className="h-64 w-full rounded-lg" />
        </div>
      ) : error ? (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : !stock ? (
        <p className="text-center text-muted-foreground py-12">종목을 찾을 수 없습니다.</p>
      ) : (
        <>
          <Card className="border-border bg-primary text-primary-foreground">
            <CardContent className="pt-5 pb-5">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-medium text-primary-foreground/70">{stock.stockCode}</p>
                  <p className="text-2xl font-bold mt-1">{stock.stockName}</p>
                  <p className="text-xs text-primary-foreground/60 mt-1">{stock.market}</p>
                </div>
                <Badge variant="secondary" className="text-xs shrink-0">
                  {stock.status === 'ACTIVE' ? '거래 가능' : stock.status}
                </Badge>
              </div>
            </CardContent>
          </Card>

          <Card className="border-border">
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-medium text-muted-foreground flex items-center gap-2">
                <BarChart3 className="size-4" />
                기업 정보
              </CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              <InfoRow label="상장일" value={stock.listedDate ? formatDate(stock.listedDate) : '-'} />
              <InfoRow
                label="시가총액"
                value={stock.marketCap != null ? formatNumber(stock.marketCap) : '-'}
              />
              <InfoRow
                label="매출액"
                value={stock.salesAmount != null ? formatNumber(stock.salesAmount) : '-'}
              />
              <InfoRow
                label="당기순이익"
                value={stock.netIncome != null ? formatNumber(stock.netIncome) : '-'}
              />
            </CardContent>
          </Card>

          <OrderbookPanel
            orderbook={orderbook}
            streamStatus={streamStatus}
            streamError={streamError}
          />

          <TradePanel
            stock={stock}
            streamId={streamId}
            orderbook={orderbook}
            defaultAccountId={defaultAccountId}
          />
        </>
      )}
    </div>
  )
}

function OrderbookPanel({
  orderbook,
  streamStatus,
  streamError,
}: {
  orderbook: ReturnType<typeof useOrderbookStream>['orderbook']
  streamStatus: ReturnType<typeof useOrderbookStream>['status']
  streamError: string | null
}) {
  const statusLabel: Record<string, string> = {
    connecting: '연결 중...',
    connected: '실시간',
    error: '연결 오류',
    closed: '연결 종료',
    idle: '대기',
  }

  return (
    <Card className="border-border">
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="text-sm font-medium text-muted-foreground">실시간 호가</CardTitle>
          <Badge
            variant={streamStatus === 'connected' ? 'default' : 'secondary'}
            className="text-xs"
          >
            {statusLabel[streamStatus] ?? streamStatus}
          </Badge>
        </div>
      </CardHeader>
      <CardContent>
        {streamError && (
          <Alert variant="destructive" className="mb-4">
            <AlertDescription>{streamError}</AlertDescription>
          </Alert>
        )}

        {!orderbook ? (
          <p className="text-sm text-muted-foreground text-center py-8">
            {streamStatus === 'connecting' ? '호가 데이터를 불러오는 중...' : '호가 데이터가 없습니다.'}
          </p>
        ) : (
          <div className="grid grid-cols-2 gap-4">
            <div>
              <p className="text-xs font-medium text-muted-foreground mb-2">매도 호가</p>
              <div className="flex flex-col gap-1">
                {[...orderbook.asks].reverse().map((level) => (
                  <div
                    key={`ask-${level.level}`}
                    className="flex justify-between text-sm tabular-nums text-red-600 dark:text-red-400"
                  >
                    <span>{formatCurrency(level.price)}</span>
                    <span className="text-muted-foreground">{formatNumber(level.quantity)}</span>
                  </div>
                ))}
              </div>
            </div>
            <div>
              <p className="text-xs font-medium text-muted-foreground mb-2">매수 호가</p>
              <div className="flex flex-col gap-1">
                {orderbook.bids.map((level) => (
                  <div
                    key={`bid-${level.level}`}
                    className="flex justify-between text-sm tabular-nums text-blue-600 dark:text-blue-400"
                  >
                    <span>{formatCurrency(level.price)}</span>
                    <span className="text-muted-foreground">{formatNumber(level.quantity)}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {orderbook && (
          <div className="flex justify-between mt-4 pt-3 border-t border-border text-xs text-muted-foreground">
            <span>총 매도 {formatNumber(orderbook.totalAskQuantity)}</span>
            <span>총 매수 {formatNumber(orderbook.totalBidQuantity)}</span>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function getInvestmentAccountLabel(account: InvestmentAccount) {
  return `${account.nickname || '투자 계좌'} · ${maskAccountNumber(account.accountNumber)}`
}

function TradePanel({
  stock,
  streamId,
  orderbook,
  defaultAccountId,
}: {
  stock: StockDetail
  streamId: string | null
  orderbook: ReturnType<typeof useOrderbookStream>['orderbook']
  defaultAccountId: string | null
}) {
  const [accounts, setAccounts] = useState<InvestmentAccount[]>([])
  const [accountId, setAccountId] = useState('')
  const [tradeType, setTradeType] = useState<'BUY' | 'SELL'>('BUY')
  const [quantity, setQuantity] = useState('')
  const [accountPassword, setAccountPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [tradeResult, setTradeResult] = useState<InvestmentTradeResult | null>(null)

  const idempotencyKeyRef = useRef<string | null>(null)

  useEffect(() => {
    getInvestmentAccounts()
      .then((response) => {
        const active = response.filter((account) => account.status === 'ACTIVE')
        setAccounts(active)
        if (defaultAccountId && active.some((account) => String(account.id) === defaultAccountId)) {
          setAccountId(defaultAccountId)
        } else if (active.length > 0) {
          setAccountId(String(active[0].id))
        }
      })
      .catch(() => toast.error('투자 계좌 정보를 불러오지 못했습니다.'))
  }, [defaultAccountId])

  const bestAsk = orderbook?.asks[0]?.price
  const bestBid = orderbook?.bids[0]?.price
  const expectedPrice = tradeType === 'BUY' ? bestAsk : bestBid

  async function handleSubmit() {
    setFormError('')

    if (!streamId) {
      setFormError('실시간 호가 연결이 준비되지 않았습니다. 잠시 후 다시 시도해 주세요.')
      return
    }

    const parsedAccountId = Number(accountId)
    const parsedQuantity = Number(quantity)

    if (!parsedAccountId) {
      setFormError('투자 계좌를 선택해 주세요.')
      return
    }

    if (!Number.isInteger(parsedQuantity) || parsedQuantity <= 0) {
      setFormError('주문 수량은 양의 정수여야 합니다.')
      return
    }

    if (!/^\d{6}$/.test(accountPassword)) {
      setFormError('계좌 비밀번호는 숫자 6자리여야 합니다.')
      return
    }

    if (!expectedPrice || expectedPrice <= 0) {
      setFormError('기대 가격을 확인할 수 없습니다. 호가 데이터를 기다려 주세요.')
      return
    }

    if (!idempotencyKeyRef.current) {
      idempotencyKeyRef.current = createIdempotencyKey('market-order')
    }

    setSubmitting(true)
    try {
      const result = await createMarketOrder(
        {
          accountId: parsedAccountId,
          stockId: stock.id,
          streamId,
          tradeType,
          quantity: parsedQuantity,
          accountPassword,
          expectedPrice,
        },
        idempotencyKeyRef.current,
      )
      setTradeResult(result)
      idempotencyKeyRef.current = null
      setAccountPassword('')
      setQuantity('')
      toast.success(`${tradeType === 'BUY' ? '매수' : '매도'} 체결 완료`)
    } catch (err) {
      setFormError(err instanceof ApiRequestError ? err.message : '주문 처리 중 오류가 발생했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  if (tradeResult) {
    return (
      <Card className="border-border">
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-medium text-muted-foreground">체결 결과</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <InfoRow label="종목" value={`${tradeResult.stockName} (${tradeResult.stockCode})`} />
          <InfoRow label="구분" value={tradeResult.tradeType === 'BUY' ? '매수' : '매도'} />
          <InfoRow label="체결 수량" value={`${formatNumber(tradeResult.quantity)}주`} />
          <InfoRow label="체결 가격" value={formatCurrency(tradeResult.executionPrice)} />
          <InfoRow label="총 체결 금액" value={formatCurrency(tradeResult.totalAmount)} />
          <InfoRow label="기대 가격" value={formatCurrency(tradeResult.requestedPrice)} />
          <InfoRow label="체결 시각" value={formatDate(tradeResult.executedAt)} />
          <Button variant="outline" className="mt-2" onClick={() => setTradeResult(null)}>
            새 주문하기
          </Button>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="border-border">
      <CardHeader className="pb-3">
        <CardTitle className="text-sm font-medium text-muted-foreground">시장가 주문</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {formError && (
          <Alert variant="destructive">
            <AlertDescription>{formError}</AlertDescription>
          </Alert>
        )}

        {accounts.length === 0 ? (
          <Alert>
            <AlertDescription>
              활성 투자 계좌가 없습니다. 투자 계좌를 먼저 개설해 주세요.
            </AlertDescription>
          </Alert>
        ) : (
          <>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="accountId">투자 계좌</Label>
              <Select
                value={accountId}
                onValueChange={(value) => value && setAccountId(value)}
              >
                <SelectTrigger id="accountId" className="w-full">
                  <SelectValue placeholder="계좌 선택">
                    {(value: string | null) => {
                      const selectedAccount = accounts.find((account) => String(account.id) === value)
                      return selectedAccount ? getInvestmentAccountLabel(selectedAccount) : '계좌 선택'
                    }}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  {accounts.map((account) => {
                    const label = getInvestmentAccountLabel(account)
                    return (
                      <SelectItem key={account.id} value={String(account.id)} label={label}>
                        {label} · {formatCurrency(account.cashBalance)}
                      </SelectItem>
                    )
                  })}
                </SelectContent>
              </Select>
            </div>

            <Tabs value={tradeType} onValueChange={(value) => setTradeType(value as 'BUY' | 'SELL')}>
              <TabsList className="w-full">
                <TabsTrigger value="BUY" className="flex-1 text-red-600 dark:text-red-400">
                  매수
                </TabsTrigger>
                <TabsTrigger value="SELL" className="flex-1 text-blue-600 dark:text-blue-400">
                  매도
                </TabsTrigger>
              </TabsList>
              <TabsContent value={tradeType} className="flex flex-col gap-4 mt-4">
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="quantity">주문 수량</Label>
                  <Input
                    id="quantity"
                    type="number"
                    min={1}
                    step={1}
                    value={quantity}
                    onChange={(event) => setQuantity(event.target.value)}
                    placeholder="수량 입력"
                  />
                </div>

                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="accountPassword">계좌 비밀번호</Label>
                  <Input
                    id="accountPassword"
                    type="password"
                    inputMode="numeric"
                    maxLength={6}
                    value={accountPassword}
                    onChange={(event) =>
                      setAccountPassword(event.target.value.replace(/\D/g, ''))
                    }
                    placeholder="숫자 6자리"
                  />
                </div>

                <div className="rounded-lg bg-muted/50 px-3 py-2 text-sm">
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">기대 가격</span>
                    <span className="font-medium tabular-nums">
                      {expectedPrice ? formatCurrency(expectedPrice) : '-'}
                    </span>
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">
                    {tradeType === 'BUY' ? '최우선 매도 호가' : '최우선 매수 호가'} 기준
                  </p>
                </div>

                <Button
                  onClick={handleSubmit}
                  disabled={submitting || !streamId}
                  className="w-full"
                >
                  {submitting ? '주문 처리 중...' : tradeType === 'BUY' ? '매수 주문' : '매도 주문'}
                </Button>
              </TabsContent>
            </Tabs>
          </>
        )}
      </CardContent>
    </Card>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between items-center gap-4">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className="text-sm font-medium text-foreground text-right">{value}</span>
    </div>
  )
}
