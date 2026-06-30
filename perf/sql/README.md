# k6 EC2 MySQL Seed Data

이 디렉터리는 EC2 MySQL에 k6 부하테스트용 데이터를 넣기 위한 SQL 파일입니다.

주식과 청년정책은 외부 API/동기화 데이터가 기준이므로, 이 SQL에서는 가짜 `stocks`, `young_policy` 데이터를 만들지 않습니다.

## 실행 순서

app-data EC2에서 MySQL 컨테이너에 접속 가능한 위치에 SQL 파일을 복사한 뒤 아래 순서로 실행합니다.

```bash
docker exec -i mysql mysql -u root -p snaptix < 00_k6_cleanup.sql
docker exec -i mysql mysql -u root -p snaptix < 01_k6_users_accounts_transactions.sql
docker exec -i mysql mysql -u root -p snaptix < 02_k6_exchange.sql
docker exec -i mysql mysql -u root -p snaptix < 03_k6_investment.sql
docker exec -i mysql mysql -u root -p snaptix < 04_k6_young_policies.sql
```

`00_k6_cleanup.sql`은 재실행 가능하도록 k6 전용 사용자/계좌/지갑/투자계좌 데이터를 삭제합니다.

## 생성 데이터

- 테스트 사용자 20명
- 사용자당 입출금 계좌 2개
- 총 입출금 계좌 40개
- 계좌별 거래내역 20,000건
- 환전 테스트용 통화/환율 최소 데이터
- 환전 테스트용 외화 지갑
- 투자 테스트용 투자 계좌 2개
- EC2에 이미 존재하는 실제 `stocks`를 참조한 보유종목

## 생성하지 않는 데이터

- `stocks`: 외부 주식 API 동기화 데이터 사용
- `young_policy`: 외부 청년정책 API 동기화 데이터 사용

## AWS/DB 담당자 확인 필요

`03_k6_investment.sql`의 주식 코드는 아직 확정값이 아닙니다. 아래 값은 k6 투자 테스트를 작성하기 위한 후보값이며, EC2 MySQL의 실제 `stocks.stock_code`에 존재하는지 확인해야 합니다.

```text
확인/교체 필요:
005930,000660,035420,035720,005380,068270,373220,207940,051910,006400
```

AWS/DB 담당자는 아래를 확인합니다.

| 확인 항목 | 확인 방법 | 조치 |
|---|---|---|
| `stocks.stock_code` 존재 여부 | `03_k6_investment.sql` 실행 후 `matched_stock_count` 확인 | `10`보다 작으면 실제 존재하는 종목 코드로 SQL의 `stock_code` 후보를 교체 |
| k6 env의 `STOCK_CODES` | SQL에 최종 반영한 종목 코드와 비교 | `perf/k6/.env.local`의 `STOCK_CODES`에도 같은 코드 반영 |
| 보유종목 생성 여부 | `investment_holdings`에 `investment_account_id` 900001, 900002 데이터 생성 확인 | 생성 건수가 부족하면 종목 코드 또는 주식 마스터 적재 상태 확인 |

`04_k6_young_policies.sql`은 데이터를 생성하지 않고, 실제 적재된 청년정책 중 k6 테스트에 쓸 만한 정책을 조회합니다. 고정된 상세 조회 대상이 필요하면 조회 결과의 `id` 값을 k6 env의 `POLICY_IDS`에 넣습니다.

## k6 .env.local 예시

MacBook/IntelliJ 터미널에서 EC2 서버를 대상으로 k6를 실행할 때 `perf/k6/.env.local`에 아래 값을 넣습니다.

`TEST_EMAIL`/`SENDER_ACCOUNT_ID`/`RECEIVER_ACCOUNT_NUMBER`는 단일 사용자 이체 테스트용이고, `TEST_EMAILS`/`SENDER_ACCOUNT_IDS`/`RECEIVER_ACCOUNT_NUMBERS`는 20명/20개 출금 계좌로 분산 이체 부하를 줄 때 사용합니다.

```bash
export BASE_URL=https://api.0bank.shop

export TEST_EMAIL=k6-user1@0bank.test
export TEST_EMAILS=k6-user1@0bank.test,k6-user2@0bank.test,k6-user3@0bank.test,k6-user4@0bank.test,k6-user5@0bank.test,k6-user6@0bank.test,k6-user7@0bank.test,k6-user8@0bank.test,k6-user9@0bank.test,k6-user10@0bank.test,k6-user11@0bank.test,k6-user12@0bank.test,k6-user13@0bank.test,k6-user14@0bank.test,k6-user15@0bank.test,k6-user16@0bank.test,k6-user17@0bank.test,k6-user18@0bank.test,k6-user19@0bank.test,k6-user20@0bank.test
export TEST_PASSWORD='Password1!'
export ACCOUNT_IDS=900001,900002,900003,900004,900005,900006,900007,900008,900009,900010

export KRW_ACCOUNT_ID=900001
export FX_WALLET_ID=900001
export EXCHANGE_FROM=KRW
export EXCHANGE_TO=USD
export EXCHANGE_AMOUNT=1000

export INVESTMENT_ACCOUNT_IDS=900001
# 실제 EC2 stocks.stock_code 확인 후 아래 값을 교체하세요.
export STOCK_CODES=005930,000660,035420,035720
export STOCK_KEYWORDS=삼성,현대,카카오

export SENDER_ACCOUNT_ID=900001
export RECEIVER_ACCOUNT_NUMBER=900000000003
export SENDER_ACCOUNT_IDS=900001,900003,900005,900007,900009,900011,900013,900015,900017,900019,900021,900023,900025,900027,900029,900031,900033,900035,900037,900039
export RECEIVER_ACCOUNT_NUMBERS=900000000003,900000000005,900000000007,900000000009,900000000011,900000000013,900000000015,900000000017,900000000019,900000000021,900000000023,900000000025,900000000027,900000000029,900000000031,900000000033,900000000035,900000000037,900000000039,900000000001
export ACCOUNT_PASSWORD=123456
export TRANSFER_AMOUNT=1

export POLICY_AGE=25
export POLICY_REGION=서울
export POLICY_CATEGORY=금융･복지･문화
export POLICY_KEYWORD=대출
export POLICY_QUERY='대학생인데 월세나 전세자금 대출을 지원받고 싶어요.'

# 선택값입니다. 04_k6_young_policies.sql 조회 결과의 young_policy.id를 넣으면 정책 상세 조회 대상을 고정할 수 있습니다.
# export POLICY_IDS=1,2,3
```

## 실행 예시

```bash
source perf/k6/.env.local

TARGET_VUS=10 k6 run perf/k6/banking-readonly-load.js
TARGET_VUS=10 k6 run perf/k6/investment-readonly.js
TARGET_VUS=2 k6 run perf/k6/policy-recommend.js
```

쓰기 테스트는 실제 데이터가 변경됩니다. 처음에는 낮은 VU로 실행하세요.

```bash
source perf/k6/.env.local

TARGET_VUS=1 k6 run perf/k6/exchange-load.js
TARGET_VUS=1 k6 run perf/k6/transfer-load.js
```

## 주의

- `transfer-load.js`는 실제 송금 데이터와 거래내역을 생성합니다.
- `exchange-load.js`에서 `RUN_EXCHANGE_ORDER=true`를 추가하면 실제 환전 주문이 생성됩니다.
- BCrypt 기준 평문 비밀번호는 사용자 `Password1!`, 계좌 `123456`입니다.
