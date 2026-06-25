'use client'

import { useState } from 'react'
import Link from 'next/link'
import Script from 'next/script'
import { useRouter } from 'next/navigation'
import { Eye, EyeOff, ShieldCheck, UserPlus } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { signup as apiSignup } from '@/lib/api/auth'
import { ApiRequestError } from '@/lib/api'
import { ageGroupOptions, interestOptions, occupationOptions, regionOptions } from '@/lib/profileOptions'
import type { AgeGroup, FinancialInterest, OccupationStatus, Region } from '@/lib/types'

declare global {
  interface Window {
    PortOne?: {
      requestIdentityVerification: (params: {
        storeId: string
        channelKey: string
        identityVerificationId: string
      }) => Promise<{
        identityVerificationId?: string
        code?: string
        message?: string
      }>
    }
  }
}

interface FormState {
  email: string
  password: string
  passwordConfirm: string
  name: string
  phoneNumber: string
  birthDate: string
  identityVerificationId: string
  ageGroup: AgeGroup | ''
  region: Region | ''
  occupationStatus: OccupationStatus | ''
  agreedServiceTerms: boolean
  agreedPersonalInfo: boolean
  agreedFinancialInfo: boolean
  agreedMarketing: boolean
}

type FormErrors = Partial<Record<keyof FormState, string>>

export default function SignupPage() {
  const router = useRouter()

  // 0단계: 계정 정보 + 본인인증, 1단계: 프로필 설정(마이페이지와 동일한 항목).
  // 본인인증이 끝나야만 1단계로 넘어가고, 1단계를 마쳐야 실제 회원가입 API가 호출된다.
  const [step, setStep] = useState<0 | 1>(0)

  const [form, setForm] = useState<FormState>({
    email: '',
    password: '',
    passwordConfirm: '',
    name: '',
    phoneNumber: '',
    birthDate: '',
    identityVerificationId: '',
    ageGroup: '',
    region: '',
    occupationStatus: '',
    agreedServiceTerms: false,
    agreedPersonalInfo: false,
    agreedFinancialInfo: false,
    agreedMarketing: false,
  })
  const [interests, setInterests] = useState<Set<FinancialInterest>>(new Set())
  const [errors, setErrors] = useState<FormErrors>({})
  const [serverError, setServerError] = useState('')
  const [serverFieldErrors, setServerFieldErrors] = useState<Record<string, string>>({})
  const [showPw, setShowPw] = useState(false)
  const [loading, setLoading] = useState(false)
  const [verifying, setVerifying] = useState(false)

  function validateAccountStep(): FormErrors {
    const e: FormErrors = {}
    if (!form.name.trim()) e.name = '이름을 입력해 주세요.'
    if (!form.email) e.email = '이메일을 입력해 주세요.'
    else if (!/\S+@\S+\.\S+/.test(form.email)) e.email = '올바른 이메일 형식이 아닙니다.'
    if (!form.password) e.password = '비밀번호를 입력해 주세요.'
    else if (form.password.length < 8) e.password = '비밀번호는 8자 이상이어야 합니다.'
    if (form.password !== form.passwordConfirm) e.passwordConfirm = '비밀번호가 일치하지 않습니다.'
    if (!form.phoneNumber) e.phoneNumber = '휴대폰 번호를 입력해 주세요.'
    else if (!/^01[0-9]{8,9}$/.test(form.phoneNumber))
      e.phoneNumber = '올바른 형식으로 입력해 주세요. (예: 01012345678)'
    if (!form.birthDate) e.birthDate = '생년월일을 입력해 주세요.'
    if (!form.identityVerificationId.trim()) {
      e.identityVerificationId = '본인인증을 완료해 주세요.'
    }
    if (!form.agreedServiceTerms) e.agreedServiceTerms = '서비스 이용약관에 동의해 주세요.'
    if (!form.agreedPersonalInfo) e.agreedPersonalInfo = '개인정보 수집·이용에 동의해 주세요.'
    if (!form.agreedFinancialInfo) e.agreedFinancialInfo = '금융정보 수집·이용에 동의해 주세요.'
    return e
  }

  function validateProfileStep(): FormErrors {
    const e: FormErrors = {}
    if (!form.ageGroup) e.ageGroup = '연령대를 선택해 주세요.'
    if (!form.region) e.region = '지역을 선택해 주세요.'
    if (!form.occupationStatus) e.occupationStatus = '직업 상태를 선택해 주세요.'
    return e
  }

  async function handleVerifyIdentity() {
    if (!window.PortOne) {
      toast.error('본인인증 모듈을 불러오는 중입니다. 잠시 후 다시 시도해 주세요.')
      return
    }
    const storeId = 'store-bb65e821-eeb0-4a12-bd80-4e036a770949'
    const channelKey = 'channel-key-bcd683d9-77a5-406d-866e-7262a8df00d0'

    setVerifying(true)
    try {
      const response = await window.PortOne.requestIdentityVerification({
        storeId,
        channelKey,
        identityVerificationId: `identity-verification-${crypto.randomUUID()}`,
      })

      if (response?.code !== undefined) {
        toast.error(response.message ?? '본인인증에 실패했습니다.')
        return
      }
      if (!response?.identityVerificationId) {
        toast.error('본인인증 결과를 확인할 수 없습니다. 다시 시도해 주세요.')
        return
      }

      setForm((p) => ({ ...p, identityVerificationId: response.identityVerificationId! }))
      setErrors((p) => ({ ...p, identityVerificationId: undefined }))
      toast.success('본인인증이 완료되었습니다.')
    } catch {
      toast.error('본인인증 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.')
    } finally {
      setVerifying(false)
    }
  }

  function handleGoToProfileStep(e: React.FormEvent) {
    e.preventDefault()
    setServerError('')
    const errs = validateAccountStep()
    setErrors((p) => ({ ...p, ...errs }))
    if (Object.keys(errs).length > 0) return
    setStep(1)
  }

  function toggleInterest(value: FinancialInterest) {
    setInterests((prev) => {
      const next = new Set(prev)
      if (next.has(value)) next.delete(value)
      else next.add(value)
      return next
    })
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setServerError('')
    setServerFieldErrors({})
    const errs = validateProfileStep()
    setErrors((p) => ({ ...p, ...errs }))
    if (Object.keys(errs).length > 0) return

    setLoading(true)
    try {
      const res = await apiSignup({
        identityVerificationId: form.identityVerificationId.trim(),
        email: form.email,
        password: form.password,
        name: form.name,
        phoneNumber: form.phoneNumber,
        birthDate: form.birthDate,
        ageGroup: form.ageGroup as AgeGroup,
        region: form.region as Region,
        occupationStatus: form.occupationStatus as OccupationStatus,
        financialInterests: Array.from(interests),
        agreedServiceTerms: form.agreedServiceTerms,
        agreedPersonalInfo: form.agreedPersonalInfo,
        agreedFinancialInfo: form.agreedFinancialInfo,
        agreedMarketing: form.agreedMarketing,
      })
      toast.success(`${res.name}님, 회원가입이 완료되었습니다. 로그인해 주세요.`)
      router.push('/login')
    } catch (err) {
      if (err instanceof ApiRequestError) {
        if (err.details && err.details.length > 0) {
          const fieldErrs: Record<string, string> = {}
          for (const d of err.details) fieldErrs[d.field] = d.reason
          setServerFieldErrors(fieldErrs)
        } else {
          setServerError(err.message)
        }
      } else {
        setServerError('회원가입 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.')
      }
    } finally {
      setLoading(false)
    }
  }

  function fieldError(name: keyof FormState) {
    return errors[name] ?? serverFieldErrors[name]
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background px-4 py-10">
      <Script src="https://cdn.portone.io/v2/browser-sdk.js" strategy="afterInteractive" />
      <div className="w-full max-w-sm">
        <div className="mb-6 text-center">
          <div className="inline-flex items-center justify-center size-12 rounded-lg bg-primary mb-3">
            <span className="text-primary-foreground font-bold text-lg">청</span>
          </div>
          <h1 className="text-2xl font-bold text-foreground">청년은행</h1>
          <p className="text-sm text-muted-foreground mt-1">청년을 위한 스마트 금융</p>
        </div>

        <div className="bg-card border border-border rounded-lg p-6 shadow-sm">
          <div className="flex items-center justify-between mb-5">
            <h2 className="text-lg font-semibold text-foreground">
              {step === 0 ? '회원가입' : '프로필 설정'}
            </h2>
            <span className="text-xs text-muted-foreground">{step + 1} / 2</span>
          </div>

          {serverError && (
            <Alert variant="destructive" className="mb-4">
              <AlertDescription>{serverError}</AlertDescription>
            </Alert>
          )}

          {step === 0 ? (
            <form onSubmit={handleGoToProfileStep} noValidate className="flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="name">이름</Label>
                <Input
                  id="name"
                  placeholder="홍길동"
                  value={form.name}
                  onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))}
                  aria-invalid={!!fieldError('name')}
                />
                {fieldError('name') && (
                  <p className="text-xs text-destructive">{fieldError('name')}</p>
                )}
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="email">이메일</Label>
                <Input
                  id="email"
                  type="email"
                  autoComplete="email"
                  placeholder="example@email.com"
                  value={form.email}
                  onChange={(e) => setForm((p) => ({ ...p, email: e.target.value }))}
                  aria-invalid={!!fieldError('email')}
                />
                {fieldError('email') && (
                  <p className="text-xs text-destructive">{fieldError('email')}</p>
                )}
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="password">비밀번호</Label>
                <div className="relative">
                  <Input
                    id="password"
                    type={showPw ? 'text' : 'password'}
                    autoComplete="new-password"
                    placeholder="8자 이상"
                    value={form.password}
                    onChange={(e) => setForm((p) => ({ ...p, password: e.target.value }))}
                    aria-invalid={!!fieldError('password')}
                    className="pr-10"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPw((v) => !v)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                    aria-label={showPw ? '비밀번호 숨기기' : '비밀번호 표시'}
                  >
                    {showPw ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                  </button>
                </div>
                {fieldError('password') && (
                  <p className="text-xs text-destructive">{fieldError('password')}</p>
                )}
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="passwordConfirm">비밀번호 확인</Label>
                <Input
                  id="passwordConfirm"
                  type={showPw ? 'text' : 'password'}
                  autoComplete="new-password"
                  placeholder="비밀번호 재입력"
                  value={form.passwordConfirm}
                  onChange={(e) => setForm((p) => ({ ...p, passwordConfirm: e.target.value }))}
                  aria-invalid={!!fieldError('passwordConfirm')}
                />
                {fieldError('passwordConfirm') && (
                  <p className="text-xs text-destructive">{fieldError('passwordConfirm')}</p>
                )}
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="phoneNumber">휴대폰 번호</Label>
                <Input
                  id="phoneNumber"
                  type="tel"
                  inputMode="numeric"
                  placeholder="01012345678"
                  value={form.phoneNumber}
                  onChange={(e) =>
                    setForm((p) => ({ ...p, phoneNumber: e.target.value.replace(/[^0-9]/g, '') }))
                  }
                  aria-invalid={!!fieldError('phoneNumber')}
                />
                {fieldError('phoneNumber') && (
                  <p className="text-xs text-destructive">{fieldError('phoneNumber')}</p>
                )}
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="birthDate">생년월일</Label>
                <Input
                  id="birthDate"
                  type="date"
                  value={form.birthDate}
                  onChange={(e) => setForm((p) => ({ ...p, birthDate: e.target.value }))}
                  aria-invalid={!!fieldError('birthDate')}
                />
                {fieldError('birthDate') && (
                  <p className="text-xs text-destructive">{fieldError('birthDate')}</p>
                )}
              </div>

              <div className="flex flex-col gap-1.5">
                <Label>본인인증</Label>
                {form.identityVerificationId ? (
                  <div className="flex items-center justify-between rounded-md border border-border px-3 py-2">
                    <span className="flex items-center gap-1.5 text-sm text-foreground">
                      <ShieldCheck className="size-4 text-primary" />
                      본인인증이 완료되었습니다.
                    </span>
                    <button
                      type="button"
                      onClick={handleVerifyIdentity}
                      className="text-xs text-muted-foreground hover:text-foreground underline"
                    >
                      다시 인증
                    </button>
                  </div>
                ) : (
                  <Button
                    type="button"
                    variant="outline"
                    className="w-full"
                    onClick={handleVerifyIdentity}
                    disabled={verifying}
                    aria-invalid={!!fieldError('identityVerificationId')}
                  >
                    {verifying ? '본인인증 진행 중...' : '휴대폰 본인인증하기'}
                  </Button>
                )}
                {fieldError('identityVerificationId') && (
                  <p className="text-xs text-destructive">{fieldError('identityVerificationId')}</p>
                )}
              </div>

              <div className="flex flex-col gap-2 rounded-md border border-border p-3">
                <CheckboxField
                  id="agreedServiceTerms"
                  checked={form.agreedServiceTerms}
                  onChange={(checked) => setForm((p) => ({ ...p, agreedServiceTerms: checked }))}
                  label="서비스 이용약관에 동의합니다. (필수)"
                  error={fieldError('agreedServiceTerms')}
                />
                <CheckboxField
                  id="agreedPersonalInfo"
                  checked={form.agreedPersonalInfo}
                  onChange={(checked) => setForm((p) => ({ ...p, agreedPersonalInfo: checked }))}
                  label="개인정보 수집·이용에 동의합니다. (필수)"
                  error={fieldError('agreedPersonalInfo')}
                />
                <CheckboxField
                  id="agreedFinancialInfo"
                  checked={form.agreedFinancialInfo}
                  onChange={(checked) => setForm((p) => ({ ...p, agreedFinancialInfo: checked }))}
                  label="금융정보 수집·이용에 동의합니다. (필수)"
                  error={fieldError('agreedFinancialInfo')}
                />
                <CheckboxField
                  id="agreedMarketing"
                  checked={form.agreedMarketing}
                  onChange={(checked) => setForm((p) => ({ ...p, agreedMarketing: checked }))}
                  label="마케팅 정보 수신에 동의합니다. (선택)"
                />
              </div>

              <Button type="submit" className="w-full mt-2">
                다음
              </Button>
            </form>
          ) : (
            <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
              <p className="text-sm text-muted-foreground">
                서비스 이용을 위한 프로필 정보를 입력해 주세요. 가입 후 마이페이지에서 언제든 수정할 수 있습니다.
              </p>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="ageGroup">연령대</Label>
                <select
                  id="ageGroup"
                  value={form.ageGroup}
                  onChange={(e) =>
                    setForm((p) => ({ ...p, ageGroup: e.target.value as AgeGroup }))
                  }
                  className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground"
                  aria-invalid={!!fieldError('ageGroup')}
                >
                  <option value="" disabled>
                    선택해 주세요
                  </option>
                  {ageGroupOptions.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
                {fieldError('ageGroup') && (
                  <p className="text-xs text-destructive">{fieldError('ageGroup')}</p>
                )}
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="region">지역</Label>
                <select
                  id="region"
                  value={form.region}
                  onChange={(e) => setForm((p) => ({ ...p, region: e.target.value as Region }))}
                  className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground"
                  aria-invalid={!!fieldError('region')}
                >
                  <option value="" disabled>
                    선택해 주세요
                  </option>
                  {regionOptions.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
                {fieldError('region') && (
                  <p className="text-xs text-destructive">{fieldError('region')}</p>
                )}
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="occupationStatus">직업 상태</Label>
                <select
                  id="occupationStatus"
                  value={form.occupationStatus}
                  onChange={(e) =>
                    setForm((p) => ({ ...p, occupationStatus: e.target.value as OccupationStatus }))
                  }
                  className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground"
                  aria-invalid={!!fieldError('occupationStatus')}
                >
                  <option value="" disabled>
                    선택해 주세요
                  </option>
                  {occupationOptions.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
                {fieldError('occupationStatus') && (
                  <p className="text-xs text-destructive">{fieldError('occupationStatus')}</p>
                )}
              </div>

              <div className="flex flex-col gap-1.5">
                <Label>관심 금융 분야</Label>
                <div className="flex flex-wrap gap-1.5">
                  {interestOptions.map((o) => {
                    const active = interests.has(o.value)
                    return (
                      <button
                        key={o.value}
                        type="button"
                        onClick={() => toggleInterest(o.value)}
                        className="focus:outline-none"
                      >
                        <Badge variant={active ? 'default' : 'outline'}>{o.label}</Badge>
                      </button>
                    )
                  })}
                </div>
              </div>

              <div className="flex gap-2 mt-2">
                <Button type="button" variant="outline" className="flex-1" onClick={() => setStep(0)}>
                  이전
                </Button>
                <Button type="submit" className="flex-1" disabled={loading}>
                  {loading ? (
                    <span className="flex items-center gap-2">
                      <span className="size-4 border-2 border-primary-foreground border-t-transparent rounded-full animate-spin" />
                      처리 중...
                    </span>
                  ) : (
                    <>
                      <UserPlus data-icon="inline-start" />
                      회원가입 완료
                    </>
                  )}
                </Button>
              </div>
            </form>
          )}
        </div>

        <p className="text-center text-sm text-muted-foreground mt-4">
          이미 계정이 있으신가요?{' '}
          <Link href="/login" className="text-primary font-medium hover:underline">
            로그인
          </Link>
        </p>
      </div>
    </div>
  )
}

function CheckboxField({
  id,
  checked,
  onChange,
  label,
  error,
}: {
  id: keyof FormState
  checked: boolean
  onChange: (checked: boolean) => void
  label: string
  error?: string
}) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="flex items-start gap-2 text-sm text-foreground">
        <input
          id={id}
          type="checkbox"
          checked={checked}
          onChange={(event) => onChange(event.target.checked)}
          className="mt-1 size-4 rounded border-border"
        />
        <span>{label}</span>
      </label>
      {error && <p className="pl-6 text-xs text-destructive">{error}</p>}
    </div>
  )
}
