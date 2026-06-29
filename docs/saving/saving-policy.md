# Saving 도메인 정책 요약

팀원이 Saving 정책을 빠르게 확인하기 위한 문서다. 자세한 엔티티/API 흐름은 `saving-domain.md`, 예/적금 전용 계좌 설계는 `work-116-saving-account-policy.md`를 함께 본다.

## 1. 도메인 책임

Saving 도메인은 예금/적금 상품과 가입 이후의 저축 흐름을 관리한다.

| 책임 | 설명 |
| --- | --- |
| 상품 조회 | 예금/적금 상품 목록 및 상세 조회 |
| 예금 가입 | 입출금계좌에서 출금 후 예금 전용 Account에 입금 |
| 적금 가입 | 입출금계좌에서 1회차 납입액 출금 후 적금 전용 Account에 입금 |
| 자동이체 | 정기 납입일에 입출금계좌에서 적금 전용 Account로 납입 |
| 예상 이자 | 예상 이자와 예상 수령액 계산 |
| 중도해지 | 전용 Account를 닫고 입출금계좌로 반환 |
| 만기 처리 | 전용 Account를 닫고 입출금계좌로 지급 |

## 2. Account와 Saving 역할 분리

Saving은 가입 계약을 관리하고, Account는 실제 잔액을 관리한다.

| 데이터 | 담당 |
| --- | --- |
| 실제 현재 잔액 | `Account.balance` |
| 계좌 상태 | `Account.status` |
| 예금 원금 | `Deposit.principal` |
| 적금 납입 누계 | `Installment.paidAmount` |
| 상품 금리/기간 | `SavingProduct` |
| 가입 상태 | `Deposit.status`, `Installment.status` |

중요 원칙:

- 예/적금 돈의 실제 이동은 전용 `Account` 잔액으로 추적한다.
- `Deposit.principal`, `Installment.paidAmount`는 계약 기준 금액이다.
- 예/적금 전용 Account는 일반 입금/송금 계좌가 아니다.

## 3. 예/적금 전용 계좌 정책

| 전용 계좌 타입 | 생성 시점 | 사용 목적 | 일반 입금/송금 |
| --- | --- | --- | --- |
| `SAVING_DEPOSIT` | 예금 가입 시 | 예금 원금 보관 | 불가 |
| `SAVING_INSTALLMENT` | 적금 가입 시 | 적금 납입금 보관 | 불가 |

예/적금 전용 계좌 금액 이동은 아래 흐름에서만 가능하다.

- 예금 가입
- 적금 가입
- 적금 자동이체
- 중도해지
- 만기 처리

## 4. 상품 정책

| 상품 타입 | 주요 금액 정책 |
| --- | --- |
| 예금 상품 | `minAmount` 이상, `maxAmount` 이하 가입 |
| 적금 상품 | `minAmount` 이상, `monthlyLimit` 이하 월 납입 |

적금 목표 금액 정책:

```text
targetAmount = monthlyAmount * periodMonth
```

상품은 `active = true`인 상품만 가입 가능하다.

## 5. 예금 가입 정책

```text
1. 사용자 조회
2. 활성 예금 상품 조회
3. 출금용 입출금계좌(DEPOSIT)를 락 조회
4. 가입 금액이 상품 최소/최대 금액 기준을 만족하는지 검증
5. 출금 계좌 비밀번호 검증
6. 입출금계좌에서 가입 금액 출금
7. 예금 전용 Account(SAVING_DEPOSIT) 생성
8. 예금 전용 Account에 가입 금액 입금
9. Deposit 저장
10. 양쪽 계좌 거래내역 저장
```

예금 가입 후:

- `Deposit.status = ACTIVE`
- 예금 전용 Account 잔액은 가입 금액과 같아야 한다.
- 출금 계좌에는 `OUT`, 예금 전용 Account에는 `IN` 거래내역을 남긴다.

## 6. 적금 가입 정책

```text
1. 사용자 조회
2. 활성 적금 상품 조회
3. 출금용 입출금계좌(DEPOSIT)를 락 조회
4. 월 납입액과 목표 금액 검증
5. 출금 계좌 비밀번호 검증
6. 입출금계좌에서 1회차 월 납입액 출금
7. 적금 전용 Account(SAVING_INSTALLMENT) 생성
8. 적금 전용 Account에 1회차 월 납입액 입금
9. Installment 저장
10. 양쪽 계좌 거래내역 저장
```

적금 가입 후:

- `Installment.status = ACTIVE`
- `paidAmount = monthlyAmount`
- 다음 납입일을 관리한다.

## 7. 자동이체 정책

적금 자동이체는 스케줄러가 실행한다.

```text
1. 납입 대상 Installment ID 목록을 조회한다.
2. 한 건씩 별도 트랜잭션으로 처리한다.
3. Installment와 양쪽 Account를 조회한다.
4. 입출금계좌와 적금 전용 Account를 ID 작은 순서대로 명시적 락 처리한다.
5. 납입 대상인지 다시 확인한다.
6. 출금 계좌 ACTIVE 여부를 확인한다.
7. 잔액이 월 납입액 이상인지 확인한다.
8. 입출금계좌에서 출금한다.
9. 적금 전용 Account에 입금한다.
10. paidAmount, nextPaymentDate를 갱신한다.
11. 양쪽 계좌 거래내역을 저장한다.
```

실패 정책:

| 실패 상황 | 처리 |
| --- | --- |
| 출금 계좌 비활성 | `PAYMENT_FAILED` 처리 |
| 잔액 부족 | `PAYMENT_FAILED` 처리 |
| 처리 중 예외 | 로그 남기고 다음 건 계속 처리 |

## 8. 중도해지 정책

중도해지는 사용자 요청으로 처리한다.

```text
1. Deposit 또는 Installment를 본인 소유 기준으로 조회한다.
2. 상태가 ACTIVE인지 확인한다.
3. 입출금계좌와 예/적금 전용 Account를 ID 작은 순서대로 명시적 락 처리한다.
4. 중도해지 이자를 계산한다.
5. 예/적금 전용 Account에서 원금 또는 납입금을 출금한다.
6. 전용 Account 잔액이 0원인지 방어적으로 검증한다.
7. 예/적금 전용 Account를 CLOSED 처리한다.
8. 연결 입출금계좌에 원금/납입금 + 이자를 입금한다.
9. 양쪽 계좌 거래내역을 저장한다.
10. Saving 상태를 CANCELLED로 변경한다.
```

주의:

- 중도해지 후 전용 Account에 잔액이 남으면 안 된다.
- 잔액이 0원이 아니면 `ACCOUNT_BALANCE_NOT_ZERO` 예외로 막는다.

## 9. 만기 처리 정책

만기 처리는 사용자 요청 또는 스케줄러로 처리한다.

```text
1. Deposit 또는 Installment를 조회한다.
2. 상태가 ACTIVE인지 확인한다.
3. 만기일이 지났는지 확인한다.
4. 입출금계좌와 예/적금 전용 Account를 ID 작은 순서대로 명시적 락 처리한다.
5. 만기 이자를 계산한다.
6. 예/적금 전용 Account에서 원금 또는 납입금을 출금한다.
7. 전용 Account 잔액이 0원인지 방어적으로 검증한다.
8. 예/적금 전용 Account를 CLOSED 처리한다.
9. 연결 입출금계좌에 원금/납입금 + 이자를 입금한다.
10. 양쪽 계좌 거래내역을 저장한다.
11. Saving 상태를 MATURED로 변경한다.
```

스케줄러 처리 원칙:

- 서버가 여러 대여도 같은 스케줄러가 동시에 실행되지 않도록 분산락을 사용한다.
- 대상 ID를 먼저 조회하고, 한 건씩 별도 트랜잭션으로 처리한다.
- 한 건이 실패해도 전체 스케줄러가 중단되지 않도록 로그만 남기고 다음 건을 처리한다.

## 10. 락/동시성 정책

Saving은 돈이 이동하는 흐름이 많으므로 락 정책이 중요하다.

| 흐름 | 락 대상 |
| --- | --- |
| 예금 가입 | 출금용 입출금계좌 |
| 적금 가입 | 출금용 입출금계좌 |
| 적금 자동이체 | 입출금계좌 + 적금 전용 Account |
| 중도해지 | 입출금계좌 + 예/적금 전용 Account |
| 만기 | 입출금계좌 + 예/적금 전용 Account |
| 스케줄러 | Redis 분산락 |

두 Account를 함께 잠글 때:

```text
계좌 ID가 작은 계좌부터 잠근다.
```

이유:

- 여러 서버/트랜잭션이 같은 두 계좌를 처리해도 락 순서를 통일한다.
- 데드락 가능성을 줄인다.

## 11. 거래내역 표시 정책

예/적금 거래는 일반 입금/출금처럼만 보이면 사용자가 이해하기 어렵다.

거래내역 응답은 `counterpartyName`과 `displayName`을 분리한다.

| 필드 | 의미 |
| --- | --- |
| `counterpartyName` | 실제 거래 상대방 이름 |
| `displayName` | 화면에 보여줄 거래명 |

예/적금 내부 거래는 실제 상대방이 없으므로 `counterpartyName = null`이 자연스럽다.
대신 `displayName`으로 거래 의미를 내려준다.

| TransactionType | displayName |
| --- | --- |
| `SAVING_DEPOSIT_SIGNUP` | 예금 가입 |
| `SAVING_INSTALLMENT_SIGNUP` | 적금 가입 |
| `INSTALLMENT_PAYMENT` | 적금 자동납입 |
| `SAVING_CANCEL_REFUND` | 방향에 따라 예적금 해지입금/해지출금 |
| `SAVING_MATURITY` | 방향에 따라 예적금 만기입금/만기출금 |

## 12. 팀원 확인 체크리스트

Saving 관련 작업 전 아래를 확인한다.

- [ ] 예/적금 가입 시 출금 계좌가 `DEPOSIT` 타입인가?
- [ ] 가입/납입 전 출금 계좌 비밀번호를 검증하는가?
- [ ] 상품 금액 조건을 검증하는가?
- [ ] 예/적금 전용 Account를 생성하고 실제 잔액을 반영하는가?
- [ ] 양쪽 계좌 거래내역을 모두 저장하는가?
- [ ] 자동이체/중도해지/만기에서 양쪽 Account를 명시적으로 락 처리하는가?
- [ ] 중도해지/만기 후 전용 Account 잔액 0원을 검증하는가?
- [ ] 스케줄러는 분산락으로 중복 실행을 막는가?
- [ ] 예/적금 내부 거래는 `counterpartyName`을 임의로 덮어쓰지 않고 `displayName`을 사용하는가?
