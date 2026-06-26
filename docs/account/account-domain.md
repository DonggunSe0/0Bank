# Account 도메인 정리

## 목적

Account 도메인은 사용자의 계좌 정보를 관리한다.

입출금계좌는 일반 입금, 송금, 거래내역 조회의 기준 계좌로 사용한다. 예금/적금 전용 계좌도 `Account`로 관리하지만, 생성과 금액 이동은 Saving 도메인 정책을 따른다.

## 주요 책임

| 책임 | 설명 |
| --- | --- |
| 계좌 생성 | 본인인증 완료 사용자의 입출금계좌 생성 |
| 계좌 조회 | 내 계좌 목록, 해지 계좌 목록, 상세 조회 |
| 계좌 별칭 변경 | 활성 계좌의 별칭 수정 |
| 계좌 비밀번호 관리 | 비밀번호 설정, 변경, 검증 |
| 계좌 해지 | 잔액 0원인 활성 계좌를 CLOSED 처리 |
| 잔액 처리 | 입금/출금 메서드로만 잔액 증감 |
| 계좌 타입 정책 | 일반 계좌와 예/적금 전용 계좌 구분 |

## 주요 엔티티

### Account

| 필드 | 설명 |
| --- | --- |
| `user` | 계좌 소유 사용자 |
| `accountNumber` | 서버에서 생성하는 고유 계좌번호 |
| `nickname` | 계좌 별칭 |
| `accountType` | 계좌 타입 |
| `balance` | 계좌 잔액 |
| `accountPasswordHash` | 계좌 비밀번호 해시 |
| `status` | 계좌 상태 |

## 계좌 타입

| 타입 | 설명 | 생성 주체 |
| --- | --- | --- |
| `DEPOSIT` | 입출금계좌 | Account 일반 계좌 개설 API |
| `SAVING_DEPOSIT` | 예금 전용 계좌 | Saving 예금 가입 로직 |
| `SAVING_INSTALLMENT` | 적금 전용 계좌 | Saving 적금 가입 로직 |

일반 계좌 개설 API는 `DEPOSIT`만 허용한다.

`SAVING_DEPOSIT`, `SAVING_INSTALLMENT`는 사용자가 Account API로 직접 만들 수 없다.

## 일반 입금/송금 정책

일반 입금/송금은 `DEPOSIT` 타입 계좌만 사용할 수 있다.

| 계좌 타입 | 일반 송금 보내기 | 일반 송금 받기 | 일반 입금(topUp) |
| --- | --- | --- | --- |
| `DEPOSIT` | 가능 | 가능 | 가능 |
| `SAVING_DEPOSIT` | 불가 | 불가 | 불가 |
| `SAVING_INSTALLMENT` | 불가 | 불가 | 불가 |

예/적금 전용 계좌는 실제 잔액과 거래내역을 관리하기 위해 Account로 만들지만, 자유 입출금 계좌는 아니다.

예/적금 전용 계좌의 금액 이동은 Saving 도메인의 가입, 자동이체, 중도해지, 만기 흐름에서만 처리한다.

## 계좌 상태

| 상태 | 설명 |
| --- | --- |
| `ACTIVE` | 정상 사용 가능 |
| `SUSPENDED` | 정지 상태 |
| `CLOSED` | 해지/종료 상태 |

현재 일반 조회 목록에서는 `CLOSED` 계좌를 제외한다.

해지 계좌 목록 API에서는 `CLOSED` 계좌만 조회한다.

## API 목록

Base URL: `/api/v1/accounts`

| Method | Endpoint | 기능 |
| --- | --- | --- |
| `POST` | `/api/v1/accounts` | 입출금계좌 개설 |
| `GET` | `/api/v1/accounts` | 내 활성/정지 계좌 목록 조회 |
| `GET` | `/api/v1/accounts/closed` | 내 해지 계좌 목록 조회 |
| `GET` | `/api/v1/accounts/{accountId}` | 내 계좌 상세 조회 |
| `PATCH` | `/api/v1/accounts/{accountId}/nickname` | 계좌 별칭 수정 |
| `POST` | `/api/v1/accounts/{accountId}/password` | 계좌 비밀번호 설정 |
| `PATCH` | `/api/v1/accounts/{accountId}/password` | 계좌 비밀번호 변경 |
| `POST` | `/api/v1/accounts/{accountId}/close` | 계좌 해지 |

## 계좌 개설 정책

```text
1. 요청 계좌 타입이 DEPOSIT인지 확인한다.
2. 계좌 비밀번호가 숫자 6자리인지 검증한다.
3. 사용자를 조회한다.
4. 본인인증 완료 여부를 확인한다.
5. 중복되지 않는 계좌번호를 생성한다.
6. 계좌 비밀번호를 해시 처리한다.
7. 잔액 0원, 상태 ACTIVE로 Account를 저장한다.
```

예외:

| 상황 | 에러 |
| --- | --- |
| 사용자가 없음 | `USER_NOT_FOUND` |
| 본인인증 미완료 | `IDENTITY_VERIFICATION_REQUIRED` |
| DEPOSIT이 아닌 타입 요청 | `INVALID_ACCOUNT_TYPE` |
| 계좌번호 생성 실패 | `ACCOUNT_NUMBER_GENERATION_FAILED` |

## 계좌 비밀번호 정책

계좌 비밀번호는 원문을 저장하지 않고 해시 값만 저장한다.

| 기능 | 정책 |
| --- | --- |
| 비밀번호 설정 | 신규 계좌는 개설 시 필수 설정, 기존 활성 계좌는 별도 설정 가능, 이미 설정되어 있으면 실패 |
| 비밀번호 변경 | 현재 비밀번호 검증 후 새 비밀번호 저장 |
| 송금 비밀번호 검증 | Transfer 도메인에서 `Account.verifyPassword()` 사용 |

## 계좌 해지 정책

일반 계좌 해지는 다음 조건을 만족해야 한다.

```text
1. 본인 소유 계좌여야 한다.
2. 계좌 상태가 ACTIVE여야 한다.
3. 잔액이 0원이어야 한다.
4. 조건을 만족하면 상태를 CLOSED로 변경한다.
```

예/적금 전용 계좌는 Saving 중도해지/만기 처리에서 `CLOSED`로 변경된다.

## 잔액 처리 정책

`Account` 엔티티는 잔액을 직접 수정하지 않고, 입금/출금 메서드로만 변경한다.

| 메서드 | 설명 |
| --- | --- |
| `deposit(amount)` | 잔액 증가 |
| `withdraw(amount)` | 잔액 부족 검증 후 잔액 감소 |

잔액 증감은 각 도메인 서비스의 트랜잭션 안에서 처리한다.

실제 돈의 현재 잔액은 `Account.balance`에 저장한다.

`Deposit.principal`, `Installment.paidAmount`는 예/적금 계약 기준 금액이고, 실제 계좌 잔액은 Account가 담당한다.

## 락 정책

금액이 바뀌는 흐름에서는 동시에 같은 계좌를 수정하지 못하도록 DB 락을 사용한다.

| Repository 메서드 | 사용 목적 |
| --- | --- |
| `findByIdForUpdate` | 송금 시 계좌 ID 기준 락 |
| `findByIdAndUserIdForUpdate` | 본인 소유 계좌 금액 변경/비밀번호 변경/해지 |

송금처럼 두 계좌가 함께 바뀌는 경우 충돌을 줄이기 위해 ID가 작은 계좌부터 잠근다.

## 다른 도메인과의 관계

| 도메인 | 관계 |
| --- | --- |
| Transfer | 입금/송금 시 Account 잔액 변경 및 비밀번호 검증 |
| Transaction | 거래내역은 특정 Account를 기준으로 저장 |
| Saving | 예/적금 가입 시 전용 Account 생성 및 참조 |

## 테스트 기준

- 본인인증 완료 사용자만 계좌 개설 가능
- 일반 계좌 개설 API는 `DEPOSIT`만 허용
- 계좌번호 중복 시 재시도
- 계좌 별칭 수정은 활성 계좌만 가능
- 비밀번호 설정/변경 정책 검증
- 잔액 0원 계좌만 해지 가능
- 해지 계좌는 일반 계좌 목록에서 제외
