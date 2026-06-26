# WORK-116 예/적금 전용 계좌 정책 정리

## 목적

예금/적금 가입 정보를 금액 필드로만 관리하지 않고, 별도 `Account`로 실제 돈의 흐름을 추적한다.

예/적금 전용 계좌는 일반 입금/송금 계좌가 아니라 **Saving 도메인의 가입, 자동이체, 중도해지, 만기 흐름에서만** 금액이 이동하는 계좌로 사용한다.

## 적용 범위

| 도메인 | 적용 내용 |
| --- | --- |
| Account | 예금/적금 전용 계좌 타입 추가 |
| Saving | 예금/적금 가입 시 전용 Account 생성 및 참조 |
| Transfer | 일반 송금/입금에서 예금/적금 계좌 차단 |
| Transaction | 가입/납입/해지/만기 시 양쪽 계좌 거래내역 기록 |
| Flyway | AccountType, Saving Account 참조 컬럼, 거래내역 타입 추가 |

## 계좌 타입 정책

`AccountType`은 다음 값을 사용한다.

| 타입 | 의미 | 일반 계좌 개설 API | 일반 송금 보내기 | 일반 송금 받기/입금 |
| --- | --- | --- | --- | --- |
| `DEPOSIT` | 입출금계좌 | 가능 | 가능 | 가능 |
| `SAVING_DEPOSIT` | 예금 전용 계좌 | 불가 | 불가 | 불가 |
| `SAVING_INSTALLMENT` | 적금 전용 계좌 | 불가 | 불가 | 불가 |

일반 계좌 개설 API는 `DEPOSIT` 계좌만 생성한다.

예금/적금 전용 계좌는 Saving 가입 로직에서만 생성한다.

## Account / Saving 역할 분리 정책

`Account`는 실제 돈과 계좌 상태를 관리한다.

```text
Account.balance = 실제 현재 잔액
Account.status = ACTIVE / CLOSED 등 계좌 상태
Account.accountType = 일반 계좌인지 예/적금 전용 계좌인지 구분
```

`Deposit`, `Installment`는 예/적금 가입 정보를 관리한다.

```text
Deposit = 예금 상품, 원금, 금리, 만기일, 상태 관리
Installment = 적금 상품, 월 납입액, 납입 누계, 만기일, 상태 관리
```

정리하면 실제 잔액은 Account가 담당하고, 가입 조건은 Deposit/Installment가 담당한다.

## 예금 가입 흐름

```text
1. 사용자의 입출금계좌(DEPOSIT)를 조회하고 락을 건다.
2. 가입 금액을 검증한다.
3. 출금 계좌 비밀번호를 검증한다.
4. 입출금계좌에서 가입 금액을 출금한다.
4. 예금 전용 Account(SAVING_DEPOSIT)를 생성한다.
5. 예금 전용 Account에 가입 금액을 입금한다.
6. Deposit 엔티티가 입출금계좌와 예금 전용 Account를 함께 참조한다.
7. 입출금계좌 OUT 거래내역과 예금 전용 Account IN 거래내역을 저장한다.
```

## 적금 가입 흐름

```text
1. 사용자의 입출금계좌(DEPOSIT)를 조회하고 락을 건다.
2. 월 납입액과 목표 금액을 검증한다.
3. 출금 계좌 비밀번호를 검증한다.
4. 입출금계좌에서 1회차 월 납입액을 출금한다.
4. 적금 전용 Account(SAVING_INSTALLMENT)를 생성한다.
5. 적금 전용 Account에 1회차 월 납입액을 입금한다.
6. Installment 엔티티가 입출금계좌와 적금 전용 Account를 함께 참조한다.
7. 입출금계좌 OUT 거래내역과 적금 전용 Account IN 거래내역을 저장한다.
```

## 자동이체 흐름

적금 자동이체는 일반 송금이 아니라 Saving 도메인의 납입 흐름으로 처리한다.

```text
1. 납입 대상 Installment를 조회하고 입출금계좌/적금 전용 Account를 함께 락 처리한다.
2. 입출금계좌가 ACTIVE인지 확인한다.
3. 입출금계좌 잔액이 월 납입액 이상인지 확인한다.
4. 입출금계좌에서 월 납입액을 출금한다.
5. 적금 전용 Account에 월 납입액을 입금한다.
6. paidAmount를 증가시킨다.
7. 양쪽 계좌 거래내역을 저장한다.
```

## 중도해지 흐름

예금/적금 중도해지는 Saving 도메인에서만 처리한다.

```text
1. Deposit 또는 Installment를 조회하고 입출금계좌/예적금 전용 Account를 함께 락 처리한다.
2. 상태가 ACTIVE인지 확인한다.
3. 중도해지 이자를 계산한다.
4. 예적금 전용 Account에서 원금 또는 납입금을 출금한다.
5. 예적금 전용 Account를 CLOSED 상태로 변경한다.
6. 연결 입출금계좌에 원금/납입금 + 이자를 입금한다.
7. 양쪽 계좌 거래내역을 저장한다.
8. Saving 상태를 CANCELLED로 변경한다.
```

## 만기 처리 흐름

만기 처리는 사용자 요청 또는 스케줄러에서 Saving 도메인 로직으로 처리한다.

```text
1. 만기 대상 Deposit 또는 Installment를 조회하고 입출금계좌/예적금 전용 Account를 함께 락 처리한다.
2. 상태가 ACTIVE인지 확인한다.
3. 만기일이 지났는지 확인한다.
4. 만기 이자를 계산한다.
5. 예적금 전용 Account에서 원금 또는 납입금을 출금한다.
6. 예적금 전용 Account를 CLOSED 상태로 변경한다.
7. 연결 입출금계좌에 원금/납입금 + 이자를 입금한다.
8. 양쪽 계좌 거래내역을 저장한다.
9. Saving 상태를 MATURED로 변경한다.
```

## Transfer 제한 정책

예금/적금은 정해진 흐름 외에는 추가 입금할 수 없는 상품이다.

그래서 일반 입금/송금 API에서는 입출금계좌만 사용할 수 있다.

| 상황 | 허용 여부 | 실패 코드 |
| --- | --- | --- |
| `DEPOSIT` 계좌로 송금 보내기 | 허용 | - |
| `SAVING_DEPOSIT`, `SAVING_INSTALLMENT` 계좌로 송금 보내기 | 차단 | `INVALID_ACCOUNT_TYPE` |
| `DEPOSIT` 계좌로 송금 받기 | 허용 | - |
| `SAVING_DEPOSIT`, `SAVING_INSTALLMENT` 계좌로 송금 받기 | 차단 | `INVALID_ACCOUNT_TYPE` |
| `DEPOSIT` 계좌로 일반 입금(topUp) | 허용 | - |
| `SAVING_DEPOSIT`, `SAVING_INSTALLMENT` 계좌로 일반 입금(topUp) | 차단 | `INVALID_ACCOUNT_TYPE` |

## 거래내역 정책

| 흐름 | 출금 계좌 내역 | 입금 계좌 내역 |
| --- | --- | --- |
| 예금 가입 | 입출금계좌 `OUT` | 예금 전용 Account `IN` |
| 적금 가입 | 입출금계좌 `OUT` | 적금 전용 Account `IN` |
| 적금 자동이체 | 입출금계좌 `OUT` | 적금 전용 Account `IN` |
| 중도해지 | 예적금 전용 Account `OUT` | 입출금계좌 `IN` |
| 만기 | 예적금 전용 Account `OUT` | 입출금계좌 `IN` |

거래 후 잔액(`balanceAfter`)은 돈이 바뀐 각 계좌 기준으로 저장한다.

## Flyway 변경 사항

`V12__extend_account_type_for_saving.sql`에서 다음 변경을 함께 적용한다.

1. `accounts.account_type` enum에 `SAVING_DEPOSIT`, `SAVING_INSTALLMENT` 추가
2. `deposits.saving_account_id` 컬럼 추가
3. `installments.saving_account_id` 컬럼 추가
4. 각 Saving 엔티티와 Account 간 FK/UNIQUE 제약 추가
5. `transaction_histories.type` enum에 Saving 거래 타입 추가
6. 사용하지 않는 `withdrawal_locked`, `withdrawal_lock_reason` 컬럼 제거

주의:

- PR 전 브랜치 기준에서는 `V13`을 별도로 유지하지 않고 `V12`에 합친다.
- 이미 다른 환경에 적용된 마이그레이션이면 기존 파일을 고치지 말고 새 파일을 추가해야 한다.
- 현재 정리는 팀원이 아직 해당 `V12`를 적용하지 않았다는 전제를 둔다.

## 테스트 기준

다음 흐름을 검증한다.

- 예/적금 가입 시 전용 Account 생성 및 양쪽 거래내역 저장
- 적금 자동이체 시 입출금계좌 출금, 적금 전용 Account 입금, `paidAmount` 증가
- 예/적금 전용 Account는 일반 송금 보내기/받기/topUp 실패
- 중도해지/만기 시 전용 Account 출금 및 CLOSED 처리
- 중도해지/만기 시 연결 입출금계좌로 반환 또는 지급

## Transfer 락 구조 상세

Transfer 도메인은 `Account.accountType`으로 일반 입금/송금 가능 여부를 판단한다.

일반 입금(topUp), 보내는 계좌, 받는 계좌 모두 같은 검증 메서드를 사용한다.

```java
validateDepositAccount(account);
validateDepositAccount(senderAccount);
validateDepositAccount(receiverAccount);
```

즉, 일반 입금/송금에 참여하는 Account는 모두 `DEPOSIT` 타입이어야 한다.
Transfer는 이 판단을 할 때 `Deposit` 또는 `Installment` 상태를 보지 않는다.
중도해지/만기 가능 여부는 Saving 도메인에서 판단한다.

### 일반 입금(topUp) 락 흐름

일반 입금은 대상 Account DB 행을 잠근 뒤 처리한다.

```java
accountRepository.findByIdAndUserIdForUpdate(accountId, userId)
```

처리 순서는 다음과 같다.

```text
1. Account DB 행 잠금
2. 계좌 ACTIVE 여부 확인
3. accountType이 DEPOSIT인지 확인
4. DEPOSIT이면 입금 진행
5. SAVING_DEPOSIT 또는 SAVING_INSTALLMENT이면 차단
```

### 일반 송금(transfer) 락 흐름

일반 송금은 보내는 Account와 받는 Account의 잔액이 함께 바뀐다.
그래서 두 Account DB 행을 모두 잠근 뒤 처리한다.

```java
Account first = accountRepository.findByIdForUpdate(firstId);
Account second = accountRepository.findByIdForUpdate(secondId);
```

두 계좌는 항상 ID가 작은 계좌부터 잠근다.
이유는 동시에 반대 방향 송금이 들어올 때 충돌 가능성을 줄이기 위해서다.

처리 순서는 다음과 같다.

```text
1. 보내는 Account와 받는 Account DB 행 잠금
2. 두 계좌 ACTIVE 여부 확인
3. 두 계좌 모두 DEPOSIT인지 확인
4. 둘 다 DEPOSIT이면 송금 진행
5. 하나라도 예/적금 전용 Account이면 차단
```

3번은 같은 `validateDepositAccount()` 메서드로 확인한다.

### Deposit/Installment를 일반 송금에서 잠그지 않는 이유

현재 일반 송금 가능 여부의 판단 기준은 `Account.accountType`이다.

```text
송금 판단에 필요한 값: Account.accountType
잠그는 대상: Account DB 행
```

Transfer는 이미 Account DB 행을 잠그고 있고, Deposit/Installment 상태로 송금 가능 여부를 판단하지 않는다.
따라서 일반 송금에서 Deposit/Installment를 추가로 잠그지 않는다.

## Transaction 변경 상세

### 추가된 TransactionType

| 타입 | 의미 |
| --- | --- |
| `SAVING_DEPOSIT_SIGNUP` | 예금 가입 시 금액 이동 |
| `SAVING_INSTALLMENT_SIGNUP` | 적금 가입 시 1회차 납입금 이동 |
| `SAVING_CANCEL_REFUND` | 예/적금 중도해지 반환금 이동 |
| `SAVING_MATURITY` | 예/적금 만기 지급금 이동 |
| `INSTALLMENT_PAYMENT` | 적금 월 납입 자동이체 |

### type 컬럼 length 변경 이유

`TransactionHistory.type`은 enum 이름을 문자열로 저장한다.
새 타입 이름이 길어져서 DB 컬럼 길이를 `30`으로 늘렸다.

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 30)
private TransactionType type;
```

### 추가된 생성 메서드

`TransactionHistory`에 Saving 흐름 전용 생성 메서드를 추가했다.

| 메서드 | 사용 위치 |
| --- | --- |
| `createSavingDepositSignup()` | 예금 가입 |
| `createSavingInstallmentSignup()` | 적금 가입 |
| `createSavingCancelRefund()` | 중도해지 |
| `createSavingMaturityPayout()` | 만기 처리 |
| `createInstallmentPayment()` | 적금 자동이체 |

Saving 도메인 서비스는 거래내역 필드를 직접 채우지 않고, 위 생성 메서드를 사용한다.

### Transfer와의 차이

일반 송금은 `Transfer` 엔티티가 생성되고, 거래내역에 `transfer_id`가 들어간다.

반면 예/적금 가입, 자동이체, 중도해지, 만기 처리는 일반 송금이 아니다.

따라서 Saving 관련 거래내역은 다음처럼 저장한다.

```text
transfer_id = null
account_id = 금액이 변한 계좌 ID
type = Saving 관련 TransactionType
direction = IN 또는 OUT
```

## 후속 작업
- PR 설명에 “예/적금은 추가 입금 불가 상품으로 정의” 정책 명시
- 기존 개발 DB에 예전 V12/V13이 적용된 경우 로컬 Flyway checksum 정리 필요
- Saving API 명세서가 필요하면 별도 문서로 분리
