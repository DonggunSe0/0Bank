'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { ArrowLeft, BarChart3, KeyRound } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  createInvestmentAccount,
  issueInvestmentAccountOpenVerification,
} from '@/lib/api/investments'
import { ApiRequestError } from '@/lib/api'

export default function NewInvestmentAccountPage() {
  const router = useRouter()
  const [nickname, setNickname] = useState('')
  const [accountPassword, setAccountPassword] = useState('')
  const [verificationKey, setVerificationKey] = useState('')
  const [expiresInSeconds, setExpiresInSeconds] = useState<number | null>(null)
  const [loading, setLoading] = useState(false)
  const [issuing, setIssuing] = useState(false)
  const [error, setError] = useState('')

  async function handleIssueVerificationKey() {
    setError('')
    setIssuing(true)
    try {
      const response = await issueInvestmentAccountOpenVerification()
      setVerificationKey(response.verificationKey)
      setExpiresInSeconds(response.expiresInSeconds)
      toast.success('개설 인증키가 발급되었습니다.')
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : '개설 인증키를 발급하지 못했습니다.')
    } finally {
      setIssuing(false)
    }
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setError('')

    if (!/^\d{6}$/.test(accountPassword)) {
      setError('투자 계좌 비밀번호는 숫자 6자리여야 합니다.')
      return
    }
    if (!verificationKey.trim()) {
      setError('개설 인증키를 먼저 발급받아 주세요.')
      return
    }

    setLoading(true)
    try {
      const account = await createInvestmentAccount({
        nickname: nickname.trim() || undefined,
        accountPassword,
        verificationKey,
        currencyCode: 'KRW',
      })
      toast.success('투자 계좌가 개설되었습니다.')
      router.push(`/investment-accounts/${account.id}`)
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : '투자 계좌 개설 중 오류가 발생했습니다.')
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
              <p className="text-xs text-muted-foreground mt-0.5">본인인증 완료 사용자만 개설 가능</p>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {error && (
            <Alert variant="destructive" className="mb-4">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          <div className="mb-4 rounded-md border border-border p-3">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-sm font-medium text-foreground">개설 인증키</p>
                <p className="text-xs text-muted-foreground">
                  {verificationKey
                    ? `유효시간 ${expiresInSeconds ?? '-'}초`
                    : '계좌 개설 전 인증키를 발급받아 주세요.'}
                </p>
              </div>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleIssueVerificationKey}
                disabled={issuing}
              >
                <KeyRound data-icon="inline-start" />
                {issuing ? '발급 중' : '발급'}
              </Button>
            </div>
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
            </div>

            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? '개설 중...' : '투자 계좌 개설하기'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
