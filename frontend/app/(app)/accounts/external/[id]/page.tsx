'use client'

import { useEffect, useRef, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import {
  ArrowDownLeft,
  ArrowLeft,
  ArrowUpRight,
  CreditCard,
  RefreshCw,
} from 'lucide-react'
import { toast } from 'sonner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/contexts/AuthContext'
import {
  getExternalAccount,
  refreshExternalAccountInfo,
  refreshExternalAccountTransactions,
} from '@/lib/api/accounts'
import type {
  ExternalAccount,
  ExternalAccountTransaction,
} from '@/lib/api/accounts'
import { ApiRequestError } from '@/lib/api'
import { formatCurrency, formatDate, formatDateTime } from '@/lib/format'

const statusLabel: Record<string, string> = {
  ACTIVE: '정상',
  CLOSED: '해지',
  UNKNOWN: '확인 필요',
}

const assetTypeLabel: Record<string, string> = {
  DEMAND: '입출금',
  SAVING: '예적금',
  LOAN: '대출',
  FUND: '펀드',
  FX: '외화',
  INSURANCE: '보험',
  UNKNOWN: '기타',
}

export default function ExternalAccountDetailPage() {
  const { id } = useParams<{ id: string }>()
  const router = useRouter()
  const { user } = useAuth()
  const [account, setAccount] = useState<ExternalAccount | null>(null)
  const [transactions, setTransactions] = useState<ExternalAccountTransaction[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [transactionsError, setTransactionsError] = useState('')
  const [infoRefreshing, setInfoRefreshing] = useState(false)
  const [transactionsRefreshing, setTransactionsRefreshing] = useState(false)
  const autoRefreshAttempted = useRef(false)

  async function loadDetail(options?: { silent?: boolean }) {
    if (!user) return
    if (!options?.silent) {
      setLoading(true)
    }
    setError('')
    try {
      const detail = await getExternalAccount(id)
      setAccount(detail.account)
      setTransactions(detail.transactions)
    } catch (err) {
      setAccount(null)
      setTransactions([])
      setError(err instanceof ApiRequestError ? err.message : '외부 계좌 정보를 불러오지 못했습니다.')
    } finally {
      if (!options?.silent) {
        setLoading(false)
      }
    }
  }

  useEffect(() => {
    void loadDetail()
  }, [id, user])

  useEffect(() => {
    autoRefreshAttempted.current = false
  }, [id])

  useEffect(() => {
    if (!user || !account || transactions.length > 0 || autoRefreshAttempted.current) return
    autoRefreshAttempted.current = true
    void refreshTransactions({ automatic: true })
  }, [account, transactions.length, user])

  async function handleRefreshInfo() {
    if (!account) return
    setInfoRefreshing(true)
    try {
      const refreshed = await refreshExternalAccountInfo(account)
      setAccount(refreshed)
      await loadDetail({ silent: true })
      toast.success('외부 계좌 정보가 업데이트되었습니다.')
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '외부 계좌 정보 업데이트에 실패했습니다.')
    } finally {
      setInfoRefreshing(false)
    }
  }

  async function handleRefreshTransactions() {
    await refreshTransactions()
  }

  async function refreshTransactions(options?: { automatic?: boolean }) {
    setTransactionsRefreshing(true)
    setTransactionsError('')
    try {
      const result = await refreshExternalAccountTransactions(id)
      setAccount(result.detail.account)
      setTransactions(result.detail.transactions)
      if (!options?.automatic) {
        toast.success(
          `거래내역 업데이트 완료: 신규 ${result.createdCount}건, 갱신 ${result.updatedCount}건`,
        )
      }
    } catch (err) {
      const message = err instanceof ApiRequestError ? err.message : '거래내역 업데이트에 실패했습니다.'
      setTransactionsError(message)
      if (!options?.automatic) {
        toast.error(message)
      }
    } finally {
      setTransactionsRefreshing(false)
    }
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="sm" onClick={() => router.back()} className="-ml-2">
          <ArrowLeft data-icon="inline-start" />
          뒤로
        </Button>
      </div>

      {loading ? (
        <div className="flex flex-col gap-4">
          <Skeleton className="h-36 w-full rounded-lg" />
          <Skeleton className="h-24 w-full rounded-lg" />
          <Skeleton className="h-52 w-full rounded-lg" />
        </div>
      ) : error ? (
        <Alert variant="destructive" className="border-destructive/30">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : !account ? (
        <p className="text-center text-muted-foreground py-12">외부 계좌를 찾을 수 없습니다.</p>
      ) : (
        <>
          <Card className="border-border bg-primary text-primary-foreground">
            <CardContent className="pt-5 pb-5">
              <div className="mb-4 flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-primary-foreground/70">외부 계좌 잔액</p>
                  <p className="mt-1 text-3xl font-bold tabular-nums">
                    {formatCurrency(account.balance)}
                  </p>
                </div>
                <Badge variant="secondary" className="shrink-0 text-xs">
                  {statusLabel[account.status] ?? account.status}
                </Badge>
              </div>
              <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                <div className="flex min-w-0 items-center gap-2">
                  <CreditCard className="size-4 shrink-0 text-primary-foreground/70" />
                  <div className="min-w-0">
                    <p className="text-xs text-primary-foreground/60">{account.organization}</p>
                    <p className="mt-0.5 truncate text-sm font-mono tracking-wider">{account.accountNoMasked}</p>
                  </div>
                </div>
                <div className="flex shrink-0 gap-2">
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={handleRefreshInfo}
                    disabled={infoRefreshing}
                    className="h-8 bg-primary-foreground/10 px-2.5 text-primary-foreground hover:bg-primary-foreground/20"
                    title="외부 계좌 정보 업데이트"
                  >
                    <RefreshCw data-icon="inline-start" className={infoRefreshing ? 'animate-spin' : ''} />
                    정보
                  </Button>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={handleRefreshTransactions}
                    disabled={transactionsRefreshing}
                    className="h-8 bg-primary-foreground/10 px-2.5 text-primary-foreground hover:bg-primary-foreground/20"
                    title="거래내역 업데이트"
                  >
                    <RefreshCw data-icon="inline-start" className={transactionsRefreshing ? 'animate-spin' : ''} />
                    거래내역
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>

          <Card className="border-border">
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-medium text-muted-foreground">외부 계좌 정보</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              <InfoRow label="계좌명" value={account.accountAlias || account.accountName} />
              <InfoRow label="계좌 유형" value={assetTypeLabel[account.assetType] ?? account.assetType} />
              <InfoRow
                label="출금 가능 금액"
                value={account.withdrawableAmount != null ? formatCurrency(account.withdrawableAmount) : '-'}
              />
              <InfoRow label="개설일" value={account.openedAt ? formatDate(account.openedAt) : '-'} />
              <InfoRow label="만기일" value={account.maturityAt ? formatDate(account.maturityAt) : '-'} />
              <InfoRow label="마지막 거래일" value={account.lastTransactionAt ? formatDate(account.lastTransactionAt) : '-'} />
            </CardContent>
          </Card>

          <section className="flex flex-col gap-3">
            <div>
              <h2 className="text-base font-semibold text-foreground">거래내역</h2>
              <p className="text-sm text-muted-foreground">
                {transactionsRefreshing && transactions.length === 0 ? '거래내역을 불러오는 중...' : `총 ${transactions.length}건`}
              </p>
            </div>

            {transactionsError && (
              <Alert variant="destructive" className="border-destructive/30">
                <AlertDescription>{transactionsError}</AlertDescription>
              </Alert>
            )}

            <Card className="border-border">
              <CardContent className="pt-4 flex flex-col gap-0">
                {transactionsRefreshing && transactions.length === 0 ? (
                  <div className="flex flex-col gap-3">
                    {[1, 2, 3].map((i) => (
                      <Skeleton key={i} className="h-14 w-full" />
                    ))}
                  </div>
                ) : transactions.length === 0 ? (
                  <div className="py-10 text-center">
                    <p className="text-sm text-muted-foreground">거래 내역이 없습니다.</p>
                  </div>
                ) : (
                  transactions.map((txn, i) => (
                    <div key={txn.id}>
                      {i > 0 && <Separator className="my-0" />}
                      <div className="flex items-center justify-between gap-3 py-3.5">
                        <div className="flex min-w-0 items-center gap-3">
                          <div
                            className={`size-9 rounded-full flex items-center justify-center shrink-0 ${
                              txn.direction === 'IN'
                                ? 'bg-green-50 text-green-700'
                                : 'bg-red-50 text-red-600'
                            }`}
                          >
                            {txn.direction === 'IN' ? (
                              <ArrowDownLeft className="size-4" />
                            ) : (
                              <ArrowUpRight className="size-4" />
                            )}
                          </div>
                          <div className="min-w-0">
                            <div className="flex items-center gap-1.5">
                              <p className="truncate text-sm font-medium text-foreground">
                                {txn.counterpartyName ?? txn.rawCategory ?? '알 수 없음'}
                              </p>
                              <Badge variant={txn.direction === 'IN' ? 'default' : 'secondary'} className="h-4 shrink-0 text-xs">
                                {txn.direction === 'IN' ? '입금' : '출금'}
                              </Badge>
                            </div>
                            <p className="text-xs text-muted-foreground">{formatDateTime(txn.transactedAt)}</p>
                            {txn.memo && (
                              <p className="truncate text-xs text-muted-foreground">{txn.memo}</p>
                            )}
                          </div>
                        </div>
                        <div className="shrink-0 text-right">
                          <p
                            className={`text-sm font-bold tabular-nums ${
                              txn.direction === 'IN' ? 'text-green-700' : 'text-foreground'
                            }`}
                          >
                            {txn.direction === 'IN' ? '+' : '-'}
                            {formatCurrency(txn.amount)}
                          </p>
                          {txn.balanceAfter != null && (
                            <p className="text-xs text-muted-foreground">
                              잔액 {formatCurrency(txn.balanceAfter)}
                            </p>
                          )}
                        </div>
                      </div>
                    </div>
                  ))
                )}
              </CardContent>
            </Card>
          </section>

        </>
      )}
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="shrink-0 text-sm text-muted-foreground">{label}</span>
      <span className="min-w-0 text-right text-sm font-medium text-foreground">{value}</span>
    </div>
  )
}
