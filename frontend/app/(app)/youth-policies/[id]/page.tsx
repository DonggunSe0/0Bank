'use client'

import { useEffect, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { ArrowLeft, CalendarDays, ExternalLink, MapPin } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { getYouthPolicy } from '@/lib/api/youth-policies'
import type { YouthPolicy } from '@/lib/types'

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

export default function YouthPolicyDetailPage() {
  const { id } = useParams<{ id: string }>()
  const router = useRouter()
  const [policy, setPolicy] = useState<YouthPolicy | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function load() {
      try {
        const result = await getYouthPolicy(id)
        setPolicy(result)
      } catch {
        setPolicy(null)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [id])

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
          <Skeleton className="h-44 w-full rounded-lg" />
          <Skeleton className="h-40 w-full rounded-lg" />
          <Skeleton className="h-28 w-full rounded-lg" />
        </div>
      ) : !policy ? (
        <Card className="border-border">
          <CardContent className="py-12 text-center">
            <p className="text-sm text-muted-foreground">정책 정보를 찾을 수 없습니다.</p>
          </CardContent>
        </Card>
      ) : (
        <>
          <Card className="border-border">
            <CardHeader className="pb-4">
              <div className="flex items-start justify-between gap-3">
                <div className="flex max-w-3xl flex-col gap-2.5">
                  <CardTitle className="text-xl font-bold leading-snug text-foreground">
                    {policy.title}
                  </CardTitle>
                  <div className="flex flex-wrap gap-2">
                    {policy.category && <Badge variant="outline">{policy.category}</Badge>}
                    {policy.subCategory && <Badge variant="secondary">{policy.subCategory}</Badge>}
                  </div>
                </div>
              </div>
            </CardHeader>
            <CardContent className="flex flex-col gap-5">
              <div className="flex flex-wrap gap-2 text-sm text-muted-foreground">
                <span className="rounded-md bg-muted px-2 py-1">{formatAge(policy)}</span>
                {policy.regionCode && (
                  <span className="inline-flex items-center gap-1 rounded-md bg-muted px-2 py-1">
                    <MapPin className="size-4" />
                    {formatRegion(policy.regionCode)}
                  </span>
                )}
                {policy.applyPeriod && (
                  <span className="inline-flex items-center gap-1 rounded-md bg-muted px-2 py-1">
                    <CalendarDays className="size-4" />
                    {policy.applyPeriod}
                  </span>
                )}
              </div>

              {policy.description && (
                <p className="max-w-3xl whitespace-pre-line text-[15px] leading-7 text-foreground/80">
                  {policy.description}
                </p>
              )}
            </CardContent>
          </Card>

          <Card className="border-border">
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-medium text-muted-foreground">신청 정보</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col divide-y divide-border/70">
              <InfoRow label="신청 기간" value={policy.applyPeriod} />
              <InfoRow label="신청 방법" value={policy.applyMethod} />
              <InfoRow label="정책 번호" value={policy.policyId} />
              {policy.applyUrl && (
                <a
                  href={policy.applyUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="mt-1 inline-flex h-8 w-full shrink-0 items-center justify-center gap-1.5 rounded-lg bg-primary px-2.5 text-sm font-medium text-primary-foreground transition-all hover:bg-primary/80 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/50 sm:w-fit"
                >
                  신청 페이지
                  <ExternalLink data-icon="inline-end" />
                </a>
              )}
            </CardContent>
          </Card>

          <Card className="border-border">
            <CardHeader className="pb-3">
              <CardTitle className="text-sm font-medium text-muted-foreground">지원 조건</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col divide-y divide-border/70">
              <InfoRow label="연령" value={formatAge(policy)} />
              <InfoRow label="지역" value={policy.regionCode ? formatRegion(policy.regionCode) : undefined} />
              <InfoRow label="취업 요건" value={policy.jobCode} />
            </CardContent>
          </Card>
        </>
      )}
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value?: string }) {
  return (
    <div className="flex flex-col gap-1 py-3 first:pt-0 last:pb-0 sm:flex-row sm:items-start sm:justify-between sm:gap-6">
      <span className="shrink-0 text-sm text-muted-foreground sm:w-24">{label}</span>
      <span className="max-w-3xl text-sm font-medium leading-6 text-foreground sm:text-right">
        {value?.trim() || '-'}
      </span>
    </div>
  )
}

function formatAge(policy: YouthPolicy): string {
  if (!policy.minAge && !policy.maxAge) return '연령 제한 없음'
  return `${policy.minAge ?? 0}세 ~ ${policy.maxAge ?? '제한 없음'}`
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
