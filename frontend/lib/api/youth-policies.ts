import { apiFetch } from '../api'
import type { PageResponse, YouthPolicy } from '../types'

export async function getYouthPolicies(): Promise<YouthPolicy[]> {
  return apiFetch<YouthPolicy[]>('/api/v1/youth-policies')
}

export async function getYouthPolicy(id: string | number): Promise<YouthPolicy> {
  return apiFetch<YouthPolicy>(`/api/v1/youth-policies/${id}`)
}

export interface YouthPolicySearchParams {
  age?: number
  region?: string
  category?: string
  keyword?: string
  page?: number
  size?: number
}

export interface YouthPolicyRecommendRequest {
  age?: number
  region?: string
  category?: string
  query: string
}

export interface RecommendedYouthPolicy extends YouthPolicy {
  recommendReason: string
}

export interface YouthPolicyRecommendResponse {
  recommendedPolicies: RecommendedYouthPolicy[]
}

export async function searchYouthPolicies(
  params: YouthPolicySearchParams,
): Promise<PageResponse<YouthPolicy>> {
  const searchParams = new URLSearchParams()
  if (params.age !== undefined) searchParams.set('age', String(params.age))
  if (params.region?.trim()) searchParams.set('region', params.region.trim())
  if (params.category?.trim()) searchParams.set('category', params.category.trim())
  if (params.keyword?.trim()) searchParams.set('keyword', params.keyword.trim())
  searchParams.set('page', String(params.page ?? 0))
  searchParams.set('size', String(params.size ?? 20))

  return apiFetch<PageResponse<YouthPolicy>>(
    `/api/v1/youth-policies/search?${searchParams.toString()}`,
  )
}

export async function recommendYouthPolicies(
  data: YouthPolicyRecommendRequest,
): Promise<YouthPolicyRecommendResponse> {
  return apiFetch<YouthPolicyRecommendResponse>('/api/v1/youth-policies/recommend', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}
