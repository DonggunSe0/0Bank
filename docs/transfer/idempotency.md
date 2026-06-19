# 입금/송금 멱등성 정책

## 목적

입금/송금 API는 네트워크 재시도, 중복 클릭, 클라이언트 타임아웃 상황에서도 같은 금전 이동 요청이 중복 실행되지 않도록 `Idempotency-Key` 기반 멱등성 처리를 적용한다.

## 적용 API

```http
POST /api/v1/transfers/topUp
Idempotency-Key: deposit-20260618-0001

POST /api/v1/transfers
Idempotency-Key: transfer-20260618-0001
```

## Idempotency-Key 규칙

`Idempotency-Key` 헤더는 입금/송금 API에서 필수다.

```text
길이: 1~100자
허용 문자: 영문 대소문자, 숫자, ., _, :, -
정규식: ^[A-Za-z0-9._:-]{1,100}$
```

키가 없거나 공백이면 `IDEMPOTENCY_KEY_REQUIRED`를 반환한다.

키 형식이 올바르지 않으면 `IDEMPOTENCY_KEY_INVALID`를 반환한다.

## 요청 동일성 판단

같은 사용자의 같은 operation type과 같은 `Idempotency-Key`라도 요청 내용이 다르면 다른 요청으로 판단한다.

요청 동일성은 아래 값으로 만든 SHA-256 해시로 비교한다.

```text
입금:
accountId
amount
memo

송금:
senderAccountId
receiverAccountNumber
amount
memo
```

`memo`는 trim하지 않고 실제 처리 값 그대로 해시에 포함한다. 따라서 `"점심값"`과 `"점심값 "`은 다른 요청이다.

## 처리 흐름

```text
1. Idempotency-Key 검증
2. requestHash 생성
3. 기존 멱등성 레코드 조회
4. 없으면 PROCESSING 선점 저장
5. 입금/송금 처리
6. 성공 시 SUCCESS 저장 및 최초 응답 저장
7. 비즈니스 실패 시 FAILED 저장
```

`PROCESSING` 선점은 별도 트랜잭션(`REQUIRES_NEW`)으로 먼저 커밋한다. 따라서 송금 처리 중 비즈니스 예외가 발생해도 멱등성 레코드는 남을 수 있다.

동시 동일 키 요청에서 DB unique constraint 충돌이 발생하면 기존 레코드를 재조회하고 상태별 정책으로 변환한다. DB 예외를 API 응답으로 그대로 노출하지 않는다.

## 상태별 재요청 정책

| 상태 | 의미 | 같은 키 + 같은 요청 재요청 |
| --- | --- | --- |
| `PROCESSING` | 최초 요청이 처리 중 | `IDEMPOTENCY_REQUEST_PROCESSING` |
| `SUCCESS` | 처리 성공 및 최초 응답 저장 완료 | 저장된 최초 응답 반환 |
| `FAILED` | 비즈니스 예외로 처리 실패 확정 | `IDEMPOTENCY_REQUEST_FAILED` |
| `EXPIRED` | 장기 방치된 처리중 요청 만료 | `IDEMPOTENCY_REQUEST_EXPIRED` |

같은 키이지만 요청 내용이 다르면 상태와 무관하게 `IDEMPOTENCY_REQUEST_CONFLICT`를 반환한다.

## 실패 기록 정책

현재는 `BusinessException`이 발생한 경우에만 `FAILED`로 기록한다.

예시는 다음과 같다.

```text
잔액 부족
계좌 없음
비활성 계좌
본인 소유 계좌 아님
같은 계좌 송금
유효하지 않은 금액
```

예상하지 못한 시스템 예외는 실패 확정으로 기록하지 않는다. 성공/실패 여부가 모호할 수 있기 때문이다.

## PROCESSING 만료 정책

서버 종료, 장애, 예외 상황으로 `PROCESSING` 레코드가 장시간 남을 수 있다.

이 경우 같은 키 재요청이 계속 `PROCESSING`으로 막히므로, 스케줄러가 오래된 `PROCESSING` 레코드를 `EXPIRED`로 변경한다.

현재 스케줄러 정책은 다음과 같다.

```text
실행 주기: idempotency.cleanup-fixed-delay-ms, 기본 60000ms
만료 기준: 10분 이상 PROCESSING 상태로 남은 레코드
만료 후 상태: EXPIRED
```

`EXPIRED` 상태의 키는 재사용하지 않는다. 같은 키 재요청 시 `IDEMPOTENCY_REQUEST_EXPIRED`를 반환한다.

## 후속 작업

```text
환전/1원 인증 등 외부 연동 작업으로 멱등성 적용 범위 확장
FAILED 응답 재사용 여부 검토
EXPIRED 상태 재시도 허용 정책 검토
```
