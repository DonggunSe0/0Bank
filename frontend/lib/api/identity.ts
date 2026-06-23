import { apiFetch } from '../api'
import type { VerificationResponse } from '../types'

export async function uploadIdCardOcr(file: File): Promise<VerificationResponse> {
  const form = new FormData()
  form.append('idCardImage', file)
  return apiFetch<VerificationResponse>(
    '/api/v1/users/me/identity-verification/ocr',
    { method: 'POST', body: form, isFormData: true },
  )
}

export async function requestOneWon(
  accountNumber: string,
  organization: string,
): Promise<VerificationResponse> {
  return apiFetch<VerificationResponse>(
    '/api/v1/users/me/identity-verification/one-won',
    { method: 'POST', body: JSON.stringify({ accountNumber, organization }) },
  )
}

export async function verifyOneWon(code: string): Promise<VerificationResponse> {
  return apiFetch<VerificationResponse>(
    '/api/v1/users/me/identity-verification/one-won/verify',
    { method: 'POST', body: JSON.stringify({ code }) },
  )
}
