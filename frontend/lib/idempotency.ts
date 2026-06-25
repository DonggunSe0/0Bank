/**
 * 멱등성 키 생성. 백엔드 Idempotency-Key 헤더 형식(A-Za-z0-9._:-, 최대 100자)을 따릅니다.
 */
export function createIdempotencyKey(prefix?: string): string {
  const id =
    typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`
  return prefix ? `${prefix}-${id}` : id
}
