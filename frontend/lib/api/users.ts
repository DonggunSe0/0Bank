import { apiFetch } from '../api'
import type { User } from '../types'

export async function getMe(): Promise<User> {
  return apiFetch<User>('/api/v1/users/me')
}
