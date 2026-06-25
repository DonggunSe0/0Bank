'use client'

import { useEffect, useState } from 'react'
import { Building2, Calendar, Percent, TrendingUp, Wallet } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Skeleton } from '@/components/ui/skeleton'
import { getDepositProducts, getInstallmentProducts } from '@/lib/api/savings'
import { formatCurrency } from '@/lib/format'
import type { SavingsProduct } from '@/lib/types'

export default function SavingsPage() {
  const [deposits, setDeposits] = useState<SavingsProduct[]>([])
  const [installments, setInstallments] = useState<SavingsProduct[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    async function load() {
      try {
        const [d, i] = await Promise.all([getDepositProducts(), getInstallmentProducts()])
        setDeposits(d)
        setInstallments(i)
      } catch {
        setError('예적금 상품 정보를 불러오지 못했습니다.')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-foreground">예금 · 적금</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            내 목표에 맞는 상품을 찾아보세요.
          </p>
        </div>
        {error && <Badge variant="destructive" className="text-xs">오류</Badge>}
      </div>

      {error && (
        <Card className="border-destructive/30">
          <CardContent className="py-3 text-sm text-destructive">{error}</CardContent>
        </Card>
      )}

      <Tabs defaultValue="deposit">
        <TabsList className="w-full">
          <TabsTrigger value="deposit" className="flex-1">
            <Wallet className="size-4 mr-1.5" />
            정기예금
          </TabsTrigger>
          <TabsTrigger value="installment" className="flex-1">
            <TrendingUp className="size-4 mr-1.5" />
            적금
          </TabsTrigger>
        </TabsList>

        <TabsContent value="deposit" className="mt-4">
          <ProductList products={deposits} loading={loading} />
        </TabsContent>
        <TabsContent value="installment" className="mt-4">
          <ProductList products={installments} loading={loading} />
        </TabsContent>
      </Tabs>
    </div>
  )
}

function ProductList({
  products,
  loading,
}: {
  products: SavingsProduct[]
  loading: boolean
}) {
  if (loading) {
    return (
      <div className="flex flex-col gap-3">
        {[1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-36 w-full rounded-lg" />
        ))}
      </div>
    )
  }

  if (products.length === 0) {
    return (
      <p className="text-center text-sm text-muted-foreground py-10">
        상품 정보를 불러올 수 없습니다.
      </p>
    )
  }

  return (
    <div className="flex flex-col gap-3">
      {products.map((p) => (
        <ProductCard key={p.id} product={p} />
      ))}
    </div>
  )
}

function ProductCard({ product: p }: { product: SavingsProduct }) {
  const isHighRate = p.interestRate >= 5

  return (
    <Card className="border-border hover:border-primary/30 transition-colors">
      <CardContent className="pt-4 pb-4">
        <div className="flex items-start justify-between gap-3 mb-3">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-0.5">
              <h3 className="text-sm font-semibold text-foreground truncate">{p.name}</h3>
              {isHighRate && (
                <Badge className="text-xs h-5 shrink-0">인기</Badge>
              )}
            </div>
            <div className="flex items-center gap-1 text-xs text-muted-foreground">
              <Building2 className="size-3 shrink-0" />
              <span>{p.bankName}</span>
            </div>
          </div>
          <div className="text-right shrink-0">
            <p className="text-xl font-bold text-primary tabular-nums">
              {p.interestRate.toFixed(1)}
              <span className="text-sm font-normal">%</span>
            </p>
            <p className="text-xs text-muted-foreground">연이율</p>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-2 mt-3 pt-3 border-t border-border">
          <InfoChip icon={Calendar} label="기간" value={`${p.periodMonth}개월`} />
          <InfoChip
            icon={Wallet}
            label="최소금액"
            value={p.minAmount > 0 ? formatCurrency(p.minAmount) : '상세 확인'}
            compact
          />
          <InfoChip
            icon={Percent}
            label="유형"
            value={p.type === 'DEPOSIT' ? '예금' : '적금'}
          />
        </div>

        {p.terms && (
          <p className="text-xs text-muted-foreground mt-3 leading-relaxed border-t border-border pt-3">
            {p.terms}
          </p>
        )}
      </CardContent>
    </Card>
  )
}

function InfoChip({
  icon: Icon,
  label,
  value,
  compact = false,
}: {
  icon: React.ElementType
  label: string
  value: string
  compact?: boolean
}) {
  return (
    <div className="flex flex-col gap-0.5">
      <div className="flex items-center gap-1 text-muted-foreground">
        <Icon className="size-3" />
        <span className="text-xs">{label}</span>
      </div>
      <p className={`font-medium text-foreground ${compact ? 'text-xs' : 'text-sm'}`}>{value}</p>
    </div>
  )
}
