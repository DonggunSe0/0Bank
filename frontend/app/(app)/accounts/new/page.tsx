'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { ArrowLeft, CreditCard } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { useAuth } from '@/contexts/AuthContext'
import { createAccount } from '@/lib/api/accounts'
import { ApiRequestError } from '@/lib/api'

export default function NewAccountPage() {
  const router = useRouter()
  const { user } = useAuth()
  const [nickname, setNickname] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    if (!nickname.trim()) {
      setError('계좌 별칭을 입력해 주세요.')
      return
    }
    if (!user) return

    setLoading(true)
    try {
      const acc = await createAccount({
        nickname: nickname.trim(),
        accountType: 'DEPOSIT',
      })
      toast.success('계좌가 개설되었습니다.')
      router.push(`/accounts/${acc.id}`)
    } catch (err) {
      if (err instanceof ApiRequestError) {
        setError(err.message)
      } else {
        toast.error('계좌 개설 중 오류가 발생했습니다.')
        router.push('/accounts')
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
        <h1 className="text-xl font-bold text-foreground">계좌 개설</h1>
        <p className="text-sm text-muted-foreground mt-0.5">새 입출금 계좌를 만들어 보세요.</p>
      </div>

      <Card className="border-border">
        <CardHeader className="pb-3">
          <div className="flex items-center gap-3">
            <div className="size-10 rounded-full bg-primary/10 flex items-center justify-center">
              <CreditCard className="size-5 text-primary" />
            </div>
            <div>
              <CardTitle className="text-base">입출금 계좌 (DEPOSIT)</CardTitle>
              <p className="text-xs text-muted-foreground mt-0.5">자유롭게 입출금 가능한 기본 계좌</p>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {error && (
            <Alert variant="destructive" className="mb-4">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="nickname">계좌 별칭</Label>
              <Input
                id="nickname"
                placeholder="예: 생활비 통장, 비상금 통장"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                maxLength={20}
                aria-invalid={!!error}
              />
              <p className="text-xs text-muted-foreground">계좌를 구분하기 위한 이름입니다.</p>
            </div>

            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? (
                <span className="flex items-center gap-2">
                  <span className="size-4 border-2 border-primary-foreground border-t-transparent rounded-full animate-spin" />
                  개설 중...
                </span>
              ) : (
                '계좌 개설하기'
              )}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
