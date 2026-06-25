'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { ArrowLeft, CreditCard, Check, AlertCircle } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { connectExternalBank, getExternalCandidates, linkExternalAccounts } from '@/lib/api/accounts'
import type { ExternalCandidate } from '@/lib/api/accounts'
import { ApiRequestError } from '@/lib/api'
import { formatCurrency } from '@/lib/format'

const bankList = [
  { code: '0004', name: 'KB국민은행' },
  { code: '0088', name: '신한은행' },
  { code: '0011', name: 'NH농협은행' },
  { code: '0020', name: '우리은행' },
  { code: '0081', name: '하나은행' },
]

export default function ImportAccountPage() {
  const router = useRouter()
  const [step, setStep] = useState(0) // 0: Credentials Input, 1: Account Selection

  // Form Fields
  const [organization, setOrganization] = useState('0004')
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [birthDate, setBirthDate] = useState('')

  // State Management
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  
  // Candidates State
  const [candidateToken, setCandidateToken] = useState('')
  const [candidates, setCandidates] = useState<ExternalCandidate[]>([])
  const [selectedIndexes, setSelectedIndexes] = useState<Record<number, boolean>>({})

  // Step 1: Connect bank credentials and fetch account candidates
  async function handleConnect(e: React.FormEvent) {
    e.preventDefault()
    setError('')

    if (!loginId.trim() || !password.trim() || !birthDate.trim()) {
      setError('모든 인증 정보를 입력해 주세요.')
      return
    }
    if (birthDate.length !== 6 || !/^\d+$/.test(birthDate)) {
      setError('생년월일은 6자리 숫자(YYMMDD)여야 합니다.')
      return
    }

    setLoading(true)
    try {
      // 1. 등록 (connectedId 발급 및 RDB 암호화 저장)
      await connectExternalBank({
        organization,
        businessType: 'BK',
        clientType: 'P',
        loginType: '1', // ID/PW 로그인 방식
        loginId: loginId.trim(),
        password: password.trim(),
        birthDate: birthDate.trim(),
      })

      // 2. 후보 조회 (실시간 계좌 목록 조회 및 Redis 캐싱)
      const res = await getExternalCandidates(organization)
      
      if (res.accounts.length === 0) {
        setError('해당 기관에 보유하신 입출금 계좌가 발견되지 않았습니다.')
        return
      }

      setCandidateToken(res.candidateToken)
      setCandidates(res.accounts)
      
      // 기연동되지 않은 계좌는 기본으로 선택 활성화
      const initialSelection: Record<number, boolean> = {}
      res.accounts.forEach((acc) => {
        if (!acc.linked) {
          initialSelection[acc.index] = true
        }
      })
      setSelectedIndexes(initialSelection)
      setStep(1)
      toast.success('금융기관 연결에 성공했습니다. 연동할 계좌를 선택하세요.')
    } catch (err) {
      if (err instanceof ApiRequestError) {
        setError(err.message)
      } else {
        setError('금융기관 연결 및 계좌 조회 도중 오류가 발생했습니다.')
      }
    } finally {
      setLoading(false)
    }
  }

  // Step 2: Finalize accounts linkage
  async function handleLink() {
    setError('')
    const indexesToLink = Object.entries(selectedIndexes)
      .filter(([_, checked]) => checked)
      .map(([index]) => parseInt(index, 10))

    if (indexesToLink.length === 0) {
      setError('연동할 계좌를 최소 1개 이상 선택해 주세요.')
      return
    }

    setLoading(true)
    try {
      await linkExternalAccounts({
        candidateToken,
        selectedIndexes: indexesToLink,
      })
      toast.success('선택하신 외부 계좌가 성공적으로 연동되었습니다.')
      router.push('/accounts')
    } catch (err) {
      if (err instanceof ApiRequestError) {
        setError(err.message)
      } else {
        setError('계좌 연동 처리 중 오류가 발생했습니다.')
      }
    } finally {
      setLoading(false)
    }
  }

  const toggleSelect = (index: number, linked: boolean) => {
    if (linked) return // 이미 연동된 계좌는 토글 불가
    setSelectedIndexes((prev) => ({
      ...prev,
      [index]: !prev[index],
    }))
  };

  return (
    <div className="flex flex-col gap-5 max-w-md">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="sm" onClick={() => router.back()} className="-ml-2">
          <ArrowLeft data-icon="inline-start" />
          뒤로
        </Button>
      </div>

      <div>
        <h1 className="text-xl font-bold text-foreground">외부 계좌 연동</h1>
        <p className="text-sm text-muted-foreground mt-0.5">다른 은행의 입출금 계좌를 조회하고 청년은행에 등록합니다.</p>
      </div>

      {error && (
        <Alert variant="destructive" className="border-destructive/30">
          <AlertCircle className="size-4" />
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {step === 0 ? (
        <Card className="border-border">
          <CardHeader className="pb-3">
            <CardTitle className="text-base">1단계. 금융기관 인증 정보 입력</CardTitle>
            <CardDescription>인터넷뱅킹 ID/PW 정보로 보유 계좌를 실시간 조회합니다.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleConnect} className="flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="organization">은행 선택</Label>
                <select
                  id="organization"
                  value={organization}
                  onChange={(e) => setOrganization(e.target.value)}
                  className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
                >
                  {bankList.map((bank) => (
                    <option key={bank.code} value={bank.code}>
                      {bank.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="loginId">인터넷뱅킹 ID</Label>
                <Input
                  id="loginId"
                  placeholder="ID를 입력하세요"
                  value={loginId}
                  onChange={(e) => setLoginId(e.target.value)}
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="password">인터넷뱅킹 비밀번호</Label>
                <Input
                  id="password"
                  type="password"
                  placeholder="비밀번호를 입력하세요"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="birthDate">생년월일 (YYMMDD)</Label>
                <Input
                  id="birthDate"
                  placeholder="예: 990101"
                  maxLength={6}
                  value={birthDate}
                  onChange={(e) => setBirthDate(e.target.value.replace(/\D/g, ''))}
                />
              </div>

              <Button type="submit" className="w-full mt-2" disabled={loading}>
                {loading ? (
                  <span className="flex items-center gap-2">
                    <span className="size-4 border-2 border-primary-foreground border-t-transparent rounded-full animate-spin" />
                    계좌를 조회하는 중...
                  </span>
                ) : (
                  '보유 계좌 조회하기'
                )}
              </Button>
            </form>
          </CardContent>
        </Card>
      ) : (
        <Card className="border-border">
          <CardHeader className="pb-3">
            <CardTitle className="text-base">2단계. 연동할 계좌 선택</CardTitle>
            <CardDescription>발견된 계좌 중 청년은행에 등록할 대상을 선택해 주세요.</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <div className="flex flex-col gap-2 max-h-[300px] overflow-y-auto pr-1">
              {candidates.map((acc) => (
                <div
                  key={acc.index}
                  onClick={() => toggleSelect(acc.index, acc.linked)}
                  className={`flex items-center justify-between p-3 rounded-lg border text-left transition-all ${
                    acc.linked
                      ? 'border-muted bg-muted/30 opacity-70 cursor-not-allowed'
                      : selectedIndexes[acc.index]
                      ? 'border-primary bg-primary/5 cursor-pointer'
                      : 'border-border bg-card hover:bg-muted/30 cursor-pointer'
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <div
                      className={`size-5 rounded flex items-center justify-center border transition-all ${
                        acc.linked
                          ? 'border-muted bg-muted'
                          : selectedIndexes[acc.index]
                          ? 'border-primary bg-primary text-primary-foreground'
                          : 'border-input bg-background'
                      }`}
                    >
                      {(acc.linked || selectedIndexes[acc.index]) && <Check className="size-3.5" />}
                    </div>
                    <div>
                      <div className="flex items-center gap-1.5">
                        <span className="text-sm font-semibold text-foreground">{acc.accountName}</span>
                        {acc.linked && (
                          <span className="text-[10px] bg-muted text-muted-foreground px-1.5 py-0.5 rounded font-medium">
                            연동 완료
                          </span>
                        )}
                      </div>
                      <span className="text-xs text-muted-foreground block mt-0.5">
                        {acc.accountNoMasked}
                      </span>
                    </div>
                  </div>
                  <div className="text-right shrink-0">
                    <span className="text-sm font-bold text-foreground block">
                      {formatCurrency(acc.balance)}
                    </span>
                  </div>
                </div>
              ))}
            </div>

            <div className="flex gap-2.5 mt-2">
              <Button variant="outline" className="flex-1" onClick={() => setStep(0)} disabled={loading}>
                이전으로
              </Button>
              <Button className="flex-1" onClick={handleLink} disabled={loading}>
                {loading ? '연동하는 중...' : '선택 계좌 연동'}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
