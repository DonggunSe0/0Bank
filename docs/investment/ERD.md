# 주식 도메인 테이블 설계

## 1. stocks

종목 마스터 정보

### 컬럼

| 컬럼            | 설명             |
|---------------|----------------|
| stock_id      | PK             |
| stock_code    | 종목 코드          |
| stock_name    | 종목명            |
| market        | KOSPI / KOSDAQ |
| sector        | 업종             |
| currency_code | KRW            |
| nxt_available | NXT 거래 가능 여부   |
| listing_date  | 상장일            |
| status        | 거래 가능 여부       |
| created_at    | 생성일            |
| updated_at    | 수정일            |

### 데이터 출처

- KIS 종목 마스터 파일

### 동기화

- 애플리케이션 시작 시 수행
- 1일 1회 스케줄링 수행
- Upsert 기반 갱신

---

## 2. stock_prices

실시간 현재가 캐시

### 컬럼

| 컬럼             | 설명       |
|----------------|----------|
| stock_price_id | PK       |
| stock_id       | FK       |
| current_price  | 현재가      |
| change_price   | 전일 대비    |
| change_rate    | 등락률      |
| volume         | 거래량      |
| provider       | 데이터 제공자  |
| price_at       | 시세 기준 시각 |

### 목적

- 실시간 시세 조회 성능 향상
- WebSocket 수신 데이터 캐싱

### 특징

- KIS WebSocket 수신 데이터 기반 Upsert
- 현재가 조회용 캐시 테이블

---

## 3. stock_watchlists

관심 종목

### 컬럼

| 컬럼           | 설명  |
|--------------|-----|
| watchlist_id | PK  |
| user_id      | FK  |
| stock_id     | FK  |
| created_at   | 등록일 |

### 제약 조건

```sql
UNIQUE(user_id, stock_id)
```

### 비즈니스 제약

- 사용자당 최대 20개

---

## 4. market_holidays

거래 가능 여부 검증용

### 컬럼

| 컬럼           | 설명  |
|--------------|-----|
| id           | PK  |
| holiday_date | 휴장일 |
| market_type  | KRX |
| created_at   | 생성일 |
| updated_at   | 수정일 |

### 저장 대상

KIS 국내휴장일조회 API 응답 중 `opnd_yn = N`인 실제 휴장일만 저장한다.

`is_open`, `description`은 현재 코드 기준 저장하지 않는다.

### 목적

- 거래 가능 여부 검증
- 주문 가능 여부 검증

### 데이터 출처

- KIS 국내휴장일조회 API

### 동기화

- 1일 1회 수행

---

## 5. investment_accounts

모의 투자 계좌

### 컬럼

| 컬럼                    | 설명      |
|-----------------------|---------|
| investment_account_id | PK      |
| user_id               | FK      |
| account_number        | 계좌번호    |
| account_password_hash | 계좌 비밀번호 |
| account_mode          | PAPER   |
| cash_balance          | 예수금     |
| currency_code         | KRW     |
| status                | 상태      |
| created_at            | 생성일     |
| updated_at            | 수정일     |

### 특징

- 주식 거래 시 계좌 비밀번호 검증 필수

---

## 6. investment_orders

주문

### 컬럼

| 컬럼                    | 설명                  |
|-----------------------|---------------------|
| investment_order_id   | PK                  |
| investment_account_id | FK                  |
| stock_id              | FK                  |
| trade_type            | BUY / SELL          |
| price_type            | MARKET / LIMIT      |
| quantity              | 주문 수량               |
| remaining_quantity    | 잔여 주문 수량 스냅샷        |
| order_price           | 지정가 주문 요청가          |
| status                | 주문 상태               |
| idempotency_key       | 중복 요청 방지 UUID       |
| created_at            | 생성일                 |
| updated_at            | 수정일                 |

### 제약 조건

```sql
UNIQUE(investment_account_id, idempotency_key)
```

### idempotency_key 정책

클라이언트는 동일한 논리적 주문 요청에 동일한 UUID를 재사용해야 한다.

백엔드는 `investment_account_id + idempotency_key` 유니크 제약으로 동일 키 주문의 중복 생성을 방지한다.

### 주문 상태

```md
PENDING
PARTIALLY_FILLED
FILLED
CANCELLED
```

---

## 7. investment_order_executions

체결 내역

### 컬럼

| 컬럼                  | 설명    |
|---------------------|-------|
| id                  | PK    |
| investment_order_id | FK    |
| execution_price     | 체결가   |
| execution_quantity  | 체결 수량 |
| executed_at         | 체결 시각 |
| created_at          | 생성일   |
| updated_at          | 수정일   |

### 목적

부분 체결 지원

예시

```text
100주 주문

40주 체결
30주 체결
30주 체결
```

---

## 8. investment_holdings

보유 종목

### 컬럼

| 컬럼                    | 설명       |
|-----------------------|----------|
| investment_holding_id | PK       |
| investment_account_id | FK       |
| stock_id              | FK       |
| quantity              | 보유 수량    |
| average_price         | 평균 매입 단가 |
| updated_at            | 수정 시각    |

### 계산 값은 저장하지 않는다

제거 대상

```md
current_value
profit_amount
profit_rate
```

### 조회 시 계산

```md
평가금액 = quantity * 현재가
손익 = (현재가 - 평균단가) * 수량
수익률 = 손익 /매입금액
```

---

## 9. stock_master_sync_history (선택)

종목 마스터 파일 동기화 이력

### 컬럼

| 컬럼            | 설명      |
|---------------|---------|
| sync_id       | PK      |
| total_count   | 전체 종목 수 |
| success_count | 성공 건수   |
| failed_count  | 실패 건수   |
| synced_at     | 동기화 시각  |

### 목적

- 운영 모니터링
- 장애 분석

---

# 주문 처리 흐름

## 시장가 주문

```text
사용자 요청
↓
휴장일 검증
↓
정규장 검증
↓
최선 호가 확인
↓
체결
↓
investment_orders 저장
↓
investment_order_executions 저장
↓
investment_holdings 갱신
↓
investment_accounts 예수금 갱신
```

---

## 지정가 주문

```text
사용자 요청
↓
PENDING 상태 저장
↓
remaining_quantity 저장
↓
실시간 호가 이벤트 수신
↓
체결 조건 만족
↓
체결
↓
remaining_quantity 감소
↓
0이면 FILLED
```

---

# 최종 테이블 구성

```text
stocks

stock_prices

stock_watchlists

market_holidays

investment_accounts

investment_orders

investment_order_executions

investment_holdings

stock_master_sync_history (선택)
```
