import { apiFetch } from '../api'
import type { AuthResponse, User } from '../types'

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
