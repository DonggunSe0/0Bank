import { apiFetch } from '../api'
import type { AgeGroup, AuthResponse, FinancialInterest, OccupationStatus, Region, User } from '../types'

export async function login(email: string, password: string): Promise<AuthResponse> {
  return apiFetch<AuthResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
    skipAuth: true,
  })
}

export async function signup(data: {
  identityVerificationId: string
  email: string
  password: string
  name: string
  phoneNumber: string
  birthDate: string
  // 본인인증 다음 단계(프로필 설정)에서 함께 수집 — 마이페이지 프로필과 동일한 필드
  ageGroup: AgeGroup
  region: Region
  occupationStatus: OccupationStatus
  financialInterests: FinancialInterest[]
  agreedServiceTerms: boolean
  agreedPersonalInfo: boolean
  agreedFinancialInfo: boolean
  agreedMarketing?: boolean
}): Promise<User> {
  return apiFetch<User>('/api/v1/auth/signup', {
    method: 'POST',
    body: JSON.stringify(data),
    skipAuth: true,
  })
}

export async function logout(): Promise<void> {
  return apiFetch<void>('/api/v1/auth/logout', {
    method: 'POST',
  })
}
