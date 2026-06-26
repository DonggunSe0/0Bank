# Saving 도메인 정리

## 목적

Saving 도메인은 예금/적금 상품 조회, 가입, 자동이체, 중도해지, 만기 처리를 담당한다.

예금/적금 가입 건은 `Deposit`, `Installment` 엔티티로 관리하고, 실제 금액은 예/적금 전용 `Account`와 연결해 추적한다.

## 주요 책임

| 책임 | 설명 |
| --- | --- |
| 상품 조회 | 예금/적금 상품 목록 및 상세 조회 |
| 예금 가입 | 입출금계좌에서 출금 후 예금 전용 Account 생성/입금 |
| 적금 가입 | 입출금계좌에서 1회차 출금 후 적금 전용 Account 생성/입금 |
| 자동이체 | 입출금계좌에서 출금 후 적금 전용 Account 입금 |
| 예상 이자 조회 | 예금/적금 예상 이자 및 만기 예상 수령액 계산 |
| 중도해지 | 예/적금 전용 Account 출금 후 입출금계좌로 반환 |
| 만기 처리 | 예/적금 전용 Account 출금 후 입출금계좌로 지급 |

## 주요 엔티티

### SavingProduct

| 필드 | 설명 |
| --- | --- |
| `name` | 상품명 |
| `bankName` | 은행명 |
| `bankCode` | 은행 코드 |
| `type` | `DEPOSIT` 또는 `INSTALLMENT` |
| `interestRate` | 기본 금리 |
| `periodMonth` | 가입 기간 개월 수 |
| `minAmount` | 최소 가입 금액 |
| `maxAmount` | 예금 최대 가입 금액 |
| `monthlyLimit` | 적금 월 납입 한도 |
| `terms` | 가입 조건 |
| `active` | 상품 활성 여부 |

### Deposit

| 필드 | 설명 |
| --- | --- |
| `user` | 가입 사용자 |
| `savingProduct` | 가입한 예금 상품 |
| `withdrawAccount` | 가입 금액이 출금된 입출금계좌 |
| `savingAccount` | 예금 전용 Account |
| `principal` | 예치 원금 |
| `interestRate` | 가입 당시 금리 |
| `maturityDate` | 만기일 |
| `expectedInterest` | 예상 이자 |
| `status` | 예금 상태 |

### Installment

| 필드 | 설명 |
| --- | --- |
| `user` | 가입 사용자 |
| `savingProduct` | 가입한 적금 상품 |
| `withdrawAccount` | 납입금이 출금되는 입출금계좌 |
| `savingAccount` | 적금 전용 Account |
| `monthlyAmount` | 월 납입액 |
| `targetAmount` | 목표 금액 |
| `paidAmount` | 현재까지 납입한 금액 |
| `interestRate` | 가입 당시 금리 |
| `maturityDate` | 만기일 |
| `nextPaymentDate` | 다음 정기 납입일 |
| `autoTransferYn` | 자동이체 여부 |
| `status` | 적금 상태 |
| `paymentRetryCount` | 자동이체 실패/재시도 횟수 |
| `paymentFailureReason` | 자동이체 실패 사유 |

## 상태 값

### DepositStatus

| 상태 | 설명 |
| --- | --- |
| `ACTIVE` | 가입중 |
| `MATURED` | 만기 완료 |
| `CANCELLED` | 중도해지 완료 |

### InstallmentStatus

| 상태 | 설명 |
| --- | --- |
| `ACTIVE` | 가입중 |
| `MATURED` | 만기 완료 |
| `CANCELLED` | 중도해지 완료 |
| `PAYMENT_FAILED` | 자동이체 실패 후 재시도 대기 |

## API 목록

Base URL: `/api/v1/savings`

| Method | Endpoint | 기능 |
| --- | --- | --- |
| `GET` | `/deposit-products` | 활성 예금 상품 목록 조회 |
| `GET` | `/deposit-products/{productId}` | 예금 상품 상세 조회 |
| `GET` | `/installment-products` | 활성 적금 상품 목록 조회 |
| `GET` | `/installment-products/{productId}` | 적금 상품 상세 조회 |
| `POST` | `/deposits` | 예금 가입 |
| `GET` | `/deposits` | 내 예금 목록 조회 |
| `GET` | `/deposits/{depositId}` | 내 예금 상세 조회 |
| `POST` | `/installments` | 적금 가입 |
| `GET` | `/installments` | 내 적금 목록 조회 |
| `GET` | `/installments/{installmentId}` | 내 적금 상세 조회 |
| `GET` | `/{savingId}/interest-preview` | 예상 이자 조회 |
| `POST` | `/{savingId}/cancel` | 중도해지 |
| `POST` | `/{savingId}/maturity` | 만기 처리 |

## 예금 가입 정책

```text
1. 사용자 조회
2. 활성 예금 상품 조회
3. 출금용 입출금계좌 조회 및 락
4. 가입 금액 검증
5. 입출금계좌에서 가입 금액 출금
6. 예금 전용 Account(SAVING_DEPOSIT) 생성
7. 예금 전용 Account에 가입 금액 입금
8. Deposit 저장
9. 양쪽 계좌 거래내역 저장
```

가입 금액은 상품의 `minAmount`, `maxAmount` 기준을 만족해야 한다.

## 적금 가입 정책

```text
1. 사용자 조회
2. 활성 적금 상품 조회
3. 출금용 입출금계좌 조회 및 락
4. 월 납입액/목표 금액 검증
5. 입출금계좌에서 1회차 월 납입액 출금
6. 적금 전용 Account(SAVING_INSTALLMENT) 생성
7. 적금 전용 Account에 1회차 월 납입액 입금
8. Installment 저장
9. 양쪽 계좌 거래내역 저장
```

목표 금액은 `monthlyAmount * periodMonth`와 같아야 한다.

## 추가 입금 제한 정책

예/적금은 정해진 흐름으로만 돈을 넣을 수 있다.

- 예금: 가입할 때 한 번만 입금
- 적금: 가입 시 1회차 납입, 이후 자동이체로만 입금

그래서 일반 송금이나 일반 입금(topUp)으로 예/적금 전용 Account에 돈을 넣을 수 없다.

이유는 실제 잔액(`Account.balance`)과 예/적금 계약 정보가 어긋나는 것을 막기 위해서다.
예를 들어 예금에 일반 송금 입금을 허용하면 `Deposit.principal`과 예금 Account 잔액이 달라질 수 있다.

## 자동이체 정책

적금 자동이체는 스케줄러가 대상 적금을 찾고, `SavingBatchProcessor`가 한 건씩 처리한다.

```text
1. Installment, withdrawAccount, savingAccount를 함께 락 조회
2. 납입 대상인지 확인
3. 출금 계좌 ACTIVE 확인
4. 잔액 확인
5. 입출금계좌 출금
6. 적금 전용 Account 입금
7. paidAmount 증가
8. nextPaymentDate 갱신
9. 양쪽 거래내역 저장
```

자동이체 실패 시 `PAYMENT_FAILED` 상태가 되고, 다음 재시도일을 기록한다.

## 중도해지 정책

```text
1. Deposit 또는 Installment를 입출금계좌/전용 Account와 함께 락 조회
2. 상태가 ACTIVE인지 확인
3. 중도해지 이자 계산
4. 예/적금 전용 Account에서 원금 또는 납입금 출금
5. 예/적금 전용 Account CLOSED 처리
6. 연결 입출금계좌에 원금/납입금 + 이자 입금
7. 양쪽 거래내역 저장
8. 상태를 CANCELLED로 변경
```

## 만기 처리 정책

```text
1. Deposit 또는 Installment를 입출금계좌/전용 Account와 함께 락 조회
2. 상태가 ACTIVE인지 확인
3. 만기일이 지났는지 확인
4. 만기 이자 계산
5. 예/적금 전용 Account에서 원금 또는 납입금 출금
6. 예/적금 전용 Account CLOSED 처리
7. 연결 입출금계좌에 원금/납입금 + 이자 입금
8. 양쪽 거래내역 저장
9. 상태를 MATURED로 변경
```

만기 스케줄러는 만기 대상 적금을 한 건씩 처리한다. 한 건이 실패해도 다른 건은 계속 처리하고, 실패 내용은 로그로 남긴다.


## 거래내역 정책

| 흐름 | 전용 Account 내역 | 입출금계좌 내역 |
| --- | --- | --- |
| 예금 가입 | `IN` | `OUT` |
| 적금 가입 | `IN` | `OUT` |
| 적금 자동이체 | `IN` | `OUT` |
| 중도해지 | `OUT` | `IN` |
| 만기 | `OUT` | `IN` |

거래내역은 각 계좌의 변경 전/후 잔액을 저장한다.

## 락/트랜잭션 정책

| 흐름 | 처리 방식 |
| --- | --- |
| 예금/적금 가입 | 출금 계좌를 DB 락으로 조회 |
| 자동이체 | Installment와 양쪽 Account를 함께 DB 락으로 조회 |
| 중도해지 | Saving 엔티티와 양쪽 Account를 함께 DB 락으로 조회 |
| 만기 | Saving 엔티티와 양쪽 Account를 함께 DB 락으로 조회 |
| 스케줄러 처리 | 대상 ID 조회 후 한 건씩 별도 트랜잭션 처리 |

## 테스트 기준

- 상품 목록/상세 조회
- 예금 가입 금액 검증
- 적금 월 납입액/목표 금액 검증
- 예금 가입 시 전용 Account 생성 및 거래내역 저장
- 적금 가입 시 전용 Account 생성 및 거래내역 저장
- 자동이체 성공 시 양쪽 계좌 잔액과 `paidAmount` 변경
- 자동이체 실패 시 실패 상태/재시도 정보 저장
- 중도해지 시 전용 Account 출금, CLOSED 처리, 입출금계좌 반환
- 만기 시 전용 Account 출금, CLOSED 처리, 입출금계좌 지급
