# API 명세서 - 표 형태 요약본

## 1. 공통 정보

| 항목 | 값 |
| --- | --- |
| Base URL | `http://localhost:8080` |
| Content-Type | `application/json;charset=UTF-8` |
| 인증 헤더 | `Authorization: Bearer {accessToken}` |
| 날짜 포맷 | `YYYY-MM-DD` |
| 시간 포맷 | ISO-8601, 예: `2026-06-08T15:30:00` |
| 금액 단위 | 원화 정수, `Long` |

## 2. API 전체 목록

| 도메인 | 기능 | Method | URL | 인증 | 주요 Request | 주요 Response |
| --- | --- | --- | --- | --- | --- | --- |
| Auth | 회원가입 | POST | `/api/auth/signup` | N | email, password, name, phoneNumber, birthDate | userId, email, name, identityVerified |
| Auth | 로그인 | POST | `/api/auth/login` | N | email, password | accessToken, tokenType, user |
| User | 내 정보 조회 | GET | `/api/users/me` | Y | - | id, email, name, phoneNumber, birthDate, identityVerified |
| User | 본인인증 | POST | `/api/users/me/identity-verification` | Y | name, phoneNumber, birthDate, verificationCode | userId, identityVerified, verifiedAt |
| Account | 계좌 개설 | POST | `/api/accounts` | Y | nickname, accountType | accountId, accountNumber, balance, status |
| Account | 내 계좌 목록 조회 | GET | `/api/accounts` | Y | - | account list |
| Account | 내 계좌 상세 조회 | GET | `/api/accounts/{accountId}` | Y | accountId | account detail |
| Transfer | 입금 | POST | `/api/transfers/deposit` | Y | accountId, amount, memo | transactionId, balanceAfter |
| Transfer | 계좌 간 송금 | POST | `/api/transfers` | Y | senderAccountId, receiverAccountNumber, amount, memo | transferId, status, senderBalanceAfter |
| Transaction | 거래내역 조회 | GET | `/api/accounts/{accountId}/transactions` | Y | accountId, filters, page, size | transaction page |

## 3. Auth API

### 3.1 회원가입

| 항목 | 내용 |
| --- | --- |
| Method | POST |
| URL | `/api/auth/signup` |
| 인증 | 불필요 |
| 설명 | 신규 사용자를 생성한다. |
| 성공 Status | `201 Created` |
| 주요 Error | `400 INVALID_INPUT_VALUE`, `409 DUPLICATE_EMAIL` |

#### Request Body

| 필드 | 타입 | 필수 | 검증/설명 | 예시 |
| --- | --- | --- | --- | --- |
| email | String | Y | 이메일 형식, unique | `user@example.com` |
| password | String | Y | 8자 이상 권장 | `Password123!` |
| name | String | Y | 사용자 이름 | `홍길동` |
| phoneNumber | String | Y | 숫자 10~11자리 권장 | `01012345678` |
| birthDate | LocalDate | Y | 과거 날짜 | `1995-01-01` |

#### Response Body

| 필드 | 타입 | 설명 | 예시 |
| --- | --- | --- | --- |
| id | Long | 사용자 ID | `1` |
| email | String | 이메일 | `user@example.com` |
| name | String | 이름 | `홍길동` |
| phoneNumber | String | 휴대폰 번호 | `01012345678` |
| birthDate | LocalDate | 생년월일 | `1995-01-01` |
| identityVerified | Boolean | 본인인증 여부 | `false` |
| createdAt | LocalDateTime | 생성일시 | `2026-06-08T15:30:00` |

### 3.2 로그인

| 항목 | 내용 |
| --- | --- |
| Method | POST |
| URL | `/api/auth/login` |
| 인증 | 불필요 |
| 설명 | 이메일/비밀번호로 로그인하고 토큰을 발급한다. |
| 성공 Status | `200 OK` |
| 주요 Error | `400 INVALID_INPUT_VALUE`, `401 INVALID_CREDENTIALS` |

#### Request Body

| 필드 | 타입 | 필수 | 검증/설명 | 예시 |
| --- | --- | --- | --- | --- |
| email | String | Y | 가입 이메일 | `user@example.com` |
| password | String | Y | 비밀번호 | `Password123!` |

#### Response Body

| 필드 | 타입 | 설명 | 예시 |
| --- | --- | --- | --- |
| accessToken | String | API 인증 토큰 | `eyJhbGciOi...` |
| tokenType | String | 토큰 타입 | `Bearer` |
| user.id | Long | 사용자 ID | `1` |
| user.email | String | 이메일 | `user@example.com` |
| user.name | String | 이름 | `홍길동` |
| user.identityVerified | Boolean | 본인인증 여부 | `false` |

## 4. User API

### 4.1 내 정보 조회

| 항목 | 내용 |
| --- | --- |
| Method | GET |
| URL | `/api/users/me` |
| 인증 | 필요 |
| 설명 | 로그인한 사용자의 정보를 조회한다. |
| 성공 Status | `200 OK` |
| 주요 Error | `401 UNAUTHORIZED`, `404 USER_NOT_FOUND` |

#### Response Body

| 필드 | 타입 | 설명 | 예시 |
| --- | --- | --- | --- |
| id | Long | 사용자 ID | `1` |
| email | String | 이메일 | `user@example.com` |
| name | String | 이름 | `홍길동` |
| phoneNumber | String | 휴대폰 번호 | `01012345678` |
| birthDate | LocalDate | 생년월일 | `1995-01-01` |
| identityVerified | Boolean | 본인인증 여부 | `true` |
| createdAt | LocalDateTime | 생성일시 | `2026-06-08T15:30:00` |
| updatedAt | LocalDateTime | 수정일시 | `2026-06-08T15:40:00` |

### 4.2 본인인증

| 항목 | 내용 |
| --- | --- |
| Method | POST |
| URL | `/api/users/me/identity-verification` |
| 인증 | 필요 |
| 설명 | 사용자 본인인증 상태를 완료 처리한다. |
| 성공 Status | `200 OK` |
| 주요 Error | `400 INVALID_INPUT_VALUE`, `400 IDENTITY_VERIFICATION_FAILED`, `409 IDENTITY_ALREADY_VERIFIED` |

#### Request Body

| 필드 | 타입 | 필수 | 검증/설명 | 예시 |
| --- | --- | --- | --- | --- |
| name | String | Y | 가입 정보와 일치해야 함 | `홍길동` |
| phoneNumber | String | Y | 가입 정보와 일치해야 함 | `01012345678` |
| birthDate | LocalDate | Y | 가입 정보와 일치해야 함 | `1995-01-01` |
| verificationCode | String | Y | MVP 인증 코드 | `123456` |

#### Response Body

| 필드 | 타입 | 설명 | 예시 |
| --- | --- | --- | --- |
| userId | Long | 사용자 ID | `1` |
| identityVerified | Boolean | 본인인증 여부 | `true` |
| verifiedAt | LocalDateTime | 인증 완료 시각 | `2026-06-08T15:40:00` |

## 5. Account API

### 5.1 계좌 개설

| 항목 | 내용 |
| --- | --- |
| Method | POST |
| URL | `/api/accounts` |
| 인증 | 필요 |
| 설명 | 본인인증 완료 사용자가 신규 계좌를 개설한다. |
| 성공 Status | `201 Created` |
| 주요 Error | `400 INVALID_INPUT_VALUE`, `401 UNAUTHORIZED`, `403 IDENTITY_VERIFICATION_REQUIRED` |

#### Request Body

| 필드 | 타입 | 필수 | 검증/설명 | 예시 |
| --- | --- | --- | --- | --- |
| nickname | String | N | 계좌 별칭 | `생활비 계좌` |
| accountType | String | Y | MVP 기본값 `DEPOSIT` | `DEPOSIT` |

#### Response Body

| 필드 | 타입 | 설명 | 예시 |
| --- | --- | --- | --- |
| id | Long | 계좌 ID | `1` |
| accountNumber | String | 계좌번호 | `100200300001` |
| nickname | String | 계좌 별칭 | `생활비 계좌` |
| accountType | String | 계좌 타입 | `DEPOSIT` |
| balance | Long | 현재 잔액 | `0` |
| status | AccountStatus | 계좌 상태 | `ACTIVE` |
| createdAt | LocalDateTime | 생성일시 | `2026-06-08T15:45:00` |

### 5.2 내 계좌 목록 조회

| 항목 | 내용 |
| --- | --- |
| Method | GET |
| URL | `/api/accounts` |
| 인증 | 필요 |
| 설명 | 로그인 사용자의 계좌 목록을 조회한다. |
| 성공 Status | `200 OK` |
| 주요 Error | `401 UNAUTHORIZED` |

#### Response Body

| 필드 | 타입 | 설명 | 예시 |
| --- | --- | --- | --- |
| [].id | Long | 계좌 ID | `1` |
| [].accountNumber | String | 계좌번호 | `100200300001` |
| [].nickname | String | 계좌 별칭 | `생활비 계좌` |
| [].balance | Long | 현재 잔액 | `150000` |
| [].status | AccountStatus | 계좌 상태 | `ACTIVE` |
| [].createdAt | LocalDateTime | 생성일시 | `2026-06-08T15:45:00` |

### 5.3 내 계좌 상세 조회

| 항목 | 내용 |
| --- | --- |
| Method | GET |
| URL | `/api/accounts/{accountId}` |
| 인증 | 필요 |
| 설명 | 로그인 사용자의 특정 계좌 상세 정보를 조회한다. |
| 성공 Status | `200 OK` |
| 주요 Error | `401 UNAUTHORIZED`, `403 ACCOUNT_ACCESS_DENIED`, `404 ACCOUNT_NOT_FOUND` |

#### Path Parameters

| 필드 | 타입 | 필수 | 설명 | 예시 |
| --- | --- | --- | --- | --- |
| accountId | Long | Y | 계좌 ID | `1` |

#### Response Body

| 필드 | 타입 | 설명 | 예시 |
| --- | --- | --- | --- |
| id | Long | 계좌 ID | `1` |
| accountNumber | String | 계좌번호 | `100200300001` |
| nickname | String | 계좌 별칭 | `생활비 계좌` |
| accountType | String | 계좌 타입 | `DEPOSIT` |
| balance | Long | 현재 잔액 | `150000` |
| status | AccountStatus | 계좌 상태 | `ACTIVE` |
| createdAt | LocalDateTime | 생성일시 | `2026-06-08T15:45:00` |
| updatedAt | LocalDateTime | 수정일시 | `2026-06-08T16:00:00` |

## 6. Transfer API

### 6.1 입금

| 항목 | 내용 |
| --- | --- |
| Method | POST |
| URL | `/api/transfers/deposit` |
| 인증 | 필요 |
| 설명 | 본인 계좌에 금액을 입금한다. |
| 성공 Status | `200 OK` |
| 주요 Error | `400 INVALID_INPUT_VALUE`, `403 ACCOUNT_ACCESS_DENIED`, `404 ACCOUNT_NOT_FOUND`, `409 ACCOUNT_NOT_ACTIVE` |

#### Request Body

| 필드 | 타입 | 필수 | 검증/설명 | 예시 |
| --- | --- | --- | --- | --- |
| accountId | Long | Y | 본인 소유 계좌 ID | `1` |
| amount | Long | Y | 1원 이상 | `100000` |
| memo | String | N | 100자 이하 권장 | `초기 입금` |

#### Response Body

| 필드 | 타입 | 설명 | 예시 |
| --- | --- | --- | --- |
| transactionId | Long | 거래내역 ID | `1` |
| accountId | Long | 입금 계좌 ID | `1` |
| type | TransactionType | 거래 유형 | `DEPOSIT` |
| amount | Long | 입금액 | `100000` |
| balanceAfter | Long | 입금 후 잔액 | `100000` |
| memo | String | 메모 | `초기 입금` |
| transactedAt | LocalDateTime | 거래일시 | `2026-06-08T16:00:00` |

### 6.2 계좌 간 송금

| 항목 | 내용 |
| --- | --- |
| Method | POST |
| URL | `/api/transfers` |
| 인증 | 필요 |
| 설명 | 본인 출금 계좌에서 수취 계좌로 송금한다. |
| 성공 Status | `200 OK` |
| 주요 Error | `400 INVALID_INPUT_VALUE`, `403 ACCOUNT_ACCESS_DENIED`, `404 ACCOUNT_NOT_FOUND`, `409 ACCOUNT_NOT_ACTIVE`, `409 INSUFFICIENT_BALANCE` |

#### Request Body

| 필드 | 타입 | 필수 | 검증/설명 | 예시 |
| --- | --- | --- | --- | --- |
| senderAccountId | Long | Y | 본인 소유 출금 계좌 ID | `1` |
| receiverAccountNumber | String | Y | 수취 계좌번호 | `100200300002` |
| amount | Long | Y | 1원 이상, 잔액 이하 | `50000` |
| memo | String | N | 100자 이하 권장 | `점심값` |

#### Response Body

| 필드 | 타입 | 설명 | 예시 |
| --- | --- | --- | --- |
| transferId | Long | 송금 ID | `1` |
| status | TransferStatus | 송금 상태 | `SUCCESS` |
| senderAccountId | Long | 출금 계좌 ID | `1` |
| senderAccountNumber | String | 출금 계좌번호 | `100200300001` |
| receiverAccountNumber | String | 수취 계좌번호 | `100200300002` |
| amount | Long | 송금액 | `50000` |
| senderBalanceAfter | Long | 출금 후 잔액 | `50000` |
| memo | String | 메모 | `점심값` |
| transferredAt | LocalDateTime | 송금일시 | `2026-06-08T16:10:00` |

## 7. Transaction API

### 7.1 거래내역 조회

| 항목 | 내용 |
| --- | --- |
| Method | GET |
| URL | `/api/accounts/{accountId}/transactions` |
| 인증 | 필요 |
| 설명 | 본인 계좌의 거래내역을 최신순으로 조회한다. |
| 성공 Status | `200 OK` |
| 주요 Error | `400 INVALID_INPUT_VALUE`, `400 INVALID_PERIOD`, `403 ACCOUNT_ACCESS_DENIED`, `404 ACCOUNT_NOT_FOUND` |

#### Path Parameters

| 필드 | 타입 | 필수 | 설명 | 예시 |
| --- | --- | --- | --- | --- |
| accountId | Long | Y | 거래내역을 조회할 계좌 ID | `1` |

#### Query Parameters

| 필드 | 타입 | 필수 | 기본값 | 검증/설명 | 예시 |
| --- | --- | --- | --- | --- | --- |
| startDate | LocalDate | N | 없음 | 조회 시작일 | `2026-06-01` |
| endDate | LocalDate | N | 없음 | 조회 종료일, startDate 이상 | `2026-06-08` |
| type | TransactionType | N | 없음 | `DEPOSIT`, `TRANSFER_IN`, `TRANSFER_OUT` | `TRANSFER_OUT` |
| direction | String | N | 없음 | `IN`, `OUT` | `OUT` |
| minAmount | Long | N | 없음 | 최소 금액 | `1000` |
| maxAmount | Long | N | 없음 | 최대 금액, minAmount 이상 | `100000` |
| page | Integer | N | `0` | 0 이상 | `0` |
| size | Integer | N | `20` | 1 이상, 최대 100 권장 | `20` |
| sort | String | N | `transactedAt,desc` | 정렬 조건 | `transactedAt,desc` |

#### Response Body

| 필드 | 타입 | 설명 | 예시 |
| --- | --- | --- | --- |
| content[].id | Long | 거래내역 ID | `3` |
| content[].accountId | Long | 계좌 ID | `1` |
| content[].type | TransactionType | 거래 유형 | `TRANSFER_OUT` |
| content[].direction | String | 입출금 방향 | `OUT` |
| content[].amount | Long | 거래 금액 | `50000` |
| content[].balanceAfter | Long | 거래 후 잔액 | `50000` |
| content[].relatedAccountNumber | String | 상대 계좌번호 | `100200300002` |
| content[].memo | String | 메모 | `점심값` |
| content[].transactedAt | LocalDateTime | 거래일시 | `2026-06-08T16:10:00` |
| page | Integer | 현재 페이지 | `0` |
| size | Integer | 페이지 크기 | `20` |
| totalElements | Long | 전체 건수 | `2` |
| totalPages | Integer | 전체 페이지 수 | `1` |
| first | Boolean | 첫 페이지 여부 | `true` |
| last | Boolean | 마지막 페이지 여부 | `true` |

## 8. Enum 정의

### 8.1 AccountStatus

| 값 | 설명 |
| --- | --- |
| ACTIVE | 정상 계좌 |
| CLOSED | 해지 계좌 |
| SUSPENDED | 정지 계좌 |

### 8.2 TransactionType

| 값 | 방향 | 설명 |
| --- | --- | --- |
| DEPOSIT | IN | 직접 입금 |
| TRANSFER_IN | IN | 송금 수취 |
| TRANSFER_OUT | OUT | 송금 출금 |

### 8.3 TransferStatus

| 값 | 설명 |
| --- | --- |
| SUCCESS | 송금 성공 |
| FAILED | 송금 실패 |

## 9. 에러 코드 표

| 코드 | HTTP Status | 메시지 | 대표 발생 API |
| --- | --- | --- | --- |
| INVALID_INPUT_VALUE | 400 | 입력값이 올바르지 않습니다. | 전체 |
| UNAUTHORIZED | 401 | 인증이 필요합니다. | 인증 필요 API 전체 |
| FORBIDDEN | 403 | 접근 권한이 없습니다. | 계좌/거래내역 접근 |
| USER_NOT_FOUND | 404 | 사용자를 찾을 수 없습니다. | 내 정보 조회 |
| DUPLICATE_EMAIL | 409 | 이미 사용 중인 이메일입니다. | 회원가입 |
| INVALID_CREDENTIALS | 401 | 이메일 또는 비밀번호가 올바르지 않습니다. | 로그인 |
| IDENTITY_ALREADY_VERIFIED | 409 | 이미 본인인증이 완료되었습니다. | 본인인증 |
| IDENTITY_VERIFICATION_FAILED | 400 | 본인인증에 실패했습니다. | 본인인증 |
| IDENTITY_VERIFICATION_REQUIRED | 403 | 본인인증이 필요합니다. | 계좌 개설, 송금 |
| ACCOUNT_NOT_FOUND | 404 | 계좌를 찾을 수 없습니다. | 계좌/입금/송금/거래내역 |
| ACCOUNT_ACCESS_DENIED | 403 | 계좌 접근 권한이 없습니다. | 계좌/입금/송금/거래내역 |
| ACCOUNT_NOT_ACTIVE | 409 | 활성 계좌가 아닙니다. | 입금/송금 |
| INSUFFICIENT_BALANCE | 409 | 잔액이 부족합니다. | 송금 |
| TRANSFER_FAILED | 409 | 송금 처리에 실패했습니다. | 송금 |
| INVALID_PERIOD | 400 | 조회 기간이 올바르지 않습니다. | 거래내역 조회 |
| INTERNAL_SERVER_ERROR | 500 | 서버 내부 오류가 발생했습니다. | 전체 |
