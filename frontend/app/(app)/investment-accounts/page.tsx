'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { BarChart3, Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { getInvestmentAccounts } from '@/lib/api/investments'
import { formatCurrency, maskAccountNumber } from '@/lib/format'
import type { InvestmentAccount } from '@/lib/types'

const statusLabel: Record<string, string> = {
  ACTIVE: '정상',
  CLOSED: '해지',
}

export default function InvestmentAccountsPage() {
  const [accounts, setAccounts] = useState<InvestmentAccount[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    getInvestmentAccounts()
      .then(setAccounts)
      .catch(() => setError('투자 계좌 정보를 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-foreground">투자 계좌</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            주식 거래에 사용할 투자 계좌를 관리합니다.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button size="sm" variant="outline" nativeButton={false} render={<Link href="/stocks" />}>
            주식 거래
          </Button>
          <Button size="sm" nativeButton={false} render={<Link href="/investment-accounts/new" />}>
            <Plus data-icon="inline-start" />
            개설
          </Button>
        </div>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {loading ? (
        <div className="flex flex-col gap-3">
          {[1, 2].map((i) => (
            <Skeleton key={i} className="h-24 w-full rounded-lg" />
          ))}
        </div>
      ) : accounts.length === 0 ? (
        <Card className="border-border">
          <CardContent className="py-12 text-center">
            <BarChart3 className="size-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-sm font-medium text-foreground mb-1">투자 계좌가 없습니다</p>
            <p className="text-xs text-muted-foreground mb-4">새 투자 계좌를 개설해 보세요.</p>
            <Button size="sm" nativeButton={false} render={<Link href="/investment-accounts/new" />}>
              <Plus data-icon="inline-start" />
              투자 계좌 개설
            </Button>
          </CardContent>
        </Card>
      ) : (
        <div className="flex flex-col gap-3">
          {accounts.map((account) => (
            <Link key={account.id} href={`/investment-accounts/${account.id}`}>
              <Card className="border-border hover:border-primary/40 transition-colors cursor-pointer">
                <CardContent className="py-4">
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="size-10 rounded-full bg-primary/10 flex items-center justify-center">
                        <BarChart3 className="size-5 text-primary" />
                      </div>
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <p className="text-sm font-semibold text-foreground truncate">
                            {account.nickname || '투자 계좌'}
                          </p>
                          <Badge
                            variant={account.status === 'ACTIVE' ? 'default' : 'secondary'}
                            className="text-xs h-5"
                          >
                            {statusLabel[account.status] ?? account.status}
                          </Badge>
                        </div>
                        <p className="text-xs text-muted-foreground mt-0.5">
                          {maskAccountNumber(account.accountNumber)} · {account.currencyCode}
                        </p>
                      </div>
                    </div>
                    <div className="text-right shrink-0">
                      <p className="text-base font-bold text-foreground tabular-nums">
                        {formatCurrency(account.cashBalance)}
                      </p>
                      <p className="text-xs text-muted-foreground">예수금</p>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
