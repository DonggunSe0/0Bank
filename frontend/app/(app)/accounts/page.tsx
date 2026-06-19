'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { ChevronRight, CreditCard, Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { useAuth } from '@/contexts/AuthContext'
import { getAccounts } from '@/lib/api/accounts'
import { formatCurrency, maskAccountNumber } from '@/lib/format'
import type { Account } from '@/lib/types'

const statusLabel: Record<string, string> = {
  ACTIVE: '정상',
  SUSPENDED: '정지',
  CLOSED: '해지',
}

export default function AccountsPage() {
  const { user } = useAuth()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!user) return
    async function load() {
      try {
        const accs = await getAccounts()
        setAccounts(accs)
      } catch (err) {
        setError('계좌 정보를 불러오지 못했습니다.')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [user])

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-foreground">내 계좌</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            {loading ? '' : `총 ${accounts.length}개 계좌`}
          </p>
        </div>
        <Button size="sm" nativeButton={false} render={<Link href="/accounts/new" />}>
          <Plus data-icon="inline-start" />
          계좌 만들기
        </Button>
      </div>

      {error && (
        <Alert>
          <AlertDescription className="text-sm">{error}</AlertDescription>
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
            <CreditCard className="size-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-sm font-medium text-foreground mb-1">등록된 계좌가 없습니다</p>
            <p className="text-xs text-muted-foreground mb-4">새 계좌를 개설해 보세요.</p>
            <Button size="sm" nativeButton={false} render={<Link href="/accounts/new" />}>
              <Plus data-icon="inline-start" />
              계좌 만들기
            </Button>
          </CardContent>
        </Card>
      ) : (
        <div className="flex flex-col gap-3">
          {accounts.map((acc) => (
            <Link key={acc.id} href={`/accounts/${acc.id}`}>
              <Card className="border-border hover:border-primary/40 transition-colors cursor-pointer">
                <CardContent className="py-4">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="size-10 rounded-full bg-primary/10 flex items-center justify-center">
                        <CreditCard className="size-5 text-primary" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <p className="text-sm font-semibold text-foreground">{acc.nickname}</p>
                          <Badge
                            variant={acc.status === 'ACTIVE' ? 'default' : 'secondary'}
                            className="text-xs h-5"
                          >
                            {statusLabel[acc.status] ?? acc.status}
                          </Badge>
                        </div>
                        <p className="text-xs text-muted-foreground mt-0.5">
                          {maskAccountNumber(acc.accountNumber)}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <p className="text-base font-bold text-foreground tabular-nums">
                        {formatCurrency(acc.balance)}
                      </p>
                      <ChevronRight className="size-4 text-muted-foreground" />
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
