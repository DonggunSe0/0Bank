'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { ArrowLeft, BarChart3, ShieldAlert, ShieldCheck } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/contexts/AuthContext'
import { createInvestmentAccount } from '@/lib/api/investments'
import { getMe } from '@/lib/api/users'
import { ApiRequestError } from '@/lib/api'

export default function NewInvestmentAccountPage() {
  const router = useRouter()
  const { user, isLoading: authLoading, updateUser } = useAuth()
  const [nickname, setNickname] = useState('')
  const [accountPassword, setAccountPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let mounted = true

    async function refreshUser() {
      try {
        const me = await getMe()
        if (mounted) updateUser(me)
      } catch {
        /* AuthContext가 세션 복구를 처리한다. */
      }
    }

    refreshUser()
    return () => {
      mounted = false
    }
  }, [updateUser])

  const isVerified = user?.identityVerified === true

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setError('')

    if (!isVerified) {
      setError('본인인증을 완료한 회원만 투자 계좌를 개설할 수 있습니다.')
      return
    }
    if (!/^\d{6}$/.test(accountPassword)) {
      setError('투자 계좌 비밀번호는 숫자 6자리여야 합니다.')
      return
    }

    setLoading(true)
    try {
      const account = await createInvestmentAccount({
        nickname: nickname.trim() || undefined,
        accountPassword,
        currencyCode: 'KRW',
      })
      toast.success('투자 계좌가 개설되었습니다.')
      router.push(`/investment-accounts/${account.id}`)
    } catch (err) {
      if (err instanceof ApiRequestError) {
        if (err.code === 'IDENTITY_VERIFICATION_REQUIRED') {
          setError('본인인증을 완료한 회원만 투자 계좌를 개설할 수 있습니다.')
        } else {
          setError(err.message)
        }
      } else {
        setError('투자 계좌 개설 중 오류가 발생했습니다.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex flex-col gap-5 max-w-md">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="sm" onClick={() => router.back()} className="-ml-2">
          <ArrowLeft data-icon="inline-start" />
          뒤로
        </Button>
      </div>

      <div>
        <h1 className="text-xl font-bold text-foreground">투자 계좌 개설</h1>
        <p className="text-sm text-muted-foreground mt-0.5">
          주식 거래에 사용할 원화 투자 계좌를 만듭니다.
        </p>
      </div>

      <Card className="border-border">
        <CardHeader className="pb-3">
          <div className="flex items-center gap-3">
            <div className="size-10 rounded-full bg-primary/10 flex items-center justify-center">
              <BarChart3 className="size-5 text-primary" />
            </div>
            <div>
              <CardTitle className="text-base">투자 계좌 (KRW)</CardTitle>
              <p className="text-xs text-muted-foreground mt-0.5">
                본인인증 완료 회원 대상 · 계좌 개설 시 별도 인증 없음
              </p>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {error && (
            <Alert variant="destructive" className="mb-4">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          {authLoading ? (
            <div className="flex flex-col gap-3">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : !isVerified ? (
            <Alert>
              <ShieldAlert className="size-4" />
              <AlertDescription className="flex flex-col gap-3">
                <div>
                  <p className="font-medium text-foreground">본인인증이 필요합니다</p>
                  <p className="text-sm text-muted-foreground mt-1">
                    투자 계좌 개설은 본인인증을 완료한 회원에게만 제공됩니다. 계좌 개설 화면에서는
                    별도의 본인인증 절차를 진행하지 않으므로, 먼저 본인인증 메뉴에서 인증을
                    완료해 주세요.
                  </p>
                </div>
                <Button
                  type="button"
                  size="sm"
                  className="w-fit"
                  nativeButton={false}
                  render={<Link href="/identity" />}
                >
                  본인인증 하러가기
                </Button>
              </AlertDescription>
            </Alert>
          ) : (
            <>
              <div className="mb-4 flex items-center gap-2 rounded-md border border-border bg-muted/40 px-3 py-2">
                <ShieldCheck className="size-4 shrink-0 text-[hsl(var(--success))]" style={{ color: 'oklch(0.52 0.14 155)' }} />
                <p className="text-sm text-foreground">본인인증 완료 · 계좌 정보를 입력해 주세요</p>
              </div>

              <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="nickname">계좌 별칭</Label>
                  <Input
                    id="nickname"
                    placeholder="예: 장기투자 계좌"
                    value={nickname}
                    onChange={(event) => setNickname(event.target.value)}
                    maxLength={50}
                  />
                  <p className="text-xs text-muted-foreground">선택 입력 · 최대 50자</p>
                </div>

                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="accountPassword">계좌 비밀번호</Label>
                  <Input
                    id="accountPassword"
                    inputMode="numeric"
                    type="password"
                    placeholder="숫자 6자리"
                    maxLength={6}
                    value={accountPassword}
                    onChange={(event) => setAccountPassword(event.target.value.replace(/\D/g, ''))}
                  />
                  <p className="text-xs text-muted-foreground">주문·해지 시 사용하는 숫자 6자리 비밀번호입니다.</p>
                </div>

                <Button type="submit" className="w-full" disabled={loading}>
                  {loading ? '개설 중...' : '투자 계좌 개설하기'}
                </Button>
              </form>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
