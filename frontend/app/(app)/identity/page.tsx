'use client'

import { useEffect, useRef, useState } from 'react'
import { CheckCircle2, ChevronRight, Upload, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Progress } from '@/components/ui/progress'
import { Badge } from '@/components/ui/badge'
import {
  getVerificationStatus,
  requestOneWon,
  uploadIdCardOcr,
  verifyOneWon,
} from '@/lib/api/identity'
import { ApiRequestError } from '@/lib/api'
import { useAuth } from '@/contexts/AuthContext'
import type { VerificationResponse, VerificationStatus } from '@/lib/types'

const steps = ['신분증 업로드', '검토 대기', '1원 인증']

const POLL_INTERVAL_MS = 1500
const MAX_POLL_ATTEMPTS = 30 // 약 45초까지 대기 후 타임아웃 처리

const bankOrganizations = [
  { code: '004', name: '국민은행' },
  { code: '011', name: '농협은행' },
  { code: '020', name: '우리은행' },
  { code: '081', name: '하나은행' },
  { code: '088', name: '신한은행' },
]

const statusLabel: Record<VerificationStatus, string> = {
  OCR_PENDING: 'OCR 대기',
  OCR_COMPLETED: 'OCR 완료',
  GOVERNMENT_VERIFIED: '정부 인증 완료',
  ONE_WON_PENDING: '1원 인증 대기',
  COMPLETED: '인증 완료',
  FAILED: '인증 실패',
}

export default function IdentityPage() {
  const { user, updateUser } = useAuth()
  const [currentStep, setCurrentStep] = useState(0)
  const [status, setStatus] = useState<VerificationStatus | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [failureReason, setFailureReason] = useState('')
  const [polling, setPolling] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [accountNumber, setAccountNumber] = useState('')
  const [organization, setOrganization] = useState('088')
  const [verifyCode, setVerifyCode] = useState('')
  const [statusLoading, setStatusLoading] = useState(true)
  const fileRef = useRef<HTMLInputElement>(null)
  const mountedRef = useRef(true)

  useEffect(() => {
    // React 18 Strict Mode(dev)는 마운트 시 effect를 mount→cleanup→mount 순서로
    // 한 번 더 실행한다. cleanup만 false로 두면 실제로는 마운트된 상태인데도
    // mountedRef.current가 false로 굳어버려 이후 폴링이 즉시 UNMOUNTED로 빠진다.
    // 마운트될 때마다 true로 재설정해야 한다.
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  useEffect(() => {
    // 이 컴포넌트는 사이드바에서 재진입할 때마다 새로 마운트되어 status가 null로 초기화된다.
    // 서버의 실제 본인인증 상태를 확인하지 않으면 이미 완료/진행 중인 사용자도 매번
    // 1단계 업로드 화면부터 다시 보게 된다 — 진입 시 한 번 현재 상태를 조회해 반영한다.
    let active = true

    async function loadInitialStatus() {
      if (user?.identityVerified) {
        if (active) {
          setStatus('COMPLETED')
          setStatusLoading(false)
        }
        return
      }
      try {
        const res = await getVerificationStatus()
        if (!active) return
        setStatus(res.status)
        if (res.status === 'FAILED') {
          setFailureReason(res.failureReason || '인증에 실패했습니다. 다시 시도해 주세요.')
        } else if (res.status === 'GOVERNMENT_VERIFIED') {
          setCurrentStep(1)
        } else if (res.status === 'ONE_WON_PENDING') {
          setCurrentStep(2)
        }
      } catch {
        // 진행 중인 인증 세션이 없는 신규 사용자 — 1단계부터 시작
      } finally {
        if (active) setStatusLoading(false)
      }
    }

    loadInitialStatus()
    return () => {
      active = false
    }
  }, [user?.identityVerified])

  const progress = ((currentStep + 1) / steps.length) * 100

  /**
   * OCR/1원송금은 백엔드에서 비동기로 처리된다. 업로드/요청 직후 응답은 아직 처리 중인 상태일 뿐이므로,
   * 다음 단계로 넘어가기 전에 상태 조회 API로 GOVERNMENT_VERIFIED/ONE_WON_PENDING(준비 완료) 또는
   * FAILED(실패)가 될 때까지 폴링한다. 본인 명의 불일치 등으로 실패한 경우 여기서 걸러져
   * 다음 단계(1원인증 등) 화면으로 넘어가지 않는다.
   */
  async function pollUntilReady(readyStatuses: VerificationStatus[]): Promise<VerificationResponse> {
    for (let attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
      if (!mountedRef.current) {
        throw new Error('UNMOUNTED')
      }
      await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS))
      try {
        const res = await getVerificationStatus()
        if (res.status === 'FAILED' || readyStatuses.includes(res.status)) {
          return res
        }
      } catch (err) {
        // 일시적 네트워크/서버 오류로 한 번 실패해도 전체 인증 흐름을 끝내지 않고 다음 폴링에서 재시도한다.
        console.error('[polling] 상태 조회 실패, 다음 시도에서 재시도합니다.', err)
      }
    }
    throw new Error('POLL_TIMEOUT')
  }

  async function handleOcrUpload() {
    if (!file) {
      setError('신분증 이미지를 선택해 주세요.')
      return
    }
    setError('')
    setLoading(true)
    try {
      await uploadIdCardOcr(file)
      setStatus('OCR_PENDING')
      setPolling('신분증을 검토하는 중입니다. 잠시만 기다려주세요...')

      const res = await pollUntilReady(['GOVERNMENT_VERIFIED', 'ONE_WON_PENDING'])
      if (!mountedRef.current) return

      setStatus(res.status)
      if (res.status === 'FAILED') {
        setFailureReason(res.failureReason || '인증에 실패했습니다. 다시 시도해 주세요.')
      } else {
        setCurrentStep(1)
        toast.success('신분증 검토가 완료되었습니다.')
      }
    } catch (err) {
      if (!mountedRef.current) return
      if (err instanceof Error && err.message === 'POLL_TIMEOUT') {
        setError('처리 시간이 오래 걸리고 있습니다. 잠시 후 새로고침하여 다시 확인해 주세요.')
      } else if (err instanceof ApiRequestError) {
        setError(err.message)
      } else {
        setError('신분증 업로드 중 오류가 발생했습니다.')
      }
    } finally {
      if (mountedRef.current) {
        setPolling('')
        setLoading(false)
      }
    }
  }

  async function handleOneWonRequest() {
    if (!accountNumber.trim()) {
      setError('계좌번호를 입력해 주세요.')
      return
    }
    if (!/^\d{3}$/.test(organization)) {
      setError('은행 기관코드를 선택해 주세요.')
      return
    }
    setError('')
    setLoading(true)
    try {
      await requestOneWon(accountNumber.replace(/\D/g, ''), organization)
      setPolling('1원 송금을 처리하는 중입니다. 잠시만 기다려주세요...')

      const res = await pollUntilReady(['ONE_WON_PENDING'])
      if (!mountedRef.current) return

      setStatus(res.status)
      if (res.status === 'FAILED') {
        setFailureReason(res.failureReason || '1원 송금에 실패했습니다. 다시 시도해 주세요.')
      } else {
        setCurrentStep(2)
        toast.info('1원이 송금되었습니다. 입금된 4자리 코드를 확인하세요.')
      }
    } catch (err) {
      if (!mountedRef.current) return
      if (err instanceof Error && err.message === 'POLL_TIMEOUT') {
        setError('처리 시간이 오래 걸리고 있습니다. 잠시 후 새로고침하여 다시 확인해 주세요.')
      } else if (err instanceof ApiRequestError) {
        setError(err.message)
      } else {
        setError('1원 송금 요청 중 오류가 발생했습니다.')
      }
    } finally {
      if (mountedRef.current) {
        setPolling('')
        setLoading(false)
      }
    }
  }

  async function handleVerify() {
    if (verifyCode.length !== 4) {
      setError('4자리 코드를 입력해 주세요.')
      return
    }
    setError('')
    setLoading(true)
    try {
      const res = await verifyOneWon(verifyCode)
      setStatus(res.status)
      if (res.status === 'COMPLETED') {
        updateUser({ ...user!, identityVerified: true })
        toast.success('본인인증이 완료되었습니다!')
      }
    } catch (err) {
      if (err instanceof ApiRequestError) {
        setError(err.message)
      } else {
        setError('인증 코드 검증 중 오류가 발생했습니다.')
      }
    } finally {
      setLoading(false)
    }
  }

  if (statusLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <span className="size-6 border-2 border-primary border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  if (status === 'COMPLETED') {
    return (
      <div className="flex flex-col items-center justify-center gap-5 py-12 max-w-sm mx-auto text-center">
        <CheckCircle2 className="size-16" style={{ color: 'oklch(0.52 0.14 155)' }} />
        <div>
          <h1 className="text-xl font-bold text-foreground">본인인증 완료</h1>
          <p className="text-sm text-muted-foreground mt-1">
            모든 서비스를 자유롭게 이용하실 수 있습니다.
          </p>
        </div>
        <Button nativeButton={false} render={<a href="/dashboard" />}>
          대시보드로 이동
        </Button>
      </div>
    )
  }

  if (status === 'FAILED') {
    return (
      <div className="flex flex-col items-center justify-center gap-5 py-12 max-w-sm mx-auto text-center">
        <XCircle className="size-16 text-destructive" />
        <div>
          <h1 className="text-xl font-bold text-foreground">인증 실패</h1>
          <p className="text-sm text-muted-foreground mt-1">
            {failureReason || '인증에 실패했습니다. 다시 시도해 주세요.'}
          </p>
        </div>
        <Button
          variant="outline"
          onClick={() => {
            setCurrentStep(0)
            setStatus(null)
            setFile(null)
            setFailureReason('')
            setError('')
          }}
        >
          다시 시작
        </Button>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-5 max-w-md">
      <div>
        <h1 className="text-xl font-bold text-foreground">본인인증</h1>
        <p className="text-sm text-muted-foreground mt-0.5">
          안전한 서비스 이용을 위해 본인인증을 완료해 주세요.
        </p>
      </div>

      {/* Progress */}
      <Card className="border-border">
        <CardContent className="pt-4 pb-4">
          <div className="flex justify-between text-xs text-muted-foreground mb-2">
            {steps.map((s, i) => (
              <span
                key={s}
                className={
                  i <= currentStep
                    ? 'text-primary font-medium'
                    : 'text-muted-foreground'
                }
              >
                {i + 1}. {s}
              </span>
            ))}
          </div>
          <Progress value={progress} className="h-1.5" />
        </CardContent>
      </Card>

      {/* Status badge */}
      {status && (
        <div className="flex items-center gap-2">
          <span className="text-xs text-muted-foreground">현재 상태:</span>
          <Badge variant="secondary">{statusLabel[status]}</Badge>
        </div>
      )}

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {/* Step 1: ID Card OCR */}
      {currentStep === 0 && (
        <Card className="border-border">
          <CardHeader className="pb-3">
            <CardTitle className="text-base">1단계. 신분증 업로드</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <p className="text-sm text-muted-foreground">
              주민등록증 또는 운전면허증 앞면 사진을 업로드해 주세요.
            </p>

            <div
              className="border-2 border-dashed border-border rounded-lg p-8 text-center cursor-pointer hover:border-primary/50 transition-colors"
              onClick={() => fileRef.current?.click()}
            >
              <Upload className="size-8 text-muted-foreground mx-auto mb-2" />
              {file ? (
                <p className="text-sm font-medium text-foreground">{file.name}</p>
              ) : (
                <p className="text-sm text-muted-foreground">클릭하여 이미지 선택</p>
              )}
              <p className="text-xs text-muted-foreground mt-1">JPG, PNG 지원</p>
            </div>
            <input
              ref={fileRef}
              type="file"
              accept="image/png,image/jpeg"
              className="hidden"
              onChange={(e) => {
                const f = e.target.files?.[0]
                if (f) { setFile(f); setError('') }
              }}
              aria-label="신분증 이미지 파일"
            />

            <Button onClick={handleOcrUpload} disabled={loading || !file} className="w-full">
              {loading ? (
                <span className="flex items-center gap-2">
                  <span className="size-4 border-2 border-primary-foreground border-t-transparent rounded-full animate-spin" />
                  {polling ? '검토 중...' : '업로드 중...'}
                </span>
              ) : (
                <>
                  <Upload data-icon="inline-start" />
                  업로드
                </>
              )}
            </Button>
            {polling && (
              <p className="text-xs text-muted-foreground text-center">{polling}</p>
            )}
          </CardContent>
        </Card>
      )}

      {/* Step 2: Review Pending */}
      {currentStep === 1 && (
        <Card className="border-border">
          <CardHeader className="pb-3">
            <CardTitle className="text-base">2단계. 검토 및 1원 송금</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <div className="bg-muted rounded-lg p-4">
              <p className="text-sm font-medium text-foreground mb-1">신분증 OCR이 완료되었습니다.</p>
              <p className="text-sm text-muted-foreground">
                1원 인증을 위해 본인 계좌번호를 입력해 주세요. 해당 계좌로 1원이 송금되며, 입금 내역에 표시된 4자리 코드를 확인해 주세요.
              </p>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="organization">은행</Label>
              <select
                id="organization"
                value={organization}
                onChange={(event) => setOrganization(event.target.value)}
                className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground"
              >
                {bankOrganizations.map((bank) => (
                  <option key={bank.code} value={bank.code}>
                    {bank.name} ({bank.code})
                  </option>
                ))}
              </select>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="accountNumber">본인 계좌번호</Label>
              <Input
                id="accountNumber"
                placeholder="000-0000-000000"
                value={accountNumber}
                onChange={(e) => setAccountNumber(e.target.value.replace(/[^0-9-]/g, ''))}
              />
            </div>

            <Button onClick={handleOneWonRequest} disabled={loading} className="w-full">
              {loading ? (polling ? '처리 확인 중...' : '처리 중...') : '1원 송금 요청'}
              {!loading && <ChevronRight data-icon="inline-end" />}
            </Button>
            {polling && (
              <p className="text-xs text-muted-foreground text-center">{polling}</p>
            )}
          </CardContent>
        </Card>
      )}

      {/* Step 3: Code Verification */}
      {currentStep === 2 && (
        <Card className="border-border">
          <CardHeader className="pb-3">
            <CardTitle className="text-base">3단계. 코드 인증</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <p className="text-sm text-muted-foreground">
              계좌 입금 내역에서 1원 송금 메모에 표시된 4자리 숫자를 입력해 주세요.
            </p>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="verifyCode">인증 코드 (4자리)</Label>
              <Input
                id="verifyCode"
                inputMode="numeric"
                maxLength={4}
                placeholder="0000"
                value={verifyCode}
                onChange={(e) => setVerifyCode(e.target.value.replace(/[^0-9]/g, ''))}
                className="text-center text-xl tracking-widest font-mono"
              />
            </div>

            <Button
              onClick={handleVerify}
              disabled={loading || verifyCode.length !== 4}
              className="w-full"
            >
              {loading ? '인증 중...' : '인증 완료'}
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
