'use client'

import { useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { CalendarDays, ExternalLink, MapPin, Search, Sparkles } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { useAuth } from '@/contexts/AuthContext'
import {
  getYouthPolicies,
  recommendYouthPolicies,
  searchYouthPolicies,
} from '@/lib/api/youth-policies'
import { getMyProfile } from '@/lib/api/users'
import type { RecommendedYouthPolicy } from '@/lib/api/youth-policies'
import type { YouthPolicy } from '@/lib/types'

const regionOptions = [
  { value: '', label: '전체' },
  { value: '전국', label: '전국' },
  { value: '서울', label: '서울' },
  { value: '부산', label: '부산' },
  { value: '대구', label: '대구' },
  { value: '인천', label: '인천' },
  { value: '광주', label: '광주' },
  { value: '대전', label: '대전' },
  { value: '울산', label: '울산' },
  { value: '세종', label: '세종' },
  { value: '경기', label: '경기' },
  { value: '강원', label: '강원' },
  { value: '충북', label: '충북' },
  { value: '충남', label: '충남' },
  { value: '전북', label: '전북' },
  { value: '전남', label: '전남' },
  { value: '경북', label: '경북' },
  { value: '경남', label: '경남' },
  { value: '제주', label: '제주' },
]

const regionCodeLabels: Record<string, string> = {
  '003002001': '전국',
  '3001': '전국',
  '11': '서울',
  '26': '부산',
  '27': '대구',
  '28': '인천',
  '29': '광주',
  '30': '대전',
  '31': '울산',
  '36': '세종',
  '41': '경기',
  '42': '강원',
  '51': '강원',
  '43': '충북',
  '44': '충남',
  '45': '전북',
  '52': '전북',
  '46': '전남',
  '47': '경북',
  '48': '경남',
  '49': '제주',
  '50': '제주',
}

const defaultCategoryOptions = [
  '',
  '일자리',
  '주거',
  '주거지원',
  '교육',
  '금융/복지/문화',
  '금융･복지･문화',
  '문화',
  '복지',
  '참여･기반',
]

type RecommendStatus = 'idle' | 'success' | 'empty'

export default function YouthPoliciesPage() {
  const { user } = useAuth()
  const router = useRouter()
  const [policies, setPolicies] = useState<YouthPolicy[]>([])
  const [recommendedPolicies, setRecommendedPolicies] = useState<RecommendedYouthPolicy[]>([])
  const [loading, setLoading] = useState(true)
  const [searching, setSearching] = useState(false)
  const [recommending, setRecommending] = useState(false)
  const [error, setError] = useState('')
  const [age, setAge] = useState('')
  const [region, setRegion] = useState('')
  const [category, setCategory] = useState('')
  const [keyword, setKeyword] = useState('')
  const [query, setQuery] = useState('')
  const [userRegion, setUserRegion] = useState('')
  const [searched, setSearched] = useState(false)
  const [recommendStatus, setRecommendStatus] = useState<RecommendStatus>('idle')
  const recommendResultRef = useRef<HTMLDivElement>(null)
  const recommendRequestSeq = useRef(0)
  const userAge = calculateAge(user?.birthDate)
  const categoryOptions = Array.from(
    new Set([
      ...defaultCategoryOptions,
      ...policies.map((policy) => policy.category).filter((value): value is string => Boolean(value)),
      ...recommendedPolicies.map((policy) => policy.category).filter((value): value is string => Boolean(value)),
    ]),
  )

  useEffect(() => {
    getYouthPolicies()
      .then(setPolicies)
      .catch(() => setError('청년정책 정보를 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (!user) return

    getMyProfile()
      .then((profile) => setUserRegion(profile.region?.trim() ?? ''))
      .catch(() => setUserRegion(''))
  }, [user])

  async function handleSearch(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setSearching(true)
    try {
      const result = await searchYouthPolicies({
        age: parseNumber(age),
        region,
        category,
        keyword,
        size: 30,
      })
      setPolicies(result.content)
      setRecommendedPolicies([])
      setRecommendStatus('idle')
      recommendRequestSeq.current += 1
      setSearched(true)
    } catch {
      setError('청년정책 검색에 실패했습니다.')
    } finally {
      setSearching(false)
    }
  }

  async function handleRecommend(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    if (!query.trim()) {
      setError('맞춤 추천을 받으려면 고민이나 관심사를 입력해 주세요.')
      return
    }

    setRecommending(true)
    setRecommendStatus('idle')
    setRecommendedPolicies([])
    const requestSeq = recommendRequestSeq.current + 1
    recommendRequestSeq.current = requestSeq
    try {
      const effectiveAge = parseNumber(age) ?? userAge
      const effectiveRegion = region || userRegion
      const result = await recommendYouthPolicies({
        age: effectiveAge,
        region: effectiveRegion,
        category,
        query: query.trim(),
      })
      if (recommendRequestSeq.current !== requestSeq) return

      const nextRecommendedPolicies = Array.isArray(result.recommendedPolicies)
        ? result.recommendedPolicies
        : []
      setRecommendedPolicies(nextRecommendedPolicies)
      setRecommendStatus(nextRecommendedPolicies.length > 0 ? 'success' : 'empty')
    } catch {
      if (recommendRequestSeq.current !== requestSeq) return

      setError('맞춤 정책 추천에 실패했습니다.')
      setRecommendStatus('idle')
    } finally {
      if (recommendRequestSeq.current === requestSeq) {
        setRecommending(false)
      }
    }
  }

  useEffect(() => {
    if (recommendStatus === 'idle') return
    recommendResultRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, [recommendStatus])

  async function resetFilters() {
    setError('')
    setAge('')
    setRegion('')
    setCategory('')
    setKeyword('')
    setSearched(false)
    setRecommendedPolicies([])
    setRecommendStatus('idle')
    recommendRequestSeq.current += 1
    setLoading(true)
    try {
      const result = await getYouthPolicies()
      setPolicies(result)
    } catch {
      setError('청년정책 정보를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h1 className="text-xl font-bold text-foreground">청년 정책</h1>
        <p className="text-sm text-muted-foreground mt-0.5">
          청년을 위한 정부 지원 정책을 확인하세요.
        </p>
      </div>

      <Card className="border-border">
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-medium text-muted-foreground">정책 찾기</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSearch} className="flex flex-col gap-3">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="policy-keyword">정책 검색</Label>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  id="policy-keyword"
                  placeholder="정책명, 설명, 키워드로 검색"
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  className="pl-9"
                />
              </div>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="policy-age">나이</Label>
                <Input
                  id="policy-age"
                  inputMode="numeric"
                  placeholder="예: 25"
                  value={age}
                  onChange={(e) => setAge(e.target.value.replace(/\D/g, ''))}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="policy-region">지역</Label>
                <select
                  id="policy-region"
                  value={region}
                  onChange={(e) => setRegion(e.target.value)}
                  className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
                >
                  {regionOptions.map((option) => (
                    <option key={option.value || 'all'} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="policy-category">카테고리</Label>
                <select
                  id="policy-category"
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
                >
                  {categoryOptions.map((option) => (
                    <option key={option || 'all'} value={option}>
                      {option || '전체'}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={resetFilters} disabled={loading || searching}>
                초기화
              </Button>
              <Button type="submit" disabled={searching}>
                <Search data-icon="inline-start" />
                {searching ? '검색 중...' : '검색'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card className="border-border">
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-medium text-muted-foreground">맞춤 추천</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleRecommend} className="flex flex-col gap-3">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="policy-query">고민이나 관심사</Label>
              <Textarea
                id="policy-query"
                placeholder={userAge ? `예: 만 ${userAge}세 기준으로 자취 월세 부담을 줄일 정책을 찾고 싶어요.` : '예: 대학생인데 자취 월세 부담이 커서 주거비 지원을 받고 싶어요.'}
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
              {userAge && (
                <p className="text-xs text-muted-foreground">
                  내 정보 기준 만 {userAge}세{userRegion ? `, ${userRegion}` : ''}가 추천 조건에 반영됩니다.
                </p>
              )}
              {!userAge && userRegion && (
                <p className="text-xs text-muted-foreground">
                  내 정보 기준 {userRegion} 지역이 추천 조건에 반영됩니다.
                </p>
              )}
            </div>
            <div className="flex justify-end">
              <Button type="submit" disabled={recommending}>
                <Sparkles data-icon="inline-start" />
                {recommending ? '추천 중...' : '맞춤 정책 추천'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {recommendStatus === 'success' && (
        <div ref={recommendResultRef} className="flex flex-col gap-3 scroll-mt-20">
          <div>
            <h2 className="text-base font-semibold text-foreground">추천 정책</h2>
            <p className="text-xs text-muted-foreground mt-0.5">입력한 조건과 고민을 기준으로 추천된 정책입니다.</p>
          </div>
          {recommendedPolicies.map((policy) => (
            <PolicyCard
              key={`recommended-${policy.id}`}
              policy={policy}
              onOpen={() => router.push(`/youth-policies/${policy.id}`)}
            />
          ))}
        </div>
      )}

      {recommendStatus === 'empty' && (
        <Card ref={recommendResultRef} className="border-border scroll-mt-20">
          <CardContent className="py-8 text-center">
            <p className="text-sm font-medium text-foreground">추천 가능한 정책이 없습니다.</p>
            <p className="mt-1 text-xs text-muted-foreground">
              나이, 지역, 카테고리 조건을 줄이거나 고민 내용을 다른 키워드로 입력해 주세요.
            </p>
          </CardContent>
        </Card>
      )}

      <div>
        <h2 className="text-base font-semibold text-foreground">
          {searched ? '검색 결과' : '정책 목록'}
        </h2>
        <p className="text-xs text-muted-foreground mt-0.5">
          {loading ? '' : `${policies.length}개 정책`}
        </p>
      </div>

      {loading ? (
        <div className="flex flex-col gap-3">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-36 w-full rounded-lg" />
          ))}
        </div>
      ) : policies.length === 0 ? (
        <Card className="border-border">
          <CardContent className="py-12 text-center">
            <p className="text-sm text-muted-foreground">조회된 정책 정보가 없습니다.</p>
          </CardContent>
        </Card>
      ) : (
        <div className="flex flex-col gap-3">
          {policies.map((policy) => (
            <PolicyCard
              key={policy.id}
              policy={policy}
              onOpen={() => router.push(`/youth-policies/${policy.id}`)}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function PolicyCard({
  policy,
  onOpen,
}: {
  policy: YouthPolicy | RecommendedYouthPolicy
  onOpen: () => void
}) {
  const ageLabel =
    policy.minAge || policy.maxAge
      ? `${policy.minAge ?? 0}세 ~ ${policy.maxAge ?? '제한 없음'}`
      : '연령 제한 없음'

  return (
    <Card
      role="button"
      tabIndex={0}
      onClick={onOpen}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onOpen()
        }
      }}
      className="border-border cursor-pointer transition-colors hover:bg-accent/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40"
    >
      <CardHeader className="pb-2">
        <div className="flex items-start justify-between gap-3">
          <CardTitle className="text-sm font-semibold text-foreground leading-relaxed">
            {policy.title}
          </CardTitle>
          {policy.category && (
            <Badge variant="outline" className="text-xs shrink-0">
              {policy.category}
            </Badge>
          )}
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
          {policy.subCategory && <Badge variant="secondary">{policy.subCategory}</Badge>}
          <span>{ageLabel}</span>
          {policy.regionCode && (
            <span className="inline-flex items-center gap-1">
              <MapPin className="size-3" />
              {formatRegion(policy.regionCode)}
            </span>
          )}
          {policy.applyPeriod && (
            <span className="inline-flex items-center gap-1">
              <CalendarDays className="size-3" />
              {policy.applyPeriod}
            </span>
          )}
        </div>

        {policy.description && (
          <p className="text-sm text-muted-foreground leading-relaxed line-clamp-3">
            {policy.description}
          </p>
        )}

        {'recommendReason' in policy && policy.recommendReason && (
          <div className="rounded-md border border-primary/20 bg-primary/5 px-3 py-2">
            <p className="text-xs font-medium text-primary mb-1">추천 이유</p>
            <p className="text-sm text-foreground leading-relaxed">{policy.recommendReason}</p>
          </div>
        )}

        {policy.applyUrl && (
          <a
            href={policy.applyUrl}
            target="_blank"
            rel="noreferrer"
            onClick={(event) => event.stopPropagation()}
            className="inline-flex items-center gap-1 text-sm font-medium text-primary"
          >
            신청 페이지
            <ExternalLink className="size-3.5" />
          </a>
        )}
      </CardContent>
    </Card>
  )
}

function parseNumber(value: string): number | undefined {
  if (!value.trim()) return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

function calculateAge(birthDate?: string): number | undefined {
  if (!birthDate) return undefined
  const birth = new Date(birthDate)
  if (Number.isNaN(birth.getTime())) return undefined

  const today = new Date()
  let age = today.getFullYear() - birth.getFullYear()
  const monthDiff = today.getMonth() - birth.getMonth()
  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birth.getDate())) {
    age -= 1
  }
  return age >= 0 ? age : undefined
}

function formatRegion(regionCode: string): string {
  const parts = regionCode
    .split(/[,\s/|]+/)
    .map((part) => part.trim())
    .filter(Boolean)

  const labels = parts.map((part) => {
    if (regionCodeLabels[part]) return regionCodeLabels[part]
    if (/^\d{5}$/.test(part)) {
      return regionCodeLabels[part.slice(0, 2)] ?? part
    }
    return part
  })
  const uniqueLabels = Array.from(new Set(labels))

  if (uniqueLabels.length >= 10) {
    return '전국'
  }
  if (uniqueLabels.length > 8) {
    return `${uniqueLabels.slice(0, 8).join(', ')} 외 ${uniqueLabels.length - 8}개 지역`
  }
  return uniqueLabels.join(', ')
}
