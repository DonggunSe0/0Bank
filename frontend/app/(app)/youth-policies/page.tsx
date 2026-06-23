'use client'

import { useEffect, useState } from 'react'
import { CalendarDays, ExternalLink, MapPin } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { getYouthPolicies } from '@/lib/api/youth-policies'
import type { YouthPolicy } from '@/lib/types'

export default function YouthPoliciesPage() {
  const [policies, setPolicies] = useState<YouthPolicy[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    getYouthPolicies()
      .then(setPolicies)
      .catch(() => setError('청년정책 정보를 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="flex flex-col gap-5">
      <div>
        <h1 className="text-xl font-bold text-foreground">청년 정책</h1>
        <p className="text-sm text-muted-foreground mt-0.5">
          청년을 위한 정부 지원 정책을 확인하세요.
        </p>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

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
            <PolicyCard key={policy.id} policy={policy} />
          ))}
        </div>
      )}
    </div>
  )
}

function PolicyCard({ policy }: { policy: YouthPolicy }) {
  const ageLabel =
    policy.minAge || policy.maxAge
      ? `${policy.minAge ?? 0}세 ~ ${policy.maxAge ?? '제한 없음'}`
      : '연령 제한 없음'

  return (
    <Card className="border-border">
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
              {policy.regionCode}
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

        {policy.applyUrl && (
          <a
            href={policy.applyUrl}
            target="_blank"
            rel="noreferrer"
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
