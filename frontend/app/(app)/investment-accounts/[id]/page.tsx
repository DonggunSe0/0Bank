'use client'

import { useEffect, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import Link from 'next/link'
import { ArrowLeft, BarChart3, Edit2, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
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
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import {
  closeInvestmentAccount,
  getInvestmentAccount,
  updateInvestmentAccount,
} from '@/lib/api/investments'
import { getHoldings } from '@/lib/api/portfolio'
import { ApiRequestError } from '@/lib/api'
import { formatCurrency, formatDate, formatNumber } from '@/lib/format'
import type { InvestmentAccount, InvestmentHolding } from '@/lib/types'

const statusLabel: Record<string, string> = {
  ACTIVE: '정상',
  CLOSED: '해지',
}

export default function InvestmentAccountDetailPage() {
  const { id } = useParams<{ id: string }>()
  const router = useRouter()
  const [account, setAccount] = useState<InvestmentAccount | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [editOpen, setEditOpen] = useState(false)
  const [pastPassword, setPastPassword] = useState('')
  const [editNickname, setEditNickname] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [editError, setEditError] = useState('')
  const [editLoading, setEditLoading] = useState(false)

  const [closePassword, setClosePassword] = useState('')
  const [closeLoading, setCloseLoading] = useState(false)

  const [holdings, setHoldings] = useState<InvestmentHolding[]>([])
  const [holdingsLoading, setHoldingsLoading] = useState(true)
  const [holdingsError, setHoldingsError] = useState('')

  useEffect(() => {
    getInvestmentAccount(id)
      .then((response) => {
        setAccount(response)
        setEditNickname(response.nickname ?? '')
      })
      .catch(() => setError('투자 계좌 정보를 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [id])

  useEffect(() => {
    getHoldings(id)
      .then((response) => setHoldings(response.content))
      .catch(() => setHoldingsError('보유 종목을 불러오지 못했습니다.'))
      .finally(() => setHoldingsLoading(false))
  }, [id])

  async function handleUpdate() {
    if (!account) return
    setEditError('')

    if (!/^\d{6}$/.test(pastPassword)) {
      setEditError('현재 비밀번호는 숫자 6자리여야 합니다.')
      return
    }

    const trimmedNickname = editNickname.trim()
    const trimmedNewPassword = newPassword.trim()
    const shouldUpdateNickname = trimmedNickname && trimmedNickname !== (account.nickname ?? '')
    const shouldUpdatePassword = trimmedNewPassword.length > 0

    if (!shouldUpdateNickname && !shouldUpdatePassword) {
      setEditError('수정할 별칭 또는 새 비밀번호를 입력해 주세요.')
      return
    }

    if (shouldUpdatePassword && !/^\d{6}$/.test(trimmedNewPassword)) {
      setEditError('새 비밀번호는 숫자 6자리여야 합니다.')
      return
    }

    setEditLoading(true)
    try {
      const response = await updateInvestmentAccount(account.id, {
        pastPassword,
        nickname: shouldUpdateNickname ? trimmedNickname : undefined,
        newPassword: shouldUpdatePassword ? trimmedNewPassword : undefined,
      })
      setAccount((prev) =>
        prev
          ? {
              ...prev,
              nickname: response.nickname,
              updatedAt: response.updatedAt,
            }
          : prev,
      )
      setEditOpen(false)
      setPastPassword('')
      setNewPassword('')
      toast.success('투자 계좌 정보가 수정되었습니다.')
    } catch (err) {
      setEditError(err instanceof ApiRequestError ? err.message : '수정 중 오류가 발생했습니다.')
    } finally {
      setEditLoading(false)
    }
  }

  async function handleClose() {
    if (!account) return
    if (!/^\d{6}$/.test(closePassword)) {
      toast.error('계좌 비밀번호는 숫자 6자리여야 합니다.')
      return
    }

    setCloseLoading(true)
    try {
      await closeInvestmentAccount(account.id, closePassword)
      toast.success('투자 계좌가 해지되었습니다.')
      router.push('/investment-accounts')
    } catch (err) {
      toast.error(err instanceof ApiRequestError ? err.message : '해지 중 오류가 발생했습니다.')
    } finally {
      setCloseLoading(false)
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
          <Skeleton className="h-28 w-full rounded-lg" />
        </div>
      ) : error ? (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : !account ? (
        <p className="text-center text-muted-foreground py-12">투자 계좌를 찾을 수 없습니다.</p>
      ) : (
        <>
          <Card className="border-border bg-primary text-primary-foreground">
            <CardContent className="pt-5 pb-5">
              <div className="flex items-start justify-between mb-4">
                <div>
                  <p className="text-sm font-medium text-primary-foreground/70">예수금</p>
                  <p className="text-3xl font-bold tabular-nums mt-1">
                    {formatCurrency(account.cashBalance)}
                  </p>
                </div>
                <Badge variant="secondary" className="text-xs">
                  {statusLabel[account.status] ?? account.status}
                </Badge>
              </div>
              <div>
                <p className="text-xs text-primary-foreground/60">투자 계좌번호</p>
                <p className="text-sm font-mono tracking-wider mt-0.5">{account.accountNumber}</p>
              </div>
            </CardContent>
          </Card>

          <Card className="border-border">
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-medium text-muted-foreground">투자 계좌 정보</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              <InfoRow label="별칭" value={account.nickname || '투자 계좌'} />
              <InfoRow label="통화" value={account.currencyCode} />
              <InfoRow label="상태" value={statusLabel[account.status] ?? account.status} />
              <InfoRow label="개설일" value={account.createdAt ? formatDate(account.createdAt) : '-'} />
              {account.updatedAt && <InfoRow label="수정일" value={formatDate(account.updatedAt)} />}
            </CardContent>
          </Card>

          <Card className="border-border">
            <CardHeader className="pb-3">
              <div className="flex items-center justify-between">
                <CardTitle className="text-sm font-medium text-muted-foreground">보유 종목</CardTitle>
                <Button size="sm" variant="outline" nativeButton={false} render={<Link href="/stocks" />}>
                  주식 거래
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {holdingsLoading ? (
                <div className="flex flex-col gap-3">
                  <Skeleton className="h-16 w-full rounded-lg" />
                  <Skeleton className="h-16 w-full rounded-lg" />
                </div>
              ) : holdingsError ? (
                <Alert variant="destructive">
                  <AlertDescription>{holdingsError}</AlertDescription>
                </Alert>
              ) : holdings.length === 0 ? (
                <p className="text-sm text-muted-foreground text-center py-6">보유 종목이 없습니다.</p>
              ) : (
                <div className="flex flex-col gap-3">
                  {holdings.map((holding) => (
                    <Link
                      key={holding.id}
                      href={`/stocks/${holding.stockCode}?accountId=${account.id}`}
                      className="flex items-center justify-between gap-3 rounded-lg border border-border px-3 py-3 hover:border-primary/40 transition-colors"
                    >
                      <div className="min-w-0">
                        <p className="text-sm font-semibold text-foreground truncate">{holding.stockName}</p>
                        <p className="text-xs text-muted-foreground mt-0.5">{holding.stockCode}</p>
                      </div>
                      <div className="text-right shrink-0">
                        <p className="text-sm font-bold tabular-nums">{formatNumber(holding.quantity)}주</p>
                        <p className="text-xs text-muted-foreground">
                          평단 {formatCurrency(Number(holding.averagePrice))}
                        </p>
                      </div>
                    </Link>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          <div className="flex flex-col gap-2">
            <Dialog open={editOpen} onOpenChange={setEditOpen}>
              <DialogTrigger render={<Button variant="outline" className="w-full justify-start" />}>
                <Edit2 data-icon="inline-start" />
                정보 수정
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>투자 계좌 정보 수정</DialogTitle>
                </DialogHeader>
                {editError && (
                  <Alert variant="destructive">
                    <AlertDescription>{editError}</AlertDescription>
                  </Alert>
                )}
                <div className="flex flex-col gap-4 py-2">
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="pastPassword">현재 비밀번호</Label>
                    <Input
                      id="pastPassword"
                      type="password"
                      inputMode="numeric"
                      maxLength={6}
                      value={pastPassword}
                      onChange={(event) => setPastPassword(event.target.value.replace(/\D/g, ''))}
                      placeholder="숫자 6자리"
                    />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="nickname">새 별칭</Label>
                    <Input
                      id="nickname"
                      value={editNickname}
                      onChange={(event) => setEditNickname(event.target.value)}
                      placeholder="별칭 입력"
                      maxLength={50}
                    />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="newPassword">새 비밀번호</Label>
                    <Input
                      id="newPassword"
                      type="password"
                      inputMode="numeric"
                      maxLength={6}
                      value={newPassword}
                      onChange={(event) => setNewPassword(event.target.value.replace(/\D/g, ''))}
                      placeholder="변경하지 않으려면 비워두세요"
                    />
                  </div>
                </div>
                <DialogFooter>
                  <Button variant="outline" onClick={() => setEditOpen(false)}>
                    취소
                  </Button>
                  <Button onClick={handleUpdate} disabled={editLoading}>
                    {editLoading ? '처리 중...' : '저장'}
                  </Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>

            {account.status === 'ACTIVE' && (
              <AlertDialog>
                <AlertDialogTrigger
                  render={
                    <Button
                      variant="ghost"
                      className="w-full justify-start text-destructive hover:text-destructive hover:bg-destructive/10"
                    />
                  }
                >
                  <Trash2 data-icon="inline-start" />
                  투자 계좌 해지
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>투자 계좌를 해지하시겠습니까?</AlertDialogTitle>
                    <AlertDialogDescription>
                      예수금, 보유 종목, 미체결 주문이 없는 계좌만 해지할 수 있습니다.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="closePassword">계좌 비밀번호</Label>
                    <Input
                      id="closePassword"
                      type="password"
                      inputMode="numeric"
                      maxLength={6}
                      value={closePassword}
                      onChange={(event) => setClosePassword(event.target.value.replace(/\D/g, ''))}
                      placeholder="숫자 6자리"
                    />
                  </div>
                  <AlertDialogFooter>
                    <AlertDialogCancel>취소</AlertDialogCancel>
                    <AlertDialogAction
                      onClick={handleClose}
                      disabled={closeLoading}
                      className="bg-destructive hover:bg-destructive/90"
                    >
                      {closeLoading ? '처리 중...' : '해지하기'}
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            )}
          </div>
        </>
      )}
    </div>
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
