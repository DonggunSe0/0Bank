'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { ChevronRight, CreditCard, Plus } from 'lucide-react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useAuth } from '@/contexts/AuthContext'
import { getAccounts, getClosedAccounts, getExternalAccounts } from '@/lib/api/accounts'
import { formatCurrency, maskAccountNumber } from '@/lib/format'
import type { Account } from '@/lib/types'
import type { ExternalAccount } from '@/lib/api/accounts'

const accountTypeLabel: Record<string, string> = {
  DEPOSIT: '입출금',
  SAVING_DEPOSIT: '예금',
  SAVING_INSTALLMENT: '적금',
}

function getAccountTypeLabel(accountType?: string) {
  if (!accountType) return '계좌'
  return accountTypeLabel[accountType] ?? accountType
}

export default function AccountsPage() {
  const { user } = useAuth()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [closedAccounts, setClosedAccounts] = useState<Account[]>([])
  const [externalAccounts, setExternalAccounts] = useState<ExternalAccount[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!user) return
    async function load() {
      try {
        const [accs, closedAccs, externalAccs] = await Promise.all([
          getAccounts(),
          getClosedAccounts(),
          getExternalAccounts(),
        ])
        setAccounts(accs)
        setClosedAccounts(closedAccs)
        setExternalAccounts(externalAccs)
      } catch {
        setError('계좌 정보를 불러오지 못했습니다.')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [user])

  const activeTotalCount = accounts.length + externalAccounts.length
  const allTotalCount = activeTotalCount + closedAccounts.length

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-foreground">내 계좌</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            {loading ? '' : `총 ${allTotalCount}개 계좌`}
          </p>
        </div>
        <div className="flex gap-2">
          <Button size="sm" variant="outline" nativeButton={false} render={<Link href="/accounts/import" />} className="border-border hover:bg-muted text-foreground">
            <Plus data-icon="inline-start" />
            외부 계좌 연동
          </Button>
          <Button size="sm" nativeButton={false} render={<Link href="/accounts/new" />}>
            <Plus data-icon="inline-start" />
            계좌 만들기
          </Button>
        </div>
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
      ) : allTotalCount === 0 ? (
        <EmptyAccounts />
      ) : (
        <Tabs defaultValue="active">
          <TabsList className="w-full">
            <TabsTrigger value="active" className="flex-1">
              사용 중 계좌 {activeTotalCount > 0 ? `(${activeTotalCount})` : ''}
            </TabsTrigger>
            <TabsTrigger value="closed" className="flex-1">
              해지 계좌 {closedAccounts.length > 0 ? `(${closedAccounts.length})` : ''}
            </TabsTrigger>
          </TabsList>

          <TabsContent value="active" className="mt-4">
            {activeTotalCount === 0 ? (
              <p className="text-center text-sm text-muted-foreground py-10">
                사용 중인 계좌가 없습니다.
              </p>
            ) : (
              <div className="flex flex-col gap-3">
                {accounts.map((acc) => (
                  <AccountCard key={acc.id} account={acc} />
                ))}
                {externalAccounts.map((acc) => (
                  <ExternalAccountCard key={`external-${acc.id}`} account={acc} />
                ))}
              </div>
            )}
          </TabsContent>

          <TabsContent value="closed" className="mt-4">
            {closedAccounts.length === 0 ? (
              <p className="text-center text-sm text-muted-foreground py-10">
                해지된 계좌가 없습니다.
              </p>
            ) : (
              <div className="flex flex-col gap-3">
                {closedAccounts.map((acc) => (
                  <AccountCard key={acc.id} account={acc} />
                ))}
              </div>
            )}
          </TabsContent>
        </Tabs>
      )}
    </div>
  )
}

function EmptyAccounts() {
  return (
    <Card className="border-border">
      <CardContent className="py-12 text-center">
        <CreditCard className="size-10 text-muted-foreground mx-auto mb-3" />
        <p className="text-sm font-medium text-foreground mb-1">등록된 계좌가 없습니다</p>
        <p className="text-xs text-muted-foreground mb-4">
          새 계좌를 개설하거나 보유한 외부 계좌를 연동해 보세요.
        </p>
        <div className="flex justify-center gap-2">
          <Button size="sm" variant="outline" nativeButton={false} render={<Link href="/accounts/import" />} className="border-border hover:bg-muted text-foreground">
            <Plus data-icon="inline-start" />
            외부 계좌 연동
          </Button>
          <Button size="sm" nativeButton={false} render={<Link href="/accounts/new" />}>
            <Plus data-icon="inline-start" />
            계좌 만들기
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

function AccountCard({ account, disabled = false }: { account: Account; disabled?: boolean }) {
  const card = (
    <Card className={`border-border ${disabled ? '' : 'hover:border-primary/40 transition-colors cursor-pointer'}`}>
      <CardContent className="py-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="size-10 rounded-full bg-primary/10 flex items-center justify-center">
              <CreditCard className="size-5 text-primary" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <p className="text-sm font-semibold text-foreground">{account.nickname}</p>
                <Badge variant="default" className="text-xs h-5">
                  {getAccountTypeLabel(account.accountType)}
                </Badge>
              </div>
              <p className="text-xs text-muted-foreground mt-0.5">
                {maskAccountNumber(account.accountNumber)}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <p className="text-base font-bold text-foreground tabular-nums">
              {formatCurrency(account.balance)}
            </p>
            {!disabled && <ChevronRight className="size-4 text-muted-foreground" />}
          </div>
        </div>
      </CardContent>
    </Card>
  )

  if (disabled) return card
  return <Link href={`/accounts/${account.id}`}>{card}</Link>
}

function ExternalAccountCard({ account }: { account: ExternalAccount }) {
  const card = (
    <Card className="border-border hover:border-primary/40 transition-colors cursor-pointer">
      <CardContent className="py-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="size-10 rounded-full bg-muted flex items-center justify-center">
              <CreditCard className="size-5 text-muted-foreground" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <p className="text-sm font-semibold text-foreground">
                  {account.accountAlias || account.accountName}
                </p>
                <Badge variant="outline" className="text-xs h-5">
                  외부
                </Badge>
              </div>
              <p className="text-xs text-muted-foreground mt-0.5">
                {account.organization} {account.accountNoMasked}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <p className="text-base font-bold text-foreground tabular-nums">
              {formatCurrency(account.balance)}
            </p>
            <ChevronRight className="size-4 text-muted-foreground" />
          </div>
        </div>
      </CardContent>
    </Card>
  )

  return <Link href={`/accounts/external/${account.id}`}>{card}</Link>
}
