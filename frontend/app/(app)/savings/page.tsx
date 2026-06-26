'use client'

import { useEffect, useMemo, useState } from 'react'
import { Building2, Calendar, Percent, TrendingUp, Wallet } from 'lucide-react'
import { toast } from 'sonner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Progress } from '@/components/ui/progress'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { getAccounts } from '@/lib/api/accounts'
import { ApiRequestError } from '@/lib/api'
import {
  cancelSaving,
  createDeposit,
  createInstallment,
  getDepositProduct,
  getDepositProducts,
  getDeposits,
  getInstallments,
  getInstallmentProduct,
  getInstallmentProducts,
  getInterestPreview,
  matureSaving,
} from '@/lib/api/savings'
import { formatCurrency } from '@/lib/format'
import type {
  Account,
  DepositSummary,
  InstallmentSummary,
  InterestPreview,
  SavingOperationResult,
  SavingsProduct,
  SavingsType,
} from '@/lib/types'

const statusLabel: Record<string, string> = {
  ACTIVE: '가입중',
  MATURED: '만기완료',
  CANCELLED: '중도해지',
  PAYMENT_FAILED: '납입실패',
}

export default function SavingsPage() {
  const [deposits, setDeposits] = useState<SavingsProduct[]>([])
  const [installments, setInstallments] = useState<SavingsProduct[]>([])
  const [myDeposits, setMyDeposits] = useState<DepositSummary[]>([])
  const [myInstallments, setMyInstallments] = useState<InstallmentSummary[]>([])
  const [accounts, setAccounts] = useState<Account[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [joinProduct, setJoinProduct] = useState<SavingsProduct | null>(null)
  const [detailProduct, setDetailProduct] = useState<SavingsProduct | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [selectedAccountId, setSelectedAccountId] = useState('')
  const [amount, setAmount] = useState('')
  const [accountPassword, setAccountPassword] = useState('')
  const [autoTransfer, setAutoTransfer] = useState(true)
  const [submitting, setSubmitting] = useState(false)

  async function load() {
    try {
      const [d, i, md, mi, accs] = await Promise.all([
        getDepositProducts(),
        getInstallmentProducts(),
        getDeposits(),
        getInstallments(),
        getAccounts(),
      ])
      setDeposits(d)
      setInstallments(i)
      setMyDeposits(md)
      setMyInstallments(mi)
      setAccounts(accs.filter((account) => account.status === 'ACTIVE'))
    } catch {
      setError('예적금 정보를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const activeAccounts = useMemo(
    () => accounts.filter((account) => account.accountType === 'DEPOSIT' || !account.accountType),
    [accounts],
  )

  function openJoin(product: SavingsProduct) {
    setJoinProduct(product)
    setDetailProduct(null)
    setSelectedAccountId(activeAccounts[0]?.id ? String(activeAccounts[0].id) : '')
    setAmount('')
    setAccountPassword('')
    setAutoTransfer(true)
  }

  async function openDetail(product: SavingsProduct) {
    setDetailLoading(true)
    setDetailProduct(product)
    try {
      const detail =
        product.type === 'DEPOSIT'
          ? await getDepositProduct(product.id)
          : await getInstallmentProduct(product.id)
      setDetailProduct(detail)
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : '상품 상세 정보를 불러오지 못했습니다.')
      setDetailProduct(null)
    } finally {
      setDetailLoading(false)
    }
  }

  async function handleJoin() {
    if (!joinProduct) return
    if (!selectedAccountId) {
      toast.error('출금 계좌를 선택해 주세요.')
      return
    }
    const parsedAmount = Number(amount)
    if (!Number.isFinite(parsedAmount) || parsedAmount <= 0) {
      toast.error(joinProduct.type === 'DEPOSIT' ? '가입 금액을 입력해 주세요.' : '월 납입액을 입력해 주세요.')
      return
    }
    if (!/^\d{6}$/.test(accountPassword)) {
      toast.error('출금 계좌 비밀번호는 숫자 6자리여야 합니다.')
      return
    }
    if (parsedAmount < joinProduct.minAmount) {
      toast.error(`최소 가입 금액은 ${formatCurrency(joinProduct.minAmount)}입니다.`)
      return
    }
    if (joinProduct.type === 'DEPOSIT' && joinProduct.maxAmount && parsedAmount > joinProduct.maxAmount) {
      toast.error(`최대 가입 금액은 ${formatCurrency(joinProduct.maxAmount)}입니다.`)
      return
    }
    if (joinProduct.type === 'INSTALLMENT' && joinProduct.monthlyLimit && parsedAmount > joinProduct.monthlyLimit) {
      toast.error(`월 납입 한도는 ${formatCurrency(joinProduct.monthlyLimit)}입니다.`)
      return
    }

    setSubmitting(true)
    try {
      if (joinProduct.type === 'DEPOSIT') {
        await createDeposit({
          productId: joinProduct.id,
          withdrawAccountId: Number(selectedAccountId),
          amount: parsedAmount,
          accountPassword,
        })
        toast.success('예금 가입이 완료되었습니다.')
      } else {
        await createInstallment({
          productId: joinProduct.id,
          withdrawAccountId: Number(selectedAccountId),
          monthlyAmount: parsedAmount,
          targetAmount: parsedAmount * joinProduct.periodMonth,
          autoTransferYn: autoTransfer,
          accountPassword,
        })
        toast.success('적금 가입이 완료되었습니다.')
      }
      setJoinProduct(null)
      await load()
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : '가입 처리 중 오류가 발생했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-foreground">예금 · 적금</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            상품 가입부터 내 예적금 관리까지 한 번에 확인하세요.
          </p>
        </div>
        {error && <Badge variant="destructive" className="text-xs">오류</Badge>}
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription className="text-sm">{error}</AlertDescription>
        </Alert>
      )}

      <Tabs defaultValue="products">
        <TabsList className="w-full">
          <TabsTrigger value="products" className="flex-1">
            <Wallet className="size-4 mr-1.5" />
            상품 가입
          </TabsTrigger>
          <TabsTrigger value="my" className="flex-1">
            <TrendingUp className="size-4 mr-1.5" />
            내 예적금
          </TabsTrigger>
        </TabsList>

        <TabsContent value="products" className="mt-4">
          <Tabs defaultValue="deposit">
            <TabsList className="w-full">
              <TabsTrigger value="deposit" className="flex-1">정기예금</TabsTrigger>
              <TabsTrigger value="installment" className="flex-1">적금</TabsTrigger>
            </TabsList>
            <TabsContent value="deposit" className="mt-4">
              <ProductList products={deposits} loading={loading} onDetail={openDetail} onJoin={openJoin} />
            </TabsContent>
            <TabsContent value="installment" className="mt-4">
              <ProductList products={installments} loading={loading} onDetail={openDetail} onJoin={openJoin} />
            </TabsContent>
          </Tabs>
        </TabsContent>

        <TabsContent value="my" className="mt-4">
          <MySavingsList
            deposits={myDeposits}
            installments={myInstallments}
            loading={loading}
            onChanged={load}
          />
        </TabsContent>
      </Tabs>

      <Dialog open={!!detailProduct} onOpenChange={(open) => !open && setDetailProduct(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{detailProduct?.name ?? '상품 상세'}</DialogTitle>
          </DialogHeader>
          {detailProduct && (
            <div className="flex flex-col gap-4 py-2">
              <div className="rounded-lg bg-muted p-3">
                <p className="text-sm font-medium text-foreground">
                  {detailProduct.bankName}
                  {detailProduct.bankCode ? ` · ${detailProduct.bankCode}` : ''}
                </p>
                <p className="text-xs text-muted-foreground mt-1">
                  {detailProduct.type === 'DEPOSIT' ? '정기예금' : '적금'} · {detailProduct.periodMonth}개월 · 연 {detailProduct.interestRate.toFixed(1)}%
                </p>
              </div>
              <div className="grid grid-cols-2 gap-3 text-sm">
                <DetailItem label="최소 가입 금액" value={formatCurrency(detailProduct.minAmount)} />
                {detailProduct.type === 'DEPOSIT' ? (
                  <DetailItem label="최대 가입 금액" value={detailProduct.maxAmount ? formatCurrency(detailProduct.maxAmount) : '제한 없음'} />
                ) : (
                  <DetailItem label="월 납입 한도" value={detailProduct.monthlyLimit ? formatCurrency(detailProduct.monthlyLimit) : '제한 없음'} />
                )}
              </div>
              <div className="rounded-lg border border-border p-3">
                <p className="text-xs font-medium text-muted-foreground mb-1">상품 설명</p>
                <p className="text-sm leading-relaxed text-foreground">
                  {detailLoading ? '상세 정보를 불러오는 중입니다.' : detailProduct.terms ?? '가입 조건을 확인해 주세요.'}
                </p>
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setDetailProduct(null)}>닫기</Button>
            {detailProduct && (
              <Button onClick={() => openJoin(detailProduct)} disabled={detailLoading}>가입하기</Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!joinProduct} onOpenChange={(open) => !open && setJoinProduct(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{joinProduct?.name} 가입</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-4 py-2">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="withdrawAccount">출금 계좌</Label>
              <select
                id="withdrawAccount"
                value={selectedAccountId}
                onChange={(e) => setSelectedAccountId(e.target.value)}
                className="h-9 rounded-md border border-input bg-background px-3 text-sm"
              >
                {activeAccounts.length === 0 ? (
                  <option value="">사용 가능한 계좌가 없습니다</option>
                ) : (
                  activeAccounts.map((account) => (
                    <option key={account.id} value={account.id}>
                      {account.nickname} · {formatCurrency(account.balance)}
                    </option>
                  ))
                )}
              </select>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="amount">
                {joinProduct?.type === 'DEPOSIT' ? '가입 금액' : '월 납입액'}
              </Label>
              <Input
                id="amount"
                inputMode="numeric"
                placeholder="금액 입력"
                value={amount}
                onChange={(e) => setAmount(e.target.value.replace(/[^0-9]/g, ''))}
              />
              {joinProduct?.type === 'INSTALLMENT' && Number(amount) > 0 && (
                <p className="text-xs text-muted-foreground">
                  목표 금액은 {formatCurrency(Number(amount) * joinProduct.periodMonth)}로 계산됩니다.
                </p>
              )}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="savingAccountPassword">출금 계좌 비밀번호</Label>
              <Input
                id="savingAccountPassword"
                type="password"
                inputMode="numeric"
                placeholder="숫자 6자리"
                value={accountPassword}
                onChange={(e) => setAccountPassword(e.target.value.replace(/[^0-9]/g, '').slice(0, 6))}
                maxLength={6}
              />
              <p className="text-xs text-muted-foreground">예적금 전용 계좌가 아니라 선택한 출금 계좌의 비밀번호입니다.</p>
            </div>
            {joinProduct?.type === 'INSTALLMENT' && (
              <label className="flex items-center gap-2 text-sm text-foreground">
                <input
                  type="checkbox"
                  checked={autoTransfer}
                  onChange={(e) => setAutoTransfer(e.target.checked)}
                />
                자동이체 사용
              </label>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setJoinProduct(null)}>취소</Button>
            <Button onClick={handleJoin} disabled={submitting || activeAccounts.length === 0}>
              {submitting ? '처리 중...' : '가입하기'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function ProductList({
  products,
  loading,
  onDetail,
  onJoin,
}: {
  products: SavingsProduct[]
  loading: boolean
  onDetail: (product: SavingsProduct) => void
  onJoin: (product: SavingsProduct) => void
}) {
  if (loading) {
    return <div className="flex flex-col gap-3">{[1, 2, 3].map((i) => <Skeleton key={i} className="h-40 w-full rounded-lg" />)}</div>
  }
  if (products.length === 0) {
    return <p className="text-center text-sm text-muted-foreground py-10">상품 정보가 없습니다.</p>
  }
  return <div className="flex flex-col gap-3">{products.map((p) => <ProductCard key={p.id} product={p} onDetail={onDetail} onJoin={onJoin} />)}</div>
}

function ProductCard({
  product: p,
  onDetail,
  onJoin,
}: {
  product: SavingsProduct
  onDetail: (product: SavingsProduct) => void
  onJoin: (product: SavingsProduct) => void
}) {
  const isHighRate = p.interestRate >= 5
  const amountLabel = p.type === 'DEPOSIT' ? '최소금액' : '월 한도'
  const amountValue =
    p.type === 'DEPOSIT'
      ? formatCurrency(p.minAmount)
      : p.monthlyLimit
        ? formatCurrency(p.monthlyLimit)
        : '상세 확인'

  return (
    <Card className="border-border hover:border-primary/30 transition-colors">
      <CardContent className="pt-4 pb-4">
        <div className="flex items-start justify-between gap-3 mb-3">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-0.5">
              <h3 className="text-sm font-semibold text-foreground truncate">{p.name}</h3>
              {isHighRate && <Badge className="text-xs h-5 shrink-0">인기</Badge>}
            </div>
            <div className="flex items-center gap-1 text-xs text-muted-foreground">
              <Building2 className="size-3 shrink-0" />
              <span>{p.bankName}</span>
            </div>
          </div>
          <div className="text-right shrink-0">
            <p className="text-xl font-bold text-primary tabular-nums">{p.interestRate.toFixed(1)}<span className="text-sm font-normal">%</span></p>
            <p className="text-xs text-muted-foreground">연이율</p>
          </div>
        </div>
        <div className="grid grid-cols-3 gap-2 mt-3 pt-3 border-t border-border">
          <InfoChip icon={Calendar} label="기간" value={`${p.periodMonth}개월`} />
          <InfoChip icon={Wallet} label={amountLabel} value={amountValue} compact />
          <InfoChip icon={Percent} label="유형" value={p.type === 'DEPOSIT' ? '예금' : '적금'} />
        </div>
        {p.terms && <p className="text-xs text-muted-foreground mt-3 leading-relaxed border-t border-border pt-3">{p.terms}</p>}
        <div className="grid grid-cols-2 gap-2 mt-4">
          <Button size="sm" variant="outline" onClick={() => onDetail(p)}>상세보기</Button>
          <Button size="sm" onClick={() => onJoin(p)}>가입하기</Button>
        </div>
      </CardContent>
    </Card>
  )
}

function MySavingsList({ deposits, installments, loading, onChanged }: { deposits: DepositSummary[]; installments: InstallmentSummary[]; loading: boolean; onChanged: () => Promise<void> }) {
  if (loading) {
    return <div className="flex flex-col gap-3">{[1, 2].map((i) => <Skeleton key={i} className="h-32 w-full rounded-lg" />)}</div>
  }
  if (deposits.length === 0 && installments.length === 0) {
    return <p className="text-center text-sm text-muted-foreground py-10">가입한 예적금이 없습니다.</p>
  }
  return (
    <div className="flex flex-col gap-3">
      {deposits.map((deposit) => (
        <SavingCard key={`deposit-${deposit.depositId}`} id={deposit.depositId} type="DEPOSIT" title={deposit.productName} bankName={deposit.bankName} amount={deposit.principal} status={deposit.status} onChanged={onChanged} />
      ))}
      {installments.map((installment) => (
        <SavingCard key={`installment-${installment.installmentId}`} id={installment.installmentId} type="INSTALLMENT" title={installment.productName} bankName={installment.bankName} amount={installment.paidAmount} status={installment.status} progressRate={installment.progressRate} onChanged={onChanged} />
      ))}
    </div>
  )
}

function SavingCard({ id, type, title, bankName, amount, status, progressRate, onChanged }: { id: number; type: SavingsType; title: string; bankName: string; amount: number; status: string; progressRate?: number; onChanged: () => Promise<void> }) {
  const [preview, setPreview] = useState<InterestPreview | null>(null)
  const [result, setResult] = useState<SavingOperationResult | null>(null)
  const [loading, setLoading] = useState(false)

  async function run(action: 'preview' | 'cancel' | 'maturity') {
    setLoading(true)
    try {
      if (action === 'preview') {
        setPreview(await getInterestPreview(id, type))
        setResult(null)
        return
      }
      const nextResult = action === 'cancel' ? await cancelSaving(id, type) : await matureSaving(id, type)
      setResult(nextResult)
      setPreview(null)
      toast.success(action === 'cancel' ? '중도해지가 완료되었습니다.' : '만기 처리가 완료되었습니다.')
      await onChanged()
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : '처리 중 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card className="border-border">
      <CardHeader className="pb-2">
        <div className="flex items-start justify-between gap-3">
          <div>
            <CardTitle className="text-sm">{title}</CardTitle>
            <p className="text-xs text-muted-foreground mt-1">{bankName} · {type === 'DEPOSIT' ? '예금' : '적금'}</p>
          </div>
          <Badge variant={status === 'ACTIVE' ? 'default' : 'secondary'} className="text-xs">{statusLabel[status] ?? status}</Badge>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted-foreground">{type === 'DEPOSIT' ? '예치 원금' : '납입 금액'}</span>
          <span className="font-bold tabular-nums">{formatCurrency(amount)}</span>
        </div>
        {typeof progressRate === 'number' && (
          <div className="flex flex-col gap-1">
            <div className="flex justify-between text-xs text-muted-foreground"><span>목표 진행률</span><span>{progressRate}%</span></div>
            <Progress value={progressRate} />
          </div>
        )}
        {preview && (
          <div className="rounded-lg bg-muted p-3 text-sm">
            <div className="flex justify-between"><span>예상 이자</span><b>{formatCurrency(preview.expectedInterest)}</b></div>
            <div className="flex justify-between mt-1"><span>만기 예상 수령액</span><b>{formatCurrency(preview.expectedTotalAmount)}</b></div>
          </div>
        )}
        {result && (
          <div className="rounded-lg bg-muted p-3 text-sm">
            <div className="flex justify-between"><span>이자</span><b>{formatCurrency(result.interestAmount)}</b></div>
            <div className="flex justify-between mt-1"><span>처리 금액</span><b>{formatCurrency(result.refundAmount ?? result.payoutAmount ?? 0)}</b></div>
          </div>
        )}
        <div className="grid grid-cols-3 gap-2">
          <Button size="sm" variant="outline" onClick={() => run('preview')} disabled={loading}>이자조회</Button>
          <Button size="sm" variant="outline" onClick={() => run('maturity')} disabled={loading || status !== 'ACTIVE'}>만기</Button>
          <AlertDialog>
            <AlertDialogTrigger>
              <Button
                size="sm"
                variant="ghost"
                className="w-full text-destructive hover:text-destructive"
                disabled={loading || status !== 'ACTIVE'}
              >
                해지
              </Button>
            </AlertDialogTrigger>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>정말 해지하시겠습니까?</AlertDialogTitle>
                <AlertDialogDescription>
                  중도해지는 되돌릴 수 없습니다. 해지하면 예적금 전용 계좌가 닫히고 원금과 중도해지 이자가 입출금계좌로 반환됩니다.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>취소</AlertDialogCancel>
                <AlertDialogAction
                  className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                  onClick={() => run('cancel')}
                >
                  해지하기
                </AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        </div>
      </CardContent>
    </Card>
  )
}

function DetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 font-semibold text-foreground">{value}</p>
    </div>
  )
}

function InfoChip({ icon: Icon, label, value, compact = false }: { icon: React.ElementType; label: string; value: string; compact?: boolean }) {
  return (
    <div className="flex flex-col gap-0.5">
      <div className="flex items-center gap-1 text-muted-foreground"><Icon className="size-3" /><span className="text-xs">{label}</span></div>
      <p className={`font-medium text-foreground ${compact ? 'text-xs' : 'text-sm'}`}>{value}</p>
    </div>
  )
}
