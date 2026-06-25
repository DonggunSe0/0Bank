import { apiFetch } from '../api'
import type { User } from '../types'

export async function getMe(): Promise<User> {
  return apiFetch<User>('/api/v1/users/me')
}

export interface UserProfile {
  userId: number
  ageGroup?: string
  region?: string
  occupationStatus?: string
  financialInterests?: string[]
}

export async function getMyProfile(): Promise<UserProfile> {
  return apiFetch<UserProfile>('/api/v1/users/me/profile')
}
