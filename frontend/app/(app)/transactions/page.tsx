'use client'

import { useEffect, useState } from 'react'
import {
  ArrowDownLeft,
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  Filter,
  Search,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { useAuth } from '@/contexts/AuthContext'
import { getAccounts } from '@/lib/api/accounts'
import { getTransactions } from '@/lib/api/transactions'
import { formatCurrency, formatDateTime } from '@/lib/format'
import type { Account, PageResponse, Transaction, TransactionFilter } from '@/lib/types'

const emptyTransactionPage: PageResponse<Transaction> = {
  content: [],
  totalPages: 0,
  totalElements: 0,
  number: 0,
  size: 0,
}

export default function TransactionsPage() {
  const { user } = useAuth()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [selectedAccountId, setSelectedAccountId] = useState<string>('')
  const [page, setPage] = useState<PageResponse<Transaction>>(emptyTransactionPage)
  const [loading, setLoading] = useState(true)
  const [currentPage, setCurrentPage] = useState(0)
  const [showFilter, setShowFilter] = useState(false)
  const [filter, setFilter] = useState<TransactionFilter>({})
  const [error, setError] = useState('')

  useEffect(() => {
    if (!user) return
    getAccounts()
      .then((accs) => {
        setAccounts(accs)
        if (accs.length > 0) setSelectedAccountId(String(accs[0].id))
      })
      .catch(() => {
        setError('계좌 정보를 불러오지 못했습니다.')
        setLoading(false)
      })
  }, [user])

  useEffect(() => {
    if (!user || !selectedAccountId) return
    setLoading(true)
    setError('')
    getTransactions(selectedAccountId, { ...filter, page: currentPage })
      .then(setPage)
      .catch(() => {
        setPage(emptyTransactionPage)
        setError('거래내역을 불러오지 못했습니다.')
      })
      .finally(() => setLoading(false))
  }, [user, selectedAccountId, filter, currentPage])

  function handleFilterChange(key: keyof TransactionFilter, value: string) {
    setFilter((prev) => ({
      ...prev,
      [key]: value === 'all' || value === '' ? undefined : value,
    }))
    setCurrentPage(0)
  }

  const selectedAccount = accounts.find((a) => String(a.id) === selectedAccountId)

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-foreground">거래내역</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            {selectedAccount ? selectedAccount.nickname : ''}
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => setShowFilter((v) => !v)}
          className={showFilter ? 'bg-primary/10 border-primary text-primary' : ''}
        >
          <Filter data-icon="inline-start" />
          필터
        </Button>
      </div>

      {/* Account selector */}
      <div>
        <Label htmlFor="account-select" className="sr-only">계좌 선택</Label>
        <Select
          value={selectedAccountId}
          onValueChange={(value) => {
            if (value == null) return
            setSelectedAccountId(value)
            setCurrentPage(0)
          }}
        >
          <SelectTrigger id="account-select">
            <SelectValue placeholder="계좌 선택" />
          </SelectTrigger>
          <SelectContent>
            {accounts.map((acc) => (
              <SelectItem key={acc.id} value={String(acc.id)}>
                {acc.nickname} — {formatCurrency(acc.balance)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Filter Panel */}
      {showFilter && (
        <Card className="border-border">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium text-muted-foreground">필터</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-2 gap-3">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="direction-filter" className="text-xs">거래 유형</Label>
              <Select
                value={filter.direction ?? 'all'}
                onValueChange={(v) => handleFilterChange('direction', v as string)}
              >
                <SelectTrigger id="direction-filter">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">전체</SelectItem>
                  <SelectItem value="IN">입금</SelectItem>
                  <SelectItem value="OUT">출금</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="counterparty-filter" className="text-xs">거래처</Label>
              <div className="relative">
                <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 size-3.5 text-muted-foreground" />
                <Input
                  id="counterparty-filter"
                  className="pl-8 text-sm"
                  placeholder="거래처 검색"
                  value={filter.counterpartyName ?? ''}
                  onChange={(e) => handleFilterChange('counterpartyName', e.target.value)}
                />
              </div>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="start-date" className="text-xs">시작일</Label>
              <Input
                id="start-date"
                type="date"
                className="text-sm"
                value={filter.startDate ?? ''}
                onChange={(e) => handleFilterChange('startDate', e.target.value)}
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="end-date" className="text-xs">종료일</Label>
              <Input
                id="end-date"
                type="date"
                className="text-sm"
                value={filter.endDate ?? ''}
                onChange={(e) => handleFilterChange('endDate', e.target.value)}
              />
            </div>

            <div className="col-span-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => { setFilter({}); setCurrentPage(0) }}
              >
                필터 초기화
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Summary Badge */}
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <span>총 {page.totalElements}건</span>
        {error && <Badge variant="destructive" className="text-xs">오류</Badge>}
      </div>

      {error && (
        <Card className="border-destructive/30">
          <CardContent className="py-3 text-sm text-destructive">{error}</CardContent>
        </Card>
      )}

      {/* Transaction list */}
      <Card className="border-border">
        <CardContent className="pt-4 flex flex-col gap-0">
          {loading ? (
            <div className="flex flex-col gap-3">
              {[1, 2, 3, 4].map((i) => (
                <Skeleton key={i} className="h-14 w-full" />
              ))}
            </div>
          ) : page.content.length === 0 ? (
            <div className="py-10 text-center">
              <p className="text-sm text-muted-foreground">거래 내역이 없습니다.</p>
            </div>
          ) : (
            page.content.map((txn, i) => (
              <div key={txn.id}>
                {i > 0 && <Separator className="my-0" />}
                <div className="flex items-center justify-between py-3.5">
                  <div className="flex items-center gap-3">
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
                    <div>
                      <div className="flex items-center gap-1.5">
                        <p className="text-sm font-medium text-foreground">
                          {txn.counterpartyName ?? '알 수 없음'}
                        </p>
                        <Badge variant={txn.direction === 'IN' ? 'default' : 'secondary'} className="text-xs h-4">
                          {txn.direction === 'IN' ? '입금' : '출금'}
                        </Badge>
                      </div>
                      <p className="text-xs text-muted-foreground">{formatDateTime(txn.createdAt)}</p>
                      {txn.memo && (
                        <p className="text-xs text-muted-foreground">{txn.memo}</p>
                      )}
                    </div>
                  </div>
                  <div className="text-right">
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

      {/* Pagination */}
      {page.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
            disabled={currentPage === 0}
            aria-label="이전 페이지"
          >
            <ChevronLeft className="size-4" />
          </Button>
          <span className="text-sm text-muted-foreground">
            {currentPage + 1} / {page.totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setCurrentPage((p) => Math.min(page.totalPages - 1, p + 1))}
            disabled={currentPage >= page.totalPages - 1}
            aria-label="다음 페이지"
          >
            <ChevronRight className="size-4" />
          </Button>
        </div>
      )}
    </div>
  )
}
