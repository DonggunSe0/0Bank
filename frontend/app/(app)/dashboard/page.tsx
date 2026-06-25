'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import {
  ArrowDownLeft,
  ArrowUpRight,
  BadgeCheck,
  BarChart3,
  CreditCard,
  PiggyBank,
  Plus,
  Send,
  ShieldAlert,
  ShieldCheck,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Separator } from '@/components/ui/separator'
import { useAuth } from '@/contexts/AuthContext'
import { getAccounts, getExternalAccounts } from '@/lib/api/accounts'
import { getTransactions } from '@/lib/api/transactions'
import { formatCurrency, formatDateTime } from '@/lib/format'
import type { Account, Transaction } from '@/lib/types'
import type { ExternalAccount } from '@/lib/api/accounts'

export default function DashboardPage() {
  const { user } = useAuth()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [externalAccounts, setExternalAccounts] = useState<ExternalAccount[]>([])
  const [recentTxns, setRecentTxns] = useState<Transaction[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!user) return
    async function load() {
      try {
        const [accs, externalAccs] = await Promise.all([
          getAccounts(),
          getExternalAccounts(),
        ])
        setAccounts(accs)
        setExternalAccounts(externalAccs)
        if (accs.length > 0) {
          const page = await getTransactions(accs[0].id, { page: 0, sortDirection: 'DESC' })
          setRecentTxns(page.content.slice(0, 4))
        }
      } catch {
        setError('대시보드 정보를 불러오지 못했습니다.')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [user])

  const totalBalance =
    accounts.reduce((sum, a) => sum + a.balance, 0) +
    externalAccounts.reduce((sum, a) => sum + a.balance, 0)
  const activeAccounts = accounts.filter((a) => a.status === 'ACTIVE')
  const activeExternalAccounts = externalAccounts.filter((a) => a.status === 'ACTIVE')
  const accountSummaryItems = [
    ...accounts.map((account) => ({
      id: `internal-${account.id}`,
      href: `/accounts/${account.id}`,
      name: account.nickname,
      number: account.accountNumber,
      balance: account.balance,
      status: account.status,
      statusLabel: account.status === 'ACTIVE' ? '정상' : account.status === 'SUSPENDED' ? '정지' : '해지',
      external: false,
    })),
    ...externalAccounts.map((account) => ({
      id: `external-${account.id}`,
      href: null,
      name: account.accountAlias || account.accountName,
      number: `${account.organization} ${account.accountNoMasked}`,
      balance: account.balance,
      status: account.status,
      statusLabel: account.status === 'ACTIVE' ? '정상' : account.status === 'CLOSED' ? '해지' : '확인 필요',
      external: true,
    })),
  ]

  const quickActions = [
    { href: '/accounts/new', label: '계좌 만들기', icon: Plus },
    { href: '/transfer', label: '송금', icon: Send },
    { href: '/transfer?mode=deposit', label: '입금', icon: ArrowDownLeft },
    { href: '/transactions', label: '거래내역', icon: CreditCard },
    { href: '/savings', label: '예적금', icon: PiggyBank },
    { href: '/investment-accounts', label: '투자계좌', icon: BarChart3 },
  ]

  return (
    <div className="flex flex-col gap-5">
      {/* Welcome */}
      <div>
        <h1 className="text-xl font-bold text-foreground">
          안녕하세요, {user?.name ?? '고객'}님
        </h1>
        <p className="text-sm text-muted-foreground mt-0.5">오늘도 스마트한 금융생활을 시작해 보세요.</p>
      </div>

      {/* Total Balance + Identity Status */}
      {error && (
        <Card className="border-destructive/30">
          <CardContent className="py-3 text-sm text-destructive">{error}</CardContent>
        </Card>
      )}

      {/* Total Balance + Identity Status */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {/* Total Balance */}
        <Card className="border-border">
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">총 자산</CardTitle>
          </CardHeader>
          <CardContent>
            {loading ? (
              <Skeleton className="h-8 w-40" />
            ) : (
              <p className="text-2xl font-bold text-foreground tabular-nums">
                {formatCurrency(totalBalance)}
              </p>
            )}
            <p className="text-xs text-muted-foreground mt-1">
              {loading ? '' : `활성 계좌 ${activeAccounts.length + activeExternalAccounts.length}개`}
            </p>
          </CardContent>
        </Card>

        {/* Identity Verification */}
        <Card className="border-border">
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">본인인증 상태</CardTitle>
          </CardHeader>
          <CardContent>
            {user?.identityVerified ? (
              <div className="flex items-center gap-2">
                <ShieldCheck className="size-6 text-[hsl(var(--success))] shrink-0" style={{ color: 'oklch(0.52 0.14 155)' }} />
                <div>
                  <p className="text-sm font-semibold text-foreground">인증 완료</p>
                  <p className="text-xs text-muted-foreground">모든 서비스 이용 가능</p>
                </div>
              </div>
            ) : (
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <ShieldAlert className="size-6 text-destructive shrink-0" />
                  <div>
                    <p className="text-sm font-semibold text-foreground">미인증</p>
                    <p className="text-xs text-muted-foreground">일부 서비스 제한</p>
                  </div>
                </div>
                <Button size="sm" variant="outline" nativeButton={false} render={<Link href="/identity" />}>
                  인증하기
                </Button>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Quick Actions */}
      <Card className="border-border">
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-medium text-muted-foreground">빠른 작업</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex gap-3 flex-wrap">
            {quickActions.map((action) => {
              const Icon = action.icon
              return (
                <Link
                  key={action.href}
                  href={action.href}
                  className="flex flex-col items-center gap-1.5 p-3 rounded-lg bg-muted hover:bg-accent transition-colors min-w-[64px]"
                >
                  <div className="size-10 rounded-full bg-primary/10 flex items-center justify-center">
                    <Icon className="size-5 text-primary" />
                  </div>
                  <span className="text-xs font-medium text-foreground text-center">{action.label}</span>
                </Link>
              )
            })}
          </div>
        </CardContent>
      </Card>

      {/* Account List Summary */}
      <Card className="border-border">
        <CardHeader className="pb-3 flex flex-row items-center justify-between">
          <CardTitle className="text-sm font-medium text-muted-foreground">내 계좌</CardTitle>
          <Button variant="ghost" size="sm" className="h-auto py-1 text-xs text-primary" nativeButton={false} render={<Link href="/accounts" />}>
            전체보기
          </Button>
        </CardHeader>
        <CardContent className="flex flex-col gap-0">
          {loading ? (
            <div className="flex flex-col gap-3">
              <Skeleton className="h-14 w-full" />
              <Skeleton className="h-14 w-full" />
            </div>
          ) : accountSummaryItems.length === 0 ? (
            <div className="py-6 text-center">
              <p className="text-sm text-muted-foreground mb-3">등록된 계좌가 없습니다.</p>
              <Button size="sm" nativeButton={false} render={<Link href="/accounts/new" />}>
                <Plus data-icon="inline-start" />
                계좌 만들기
              </Button>
            </div>
          ) : (
            accountSummaryItems.slice(0, 3).map((acc, i) => (
              <div key={acc.id}>
                {i > 0 && <Separator className="my-0" />}
                <div className="flex items-center justify-between py-3 rounded-md px-1 -mx-1 transition-colors">
                  <div className="flex items-center gap-3">
                    <div className="size-9 rounded-full bg-primary/10 flex items-center justify-center">
                      <CreditCard className="size-4 text-primary" />
                    </div>
                    <div>
                      {acc.href ? (
                        <Link href={acc.href} className="text-sm font-medium text-foreground hover:text-primary">
                          {acc.name}
                        </Link>
                      ) : (
                        <p className="text-sm font-medium text-foreground">{acc.name}</p>
                      )}
                      <p className="text-xs text-muted-foreground">{acc.number}</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-bold text-foreground tabular-nums">
                      {formatCurrency(acc.balance)}
                    </p>
                    <div className="flex justify-end gap-1">
                      {acc.external && (
                        <Badge variant="outline" className="text-xs h-5">
                          외부
                        </Badge>
                      )}
                      <Badge
                        variant={acc.status === 'ACTIVE' ? 'default' : 'secondary'}
                        className="text-xs h-5"
                      >
                        {acc.statusLabel}
                      </Badge>
                    </div>
                  </div>
                </div>
              </div>
            ))
          )}
        </CardContent>
      </Card>

      {/* Recent Transactions */}
      <Card className="border-border">
        <CardHeader className="pb-3 flex flex-row items-center justify-between">
          <CardTitle className="text-sm font-medium text-muted-foreground">최근 거래</CardTitle>
          <Button variant="ghost" size="sm" className="h-auto py-1 text-xs text-primary" nativeButton={false} render={<Link href="/transactions" />}>
            전체보기
          </Button>
        </CardHeader>
        <CardContent className="flex flex-col gap-0">
          {loading ? (
            <div className="flex flex-col gap-3">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : recentTxns.length === 0 ? (
            <p className="py-4 text-center text-sm text-muted-foreground">거래 내역이 없습니다.</p>
          ) : (
            recentTxns.map((txn, i) => (
              <div key={txn.id}>
                {i > 0 && <Separator className="my-0" />}
                <div className="flex items-center justify-between py-3">
                  <div className="flex items-center gap-3">
                    <div
                      className={`size-8 rounded-full flex items-center justify-center ${
                        txn.direction === 'IN'
                          ? 'bg-green-100 text-green-700'
                          : 'bg-red-100 text-red-600'
                      }`}
                    >
                      {txn.direction === 'IN' ? (
                        <ArrowDownLeft className="size-4" />
                      ) : (
                        <ArrowUpRight className="size-4" />
                      )}
                    </div>
                    <div>
                      <p className="text-sm font-medium text-foreground">
                        {txn.counterpartyName ?? '알 수 없음'}
                      </p>
                      <p className="text-xs text-muted-foreground">{formatDateTime(txn.createdAt)}</p>
                    </div>
                  </div>
                  <p
                    className={`text-sm font-bold tabular-nums ${
                      txn.direction === 'IN' ? 'text-green-700' : 'text-foreground'
                    }`}
                  >
                    {txn.direction === 'IN' ? '+' : '-'}
                    {formatCurrency(txn.amount)}
                  </p>
                </div>
              </div>
            ))
          )}
        </CardContent>
      </Card>
    </div>
  )
}
