import { clearAccessToken, getAccessToken, setAccessToken } from './token'

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080'

const BASE_URL = API_BASE_URL

export class ApiRequestError extends Error {
  constructor(
    public code: string,
    message: string,
    public status: number,
    public details?: { field: string; reason: string }[],
  ) {
    super(message)
    this.name = 'ApiRequestError'
  }
}

let isRefreshing = false
let refreshPromise: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  const accessToken = getAccessToken()
  if (!accessToken) return null

  try {
    const res = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ accessToken }),
    })
    if (!res.ok) return null
    const data = await res.json()
    setAccessToken(data.accessToken)
    return data.accessToken
  } catch {
    return null
  }
}

export async function apiFetch<T>(
  path: string,
  options: RequestInit & { skipAuth?: boolean; isFormData?: boolean } = {},
): Promise<T> {
  const { skipAuth = false, isFormData = false, ...fetchOptions } = options

  const headers: Record<string, string> = {}
  if (!isFormData) headers['Content-Type'] = 'application/json'
  if (!skipAuth) {
    const token = getAccessToken()
    if (token) headers['Authorization'] = `Bearer ${token}`
  }

  const url = `${BASE_URL}${path}`

  let res = await fetch(url, {
    ...fetchOptions,
    credentials: 'include',
    headers: { ...headers, ...(fetchOptions.headers as Record<string, string>) },
  })

  // Try token refresh on 401
  if (res.status === 401 && !skipAuth) {
    if (!isRefreshing) {
      isRefreshing = true
      refreshPromise = refreshAccessToken().finally(() => {
        isRefreshing = false
        refreshPromise = null
      })
    }

    const newToken = await refreshPromise
    if (newToken) {
      headers['Authorization'] = `Bearer ${newToken}`
      res = await fetch(url, {
        ...fetchOptions,
        credentials: 'include',
        headers: { ...headers, ...(fetchOptions.headers as Record<string, string>) },
      })
    } else {
      clearAccessToken()
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('auth:logout'))
      }
      throw new ApiRequestError('UNAUTHORIZED', '로그인이 필요합니다.', 401)
    }
  }

  if (!res.ok) {
    let errorData: { code?: string; message?: string; details?: { field: string; reason: string }[] } = {}
    try {
      errorData = await res.json()
    } catch {
      errorData = { code: 'UNKNOWN', message: res.statusText }
    }
    throw new ApiRequestError(
      errorData.code ?? 'UNKNOWN',
      errorData.message ?? '오류가 발생했습니다.',
      res.status,
      errorData.details,
    )
  }

  // 204 No Content
  if (res.status === 204) return undefined as T

  return res.json() as Promise<T>
}
