'use client'

import { useEffect, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import { ArrowDownLeft, Check, Send } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useAuth } from '@/contexts/AuthContext'
import { getAccounts } from '@/lib/api/accounts'
import { deposit, transfer } from '@/lib/api/transfers'
import { formatCurrency, formatDateTime } from '@/lib/format'
import { ApiRequestError } from '@/lib/api'
import type { Account, TransferResult } from '@/lib/types'

export default function TransferPage() {
  const sp = useSearchParams()
  const defaultTab = sp.get('mode') === 'deposit' ? 'deposit' : 'transfer'
  const { user } = useAuth()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [result, setResult] = useState<TransferResult | null>(null)

  useEffect(() => {
    if (!user) return
    getAccounts()
      .then(setAccounts)
      .catch(() => toast.error('계좌 정보를 불러오지 못했습니다.'))
  }, [user])

  if (result) {
    return <ReceiptView result={result} onClose={() => setResult(null)} />
  }

  return (
    <div className="flex flex-col gap-5 max-w-lg">
      <div>
        <h1 className="text-xl font-bold text-foreground">입금 / 송금</h1>
        <p className="text-sm text-muted-foreground mt-0.5">간편하게 입금하거나 송금하세요.</p>
      </div>

      <Tabs defaultValue={defaultTab}>
        <TabsList className="w-full">
          <TabsTrigger value="transfer" className="flex-1">
            <Send className="size-4 mr-1.5" />
            송금
          </TabsTrigger>
          <TabsTrigger value="deposit" className="flex-1">
            <ArrowDownLeft className="size-4 mr-1.5" />
            입금
          </TabsTrigger>
        </TabsList>

        <TabsContent value="transfer">
          <TransferForm accounts={accounts} onSuccess={setResult} />
        </TabsContent>

        <TabsContent value="deposit">
          <DepositForm accounts={accounts} onSuccess={setResult} />
        </TabsContent>
      </Tabs>
    </div>
  )
}

function TransferForm({
  accounts,
  onSuccess,
}: {
  accounts: Account[]
  onSuccess: (r: TransferResult) => void
}) {
  const [senderAccountId, setSenderAccountId] = useState('')
  const [receiverAccountNumber, setReceiverAccountNumber] = useState('')
  const [amount, setAmount] = useState('')
  const [memo, setMemo] = useState('')
  const [accountPassword, setAccountPassword] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(false)
  const [serverError, setServerError] = useState('')

  const activeAccounts = accounts.filter((a) => a.status === 'ACTIVE')

  function validate() {
    const e: Record<string, string> = {}
    if (!senderAccountId) e.senderAccountId = '출금 계좌를 선택해 주세요.'
    if (!receiverAccountNumber) e.receiverAccountNumber = '받는 계좌번호를 입력해 주세요.'
    else if (!/^[\d\-]+$/.test(receiverAccountNumber))
      e.receiverAccountNumber = '숫자와 하이픈(-)만 입력 가능합니다.'
    const amt = Number(amount)
    if (!amount) e.amount = '금액을 입력해 주세요.'
    else if (isNaN(amt) || amt <= 0) e.amount = '올바른 금액을 입력해 주세요.'
    if (!/^\d{6}$/.test(accountPassword)) {
      e.accountPassword = '계좌 비밀번호 숫자 6자리를 입력해 주세요.'
    }
    return e
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setServerError('')
    const errs = validate()
    setErrors(errs)
    if (Object.keys(errs).length > 0) return

    setLoading(true)
    try {
      const res = await transfer({
        senderAccountId,
        receiverAccountNumber,
        amount: Number(amount),
        accountPassword,
        memo: memo || undefined,
      })
      onSuccess({
        ...res,
        amount: Number(amount),
        receiverAccountNumber,
        memo: memo || undefined,
        createdAt: new Date().toISOString(),
      })
    } catch (err) {
      if (err instanceof ApiRequestError) setServerError(err.message)
      else setServerError('송금 중 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card className="border-border mt-3">
      <CardHeader className="pb-3">
        <CardTitle className="text-sm font-medium text-muted-foreground">송금 정보</CardTitle>
      </CardHeader>
      <CardContent>
        {serverError && (
          <Alert variant="destructive" className="mb-4">
            <AlertDescription>{serverError}</AlertDescription>
          </Alert>
        )}
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="sender">출금 계좌</Label>
            <Select
              value={senderAccountId}
              onValueChange={(value) => {
                if (value != null) setSenderAccountId(value)
              }}
            >
              <SelectTrigger id="sender" aria-invalid={!!errors.senderAccountId}>
                <SelectValue placeholder="계좌 선택" />
              </SelectTrigger>
              <SelectContent>
                {activeAccounts.map((acc) => (
                  <SelectItem key={acc.id} value={String(acc.id)}>
                    {acc.nickname} — {formatCurrency(acc.balance)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.senderAccountId && (
              <p className="text-xs text-destructive">{errors.senderAccountId}</p>
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="receiver">받는 계좌번호</Label>
            <Input
              id="receiver"
              placeholder="000-0000-000000"
              value={receiverAccountNumber}
              onChange={(e) =>
                setReceiverAccountNumber(e.target.value.replace(/[^0-9\-]/g, ''))
              }
              aria-invalid={!!errors.receiverAccountNumber}
            />
            {errors.receiverAccountNumber && (
              <p className="text-xs text-destructive">{errors.receiverAccountNumber}</p>
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="amount">금액 (원)</Label>
            <Input
              id="amount"
              type="number"
              inputMode="numeric"
              placeholder="0"
              min={1}
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              aria-invalid={!!errors.amount}
            />
            {amount && !isNaN(Number(amount)) && Number(amount) > 0 && (
              <p className="text-xs text-muted-foreground">{formatCurrency(Number(amount))}</p>
            )}
            {errors.amount && <p className="text-xs text-destructive">{errors.amount}</p>}
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="accountPassword">계좌 비밀번호</Label>
            <Input
              id="accountPassword"
              type="password"
              inputMode="numeric"
              maxLength={6}
              placeholder="숫자 6자리"
              value={accountPassword}
              onChange={(e) => setAccountPassword(e.target.value.replace(/\D/g, ''))}
              aria-invalid={!!errors.accountPassword}
            />
            {errors.accountPassword && (
              <p className="text-xs text-destructive">{errors.accountPassword}</p>
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="memo">메모 (선택)</Label>
            <Input
              id="memo"
              placeholder="메모 입력"
              value={memo}
              onChange={(e) => setMemo(e.target.value)}
              maxLength={50}
            />
          </div>

          <Button type="submit" className="w-full" disabled={loading}>
            {loading ? (
              <span className="flex items-center gap-2">
                <span className="size-4 border-2 border-primary-foreground border-t-transparent rounded-full animate-spin" />
                송금 중...
              </span>
            ) : (
              <>
                <Send data-icon="inline-start" />
                송금하기
              </>
            )}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

function DepositForm({
  accounts,
  onSuccess,
}: {
  accounts: Account[]
  onSuccess: (r: TransferResult) => void
}) {
  const [accountId, setAccountId] = useState('')
  const [amount, setAmount] = useState('')
  const [memo, setMemo] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(false)
  const [serverError, setServerError] = useState('')

  const activeAccounts = accounts.filter((a) => a.status === 'ACTIVE')

  function validate() {
    const e: Record<string, string> = {}
    if (!accountId) e.accountId = '입금 계좌를 선택해 주세요.'
    const amt = Number(amount)
    if (!amount) e.amount = '금액을 입력해 주세요.'
    else if (isNaN(amt) || amt <= 0) e.amount = '올바른 금액을 입력해 주세요.'
    return e
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setServerError('')
    const errs = validate()
    setErrors(errs)
    if (Object.keys(errs).length > 0) return

    setLoading(true)
    try {
      const res = await deposit({
        accountId,
        amount: Number(amount),
        memo: memo || undefined,
      })
      onSuccess({
        ...res,
        amount: Number(amount),
        memo: memo || undefined,
        createdAt: new Date().toISOString(),
      })
    } catch (err) {
      if (err instanceof ApiRequestError) setServerError(err.message)
      else setServerError('입금 중 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card className="border-border mt-3">
      <CardHeader className="pb-3">
        <CardTitle className="text-sm font-medium text-muted-foreground">입금 정보</CardTitle>
      </CardHeader>
      <CardContent>
        {serverError && (
          <Alert variant="destructive" className="mb-4">
            <AlertDescription>{serverError}</AlertDescription>
          </Alert>
        )}
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="deposit-account">입금 계좌</Label>
            <Select
              value={accountId}
              onValueChange={(value) => {
                if (value != null) setAccountId(value)
              }}
            >
              <SelectTrigger id="deposit-account" aria-invalid={!!errors.accountId}>
                <SelectValue placeholder="계좌 선택" />
              </SelectTrigger>
              <SelectContent>
                {activeAccounts.map((acc) => (
                  <SelectItem key={acc.id} value={String(acc.id)}>
                    {acc.nickname} — {formatCurrency(acc.balance)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.accountId && <p className="text-xs text-destructive">{errors.accountId}</p>}
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="deposit-amount">금액 (원)</Label>
            <Input
              id="deposit-amount"
              type="number"
              inputMode="numeric"
              placeholder="0"
              min={1}
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              aria-invalid={!!errors.amount}
            />
            {amount && !isNaN(Number(amount)) && Number(amount) > 0 && (
              <p className="text-xs text-muted-foreground">{formatCurrency(Number(amount))}</p>
            )}
            {errors.amount && <p className="text-xs text-destructive">{errors.amount}</p>}
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="deposit-memo">메모 (선택)</Label>
            <Input
              id="deposit-memo"
              placeholder="메모 입력"
              value={memo}
              onChange={(e) => setMemo(e.target.value)}
              maxLength={50}
            />
          </div>

          <Button type="submit" className="w-full" disabled={loading}>
            {loading ? (
              <span className="flex items-center gap-2">
                <span className="size-4 border-2 border-primary-foreground border-t-transparent rounded-full animate-spin" />
                처리 중...
              </span>
            ) : (
              <>
                <ArrowDownLeft data-icon="inline-start" />
                입금하기
              </>
            )}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

function ReceiptView({
  result,
  onClose,
}: {
  result: TransferResult
  onClose: () => void
}) {
  return (
    <div className="flex flex-col gap-5 max-w-sm mx-auto">
      <div className="text-center py-6">
        <div className="size-16 rounded-full bg-green-100 flex items-center justify-center mx-auto mb-4">
          <Check className="size-8 text-green-700" />
        </div>
        <h2 className="text-xl font-bold text-foreground">처리 완료</h2>
        <p className="text-sm text-muted-foreground mt-1">거래가 정상 처리되었습니다.</p>
      </div>

      <Card className="border-border">
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-medium text-muted-foreground">거래 영수증</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <div className="flex justify-between">
            <span className="text-sm text-muted-foreground">금액</span>
            <span className="text-sm font-bold text-foreground tabular-nums">
              {formatCurrency(result.amount)}
            </span>
          </div>
          {result.receiverAccountNumber && (
            <div className="flex justify-between">
              <span className="text-sm text-muted-foreground">받는 계좌</span>
              <span className="text-sm font-medium text-foreground">
                {result.receiverAccountNumber}
              </span>
            </div>
          )}
          {result.memo && (
            <div className="flex justify-between">
              <span className="text-sm text-muted-foreground">메모</span>
              <span className="text-sm font-medium text-foreground">{result.memo}</span>
            </div>
          )}
          {result.createdAt && (
            <div className="flex justify-between">
              <span className="text-sm text-muted-foreground">처리 시각</span>
              <span className="text-sm font-medium text-foreground">
                {formatDateTime(result.createdAt)}
              </span>
            </div>
          )}
        </CardContent>
      </Card>

      <Button onClick={onClose} className="w-full">
        확인
      </Button>
    </div>
  )
}
