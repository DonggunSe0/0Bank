# API 명세서 - Banking MVP

## 1. 공통 규칙

- Base URL: `http://localhost:8080`
- Content-Type: `application/json;charset=UTF-8`
- 인증 필요 API는 `Authorization: Bearer {accessToken}` 헤더를 사용한다.
- 시간 포맷은 ISO-8601 문자열을 사용한다. 예: `2026-06-08T15:30:00`
- 날짜 포맷은 `YYYY-MM-DD`를 사용한다.
- 금액은 원화 정수(`Long`)로 전달한다.

## 2. 공통 응답/에러 포맷

### 2.1 성공 응답 권장 포맷

현재 `global.response.ApiResponse`가 비어 있으므로 MVP 구현 시 아래 포맷을 권장한다.

```json
{
  "success": true,
  "data": {},
  "message": null
}
```

단, 초기 구현에서는 컨트롤러가 DTO를 직접 반환해도 된다.

### 2.2 에러 응답 포맷

현재 `global.exception.ErrorResponse` 기준.

```json
{
  "code": "INVALID_INPUT_VALUE",
  "message": "입력값이 올바르지 않습니다.",
  "details": [
    {
      "field": "amount",
      "reason": "금액은 1원 이상이어야 합니다."
    }
  ]
}
```

## 3. 에러 코드 초안

| 코드 | HTTP Status | 메시지 | 설명 |
| --- | --- | --- | --- |
| INVALID_INPUT_VALUE | 400 | 입력값이 올바르지 않습니다. | 공통 validation 실패 |
| UNAUTHORIZED | 401 | 인증이 필요합니다. | 토큰 없음/만료/오류 |
| FORBIDDEN | 403 | 접근 권한이 없습니다. | 타인 리소스 접근 |
| USER_NOT_FOUND | 404 | 사용자를 찾을 수 없습니다. | 사용자 없음 |
| DUPLICATE_EMAIL | 409 | 이미 사용 중인 이메일입니다. | 회원가입 이메일 중복 |
| INVALID_CREDENTIALS | 401 | 이메일 또는 비밀번호가 올바르지 않습니다. | 로그인 실패 |
| IDENTITY_ALREADY_VERIFIED | 409 | 이미 본인인증이 완료되었습니다. | 중복 인증 요청 |
| IDENTITY_VERIFICATION_FAILED | 400 | 본인인증에 실패했습니다. | 인증 정보 불일치 |
| ACCOUNT_NOT_FOUND | 404 | 계좌를 찾을 수 없습니다. | 계좌 없음 |
| ACCOUNT_ACCESS_DENIED | 403 | 계좌 접근 권한이 없습니다. | 타인 계좌 접근 |
| ACCOUNT_NOT_ACTIVE | 409 | 활성 계좌가 아닙니다. | 정지/해지 계좌 |
| IDENTITY_VERIFICATION_REQUIRED | 403 | 본인인증이 필요합니다. | 계좌 개설/송금 전 인증 필요 |
| INSUFFICIENT_BALANCE | 409 | 잔액이 부족합니다. | 송금 잔액 부족 |
| TRANSFER_FAILED | 409 | 송금 처리에 실패했습니다. | 송금 처리 실패 |
| INVALID_PERIOD | 400 | 조회 기간이 올바르지 않습니다. | 시작일이 종료일보다 늦음 |
| INTERNAL_SERVER_ERROR | 500 | 서버 내부 오류가 발생했습니다. | 서버 오류 |

## 4. Auth/User API

## 4.1 회원가입

```http
POST /api/auth/signup
```

### Request Body

```json
{
  "email": "user@example.com",
  "password": "Password123!",
  "name": "홍길동",
  "phoneNumber": "01012345678",
  "birthDate": "1995-01-01"
}
```

### Validation

| 필드 | 규칙 |
| --- | --- |
| email | 필수, 이메일 형식, unique |
| password | 필수, 8자 이상 권장 |
| name | 필수 |
| phoneNumber | 필수, 숫자 10~11자리 권장 |
| birthDate | 필수, 과거 날짜 |

### Response `201 Created`

```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "phoneNumber": "01012345678",
  "birthDate": "1995-01-01",
  "identityVerified": false,
  "createdAt": "2026-06-08T15:30:00"
}
```

### Error

- `400 INVALID_INPUT_VALUE`
- `409 DUPLICATE_EMAIL`

## 4.2 로그인

```http
POST /api/auth/login
```

### Request Body

```json
{
  "email": "user@example.com",
  "password": "Password123!"
}
```

### Response `200 OK`

```json
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "user": {
    "id": 1,
    "email": "user@example.com",
    "name": "홍길동",
    "identityVerified": false
  }
}
```

### Error

- `400 INVALID_INPUT_VALUE`
- `401 INVALID_CREDENTIALS`

## 4.3 내 정보 조회

```http
GET /api/users/me
Authorization: Bearer {accessToken}
```

### Response `200 OK`

```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "phoneNumber": "01012345678",
  "birthDate": "1995-01-01",
  "identityVerified": true,
  "createdAt": "2026-06-08T15:30:00",
  "updatedAt": "2026-06-08T15:40:00"
}
```

### Error

- `401 UNAUTHORIZED`
- `404 USER_NOT_FOUND`

## 4.4 본인인증

```http
POST /api/users/me/identity-verification
Authorization: Bearer {accessToken}
```

### Request Body

```json
{
  "name": "홍길동",
  "phoneNumber": "01012345678",
  "birthDate": "1995-01-01",
  "verificationCode": "123456"
}
```

### Response `200 OK`

```json
{
  "userId": 1,
  "identityVerified": true,
  "verifiedAt": "2026-06-08T15:40:00"
}
```

### Error

- `400 INVALID_INPUT_VALUE`
- `400 IDENTITY_VERIFICATION_FAILED`
- `401 UNAUTHORIZED`
- `409 IDENTITY_ALREADY_VERIFIED`

## 5. Account API

## 5.1 계좌 개설

```http
POST /api/accounts
Authorization: Bearer {accessToken}
```

### Request Body

```json
{
  "nickname": "생활비 계좌",
  "accountType": "DEPOSIT"
}
```

### Response `201 Created`

```json
{
  "id": 1,
  "accountNumber": "100200300001",
  "nickname": "생활비 계좌",
  "accountType": "DEPOSIT",
  "balance": 0,
  "status": "ACTIVE",
  "createdAt": "2026-06-08T15:45:00"
}
```

### Error

- `400 INVALID_INPUT_VALUE`
- `401 UNAUTHORIZED`
- `403 IDENTITY_VERIFICATION_REQUIRED`

## 5.2 내 계좌 목록 조회

```http
GET /api/accounts
Authorization: Bearer {accessToken}
```

### Response `200 OK`

```json
[
  {
    "id": 1,
    "accountNumber": "100200300001",
    "nickname": "생활비 계좌",
    "balance": 150000,
    "status": "ACTIVE",
    "createdAt": "2026-06-08T15:45:00"
  }
]
```

### Error

- `401 UNAUTHORIZED`

## 5.3 내 계좌 상세 조회

```http
GET /api/accounts/{accountId}
Authorization: Bearer {accessToken}
```

### Path Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| accountId | Long | Y | 계좌 ID |

### Response `200 OK`

```json
{
  "id": 1,
  "accountNumber": "100200300001",
  "nickname": "생활비 계좌",
  "accountType": "DEPOSIT",
  "balance": 150000,
  "status": "ACTIVE",
  "createdAt": "2026-06-08T15:45:00",
  "updatedAt": "2026-06-08T16:00:00"
}
```

### Error

- `401 UNAUTHORIZED`
- `403 ACCOUNT_ACCESS_DENIED`
- `404 ACCOUNT_NOT_FOUND`

## 6. Transfer API

## 6.1 입금

```http
POST /api/transfers/deposit
Authorization: Bearer {accessToken}
```

### Request Body

```json
{
  "accountId": 1,
  "amount": 100000,
  "memo": "초기 입금"
}
```

### Validation

| 필드 | 규칙 |
| --- | --- |
| accountId | 필수 |
| amount | 필수, 1 이상 |
| memo | 선택, 100자 이하 권장 |

### Response `200 OK`

```json
{
  "transactionId": 1,
  "accountId": 1,
  "type": "DEPOSIT",
  "amount": 100000,
  "balanceAfter": 100000,
  "memo": "초기 입금",
  "transactedAt": "2026-06-08T16:00:00"
}
```

### Error

- `400 INVALID_INPUT_VALUE`
- `401 UNAUTHORIZED`
- `403 ACCOUNT_ACCESS_DENIED`
- `404 ACCOUNT_NOT_FOUND`
- `409 ACCOUNT_NOT_ACTIVE`

## 6.2 계좌 간 송금

```http
POST /api/transfers
Authorization: Bearer {accessToken}
```

### Request Body

```json
{
  "senderAccountId": 1,
  "receiverAccountNumber": "100200300002",
  "amount": 50000,
  "memo": "점심값"
}
```

### Validation

| 필드 | 규칙 |
| --- | --- |
| senderAccountId | 필수 |
| receiverAccountNumber | 필수 |
| amount | 필수, 1 이상 |
| memo | 선택, 100자 이하 권장 |

### Response `200 OK`

```json
{
  "transferId": 1,
  "status": "SUCCESS",
  "senderAccountId": 1,
  "senderAccountNumber": "100200300001",
  "receiverAccountNumber": "100200300002",
  "amount": 50000,
  "senderBalanceAfter": 50000,
  "memo": "점심값",
  "transferredAt": "2026-06-08T16:10:00"
}
```

### Error

- `400 INVALID_INPUT_VALUE`
- `401 UNAUTHORIZED`
- `403 ACCOUNT_ACCESS_DENIED`
- `404 ACCOUNT_NOT_FOUND`
- `409 ACCOUNT_NOT_ACTIVE`
- `409 INSUFFICIENT_BALANCE`
- `409 TRANSFER_FAILED`

## 7. Transaction API

## 7.1 거래내역 조회

```http
GET /api/accounts/{accountId}/transactions
Authorization: Bearer {accessToken}
```

### Path Parameters

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| accountId | Long | Y | 계좌 ID |

### Query Parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| startDate | LocalDate | N | 없음 | 조회 시작일, `YYYY-MM-DD` |
| endDate | LocalDate | N | 없음 | 조회 종료일, `YYYY-MM-DD` |
| type | TransactionType | N | 없음 | `DEPOSIT`, `TRANSFER_IN`, `TRANSFER_OUT` |
| direction | String | N | 없음 | `IN`, `OUT` |
| minAmount | Long | N | 없음 | 최소 금액 |
| maxAmount | Long | N | 없음 | 최대 금액 |
| page | Integer | N | 0 | 페이지 번호 |
| size | Integer | N | 20 | 페이지 크기 |
| sort | String | N | `transactedAt,desc` | 정렬 |

### Example Request

```http
GET /api/accounts/1/transactions?startDate=2026-06-01&endDate=2026-06-08&type=TRANSFER_OUT&page=0&size=20
Authorization: Bearer {accessToken}
```

### Response `200 OK`

```json
{
  "content": [
    {
      "id": 3,
      "accountId": 1,
      "type": "TRANSFER_OUT",
      "direction": "OUT",
      "amount": 50000,
      "balanceAfter": 50000,
      "relatedAccountNumber": "100200300002",
      "memo": "점심값",
      "transactedAt": "2026-06-08T16:10:00"
    },
    {
      "id": 1,
      "accountId": 1,
      "type": "DEPOSIT",
      "direction": "IN",
      "amount": 100000,
      "balanceAfter": 100000,
      "relatedAccountNumber": null,
      "memo": "초기 입금",
      "transactedAt": "2026-06-08T16:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

### Error

- `400 INVALID_INPUT_VALUE`
- `400 INVALID_PERIOD`
- `401 UNAUTHORIZED`
- `403 ACCOUNT_ACCESS_DENIED`
- `404 ACCOUNT_NOT_FOUND`

## 8. 구현 순서 권장안

1. 공통 예외/응답 정리
2. User/Auth: 회원가입, 로그인, 내 정보 조회, 본인인증
3. Account: 계좌 개설, 목록/상세 조회
4. Transfer: 입금, 송금, 잔액 갱신 트랜잭션
5. Transaction: 거래내역 저장/조회/필터링
6. Swagger 문서화와 통합 테스트

## 9. Swagger 태그 권장

| 태그 | 설명 |
| --- | --- |
| Auth | 회원가입/로그인 |
| User | 내 정보/본인인증 |
| Account | 계좌 개설/조회 |
| Transfer | 입금/송금 |
| Transaction | 거래내역 조회 |
