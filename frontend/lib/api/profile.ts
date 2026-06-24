import { apiFetch } from '../api'
import type { UserProfile, UserProfileRequest } from '../types'

export async function getProfile(): Promise<UserProfile> {
  return apiFetch<UserProfile>('/api/v1/users/me/profile')
}

export async function createProfile(data: UserProfileRequest): Promise<UserProfile> {
  return apiFetch<UserProfile>('/api/v1/users/me/profile', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function updateProfile(data: UserProfileRequest): Promise<UserProfile> {
  return apiFetch<UserProfile>('/api/v1/users/me/profile', {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}
