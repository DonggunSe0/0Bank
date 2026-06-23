import { apiFetch } from '../api'
import type { YouthPolicy } from '../types'

export async function getYouthPolicies(): Promise<YouthPolicy[]> {
  return apiFetch<YouthPolicy[]>('/api/v1/youth-policies')
}

export async function getYouthPolicy(id: string | number): Promise<YouthPolicy> {
  return apiFetch<YouthPolicy>(`/api/v1/youth-policies/${id}`)
}
