'use client'

import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { ShieldAlert, ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { useAuth } from '@/contexts/AuthContext'
import { createProfile, getProfile, updateProfile } from '@/lib/api/profile'
import { ApiRequestError } from '@/lib/api'
import type {
  AgeGroup,
  FinancialInterest,
  OccupationStatus,
  Region,
  UserProfile,
} from '@/lib/types'

const ageGroupOptions: { value: AgeGroup; label: string }[] = [
  { value: 'TEENS', label: '10대' },
  { value: 'TWENTIES', label: '20대' },
  { value: 'THIRTIES', label: '30대' },
  { value: 'FORTIES', label: '40대' },
  { value: 'FIFTIES_PLUS', label: '50대 이상' },
]

const occupationOptions: { value: OccupationStatus; label: string }[] = [
  { value: 'EMPLOYED', label: '직장인' },
  { value: 'SELF_EMPLOYED', label: '자영업' },
  { value: 'STUDENT', label: '학생' },
  { value: 'FREELANCER', label: '프리랜서' },
  { value: 'UNEMPLOYED', label: '무직' },
  { value: 'ETC', label: '기타' },
]

const interestOptions: { value: FinancialInterest; label: string }[] = [
  { value: 'SAVINGS', label: '저축' },
  { value: 'INVESTMENT', label: '투자' },
  { value: 'LOAN', label: '대출' },
  { value: 'INSURANCE', label: '보험' },
  { value: 'PENSION', label: '연금' },
  { value: 'FOREIGN_EXCHANGE', label: '환전' },
]

const regionOptions: { value: Region; label: string }[] = [
  { value: 'SEOUL', label: '서울특별시' },
  { value: 'BUSAN', label: '부산광역시' },
  { value: 'DAEGU', label: '대구광역시' },
  { value: 'INCHEON', label: '인천광역시' },
  { value: 'GWANGJU', label: '광주광역시' },
  { value: 'DAEJEON', label: '대전광역시' },
  { value: 'ULSAN', label: '울산광역시' },
  { value: 'SEJONG', label: '세종특별자치시' },
  { value: 'GYEONGGI', label: '경기도' },
  { value: 'GANGWON', label: '강원특별자치도' },
  { value: 'CHUNGBUK', label: '충청북도' },
  { value: 'CHUNGNAM', label: '충청남도' },
  { value: 'JEONBUK', label: '전북특별자치도' },
  { value: 'JEONNAM', label: '전라남도' },
  { value: 'GYEONGBUK', label: '경상북도' },
  { value: 'GYEONGNAM', label: '경상남도' },
  { value: 'JEJU', label: '제주특별자치도' },
]

export default function MyPage() {
  const { user } = useAuth()
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const [ageGroup, setAgeGroup] = useState<AgeGroup | ''>('')
  const [region, setRegion] = useState<Region | ''>('')
  const [occupationStatus, setOccupationStatus] = useState<OccupationStatus | ''>('')
  const [interests, setInterests] = useState<Set<FinancialInterest>>(new Set())

  useEffect(() => {
    let mounted = true

    async function load() {
      try {
        const res = await getProfile()
        if (!mounted) return
        setProfile(res)
        setAgeGroup(res.ageGroup)
        setRegion(res.region)
        setOccupationStatus(res.occupationStatus)
        setInterests(new Set(res.financialInterests))
      } catch (err) {
        // 프로필 미등록은 정상 흐름(신규 등록 폼)이므로 에러로 표시하지 않는다.
        if (!(err instanceof ApiRequestError && err.code === 'PROFILE_NOT_FOUND')) {
          setError(
            err instanceof ApiRequestError ? err.message : '프로필을 불러오는 중 오류가 발생했습니다.',
          )
        }
      } finally {
        if (mounted) setLoading(false)
      }
    }

    load()
    return () => {
      mounted = false
    }
  }, [])

  function toggleInterest(value: FinancialInterest) {
    setInterests((prev) => {
      const next = new Set(prev)
      if (next.has(value)) next.delete(value)
      else next.add(value)
      return next
    })
  }

  async function handleSubmit() {
    if (!ageGroup || !region || !occupationStatus) {
      setError('연령대, 지역, 직업 상태를 모두 선택해 주세요.')
      return
    }
    setError('')
    setSaving(true)
    try {
      const body = {
        ageGroup,
        region,
        occupationStatus,
        financialInterests: Array.from(interests),
      }
      const res = profile ? await updateProfile(body) : await createProfile(body)
      setProfile(res)
      toast.success(profile ? '프로필이 수정되었습니다.' : '프로필이 등록되었습니다.')
    } catch (err) {
      if (err instanceof ApiRequestError) {
        setError(err.message)
      } else {
        setError('저장 중 오류가 발생했습니다.')
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex flex-col gap-5 max-w-md">
      <div>
        <h1 className="text-xl font-bold text-foreground">마이페이지</h1>
        <p className="text-sm text-muted-foreground mt-0.5">내 정보와 프로필을 확인하고 관리하세요.</p>
      </div>

      <Card className="border-border">
        <CardHeader className="pb-3">
          <CardTitle className="text-base">기본 정보</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-2">
          <div className="flex justify-between text-sm">
            <span className="text-muted-foreground">이름</span>
            <span className="text-foreground font-medium">{user?.name ?? '-'}</span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-muted-foreground">이메일</span>
            <span className="text-foreground font-medium">{user?.email ?? '-'}</span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-muted-foreground">휴대폰</span>
            <span className="text-foreground font-medium">{user?.phoneNumber ?? '-'}</span>
          </div>
          <div className="flex justify-between items-center text-sm">
            <span className="text-muted-foreground">본인인증</span>
            {user?.identityVerified ? (
              <Badge variant="secondary">
                <ShieldCheck data-icon="inline-start" />
                인증 완료
              </Badge>
            ) : (
              <Badge variant="outline">
                <ShieldAlert data-icon="inline-start" />
                미인증
              </Badge>
            )}
          </div>
        </CardContent>
      </Card>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <Card className="border-border">
        <CardHeader className="pb-3">
          <CardTitle className="text-base">{profile ? '프로필 수정' : '프로필 등록'}</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {loading ? (
            <p className="text-sm text-muted-foreground">불러오는 중...</p>
          ) : (
            <>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="ageGroup">연령대</Label>
                <select
                  id="ageGroup"
                  value={ageGroup}
                  onChange={(e) => setAgeGroup(e.target.value as AgeGroup)}
                  className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground"
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
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="region">지역</Label>
                <select
                  id="region"
                  value={region}
                  onChange={(e) => setRegion(e.target.value as Region)}
                  className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground"
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
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="occupationStatus">직업 상태</Label>
                <select
                  id="occupationStatus"
                  value={occupationStatus}
                  onChange={(e) => setOccupationStatus(e.target.value as OccupationStatus)}
                  className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground"
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

              <Button onClick={handleSubmit} disabled={saving} className="w-full">
                {saving ? '저장 중...' : profile ? '수정하기' : '등록하기'}
              </Button>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
