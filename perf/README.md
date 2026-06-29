# 로컬 성능 및 부하 테스트

## 1. 백엔드 실행

MySQL/Redis를 먼저 실행한 뒤, 로컬 secret 파일이 준비된 상태에서 Spring Boot 애플리케이션을 실행합니다.

```bash
cd backend
docker compose up -d mysql redis
./gradlew bootRun
```

애플리케이션이 Prometheus 메트릭을 노출하는지 확인합니다.

```bash
curl http://localhost:8080/actuator/prometheus
```

## 2. Prometheus와 Grafana 실행

Prometheus:

```bash
prometheus \
  --config.file=perf/prometheus/prometheus.local.yml \
  --storage.tsdb.path=perf/prometheus/data \
  --web.listen-address=127.0.0.1:9090
```

Grafana:

```bash
grafana-server
```

Grafana는 `http://localhost:3001`에서 접속할 수 있습니다. 접속 후 Prometheus를 데이터 소스로 추가합니다.

```text
http://localhost:9090
```

## 3. k6 스모크 테스트 실행

테스트 사용자가 없다면 공개 health 엔드포인트부터 확인합니다.

```bash
k6 run perf/k6/health-smoke.js
```

기존 로컬 테스트 사용자가 있다면 인증이 필요한 사용자 플로우를 실행합니다. 기본 대상 서버는 `http://localhost:8080`입니다.

```bash
TEST_EMAIL=user@example.com \
TEST_PASSWORD=Password1234 \
k6 run perf/k6/banking-smoke.js
```

계좌 ID를 함께 전달하면 거래내역 조회까지 호출합니다.

```bash
TEST_EMAIL=user@example.com \
TEST_PASSWORD=Password1234 \
ACCOUNT_ID=1 \
k6 run perf/k6/banking-smoke.js
```

계좌가 여러 개라면 `ACCOUNT_IDS`에 쉼표로 구분해서 전달할 수 있습니다. 이 경우 각 iteration마다 계좌 하나를 선택해서 거래내역을 조회합니다.

```bash
TEST_EMAIL=user@example.com \
TEST_PASSWORD=Password1234 \
ACCOUNT_IDS=1,2 \
k6 run perf/k6/banking-smoke.js
```

## 4. k6 부하 테스트 실행

로그인을 포함한 전체 사용자 플로우를 부하 테스트합니다.

```bash
TEST_EMAIL=user@example.com \
TEST_PASSWORD=Password1234 \
ACCOUNT_IDS=1,2 \
TARGET_VUS=20 \
k6 run perf/k6/banking-load.js
```

로그인 비용을 제외하고 조회 API만 반복 호출하려면 아래 스크립트를 사용합니다. 로그인은 테스트 시작 전 `setup` 단계에서 한 번만 수행합니다.

```bash
TEST_EMAIL=user@example.com \
TEST_PASSWORD=Password1234 \
ACCOUNT_IDS=1,2 \
TARGET_VUS=20 \
k6 run perf/k6/banking-readonly-load.js
```

처음에는 작은 `TARGET_VUS`로 시작하고, Grafana에서 Spring, JVM, DB, Redis 메트릭을 확인하면서 점진적으로 값을 높입니다.

## 5. 기능별 k6 테스트 실행

테스트 계정 정보는 k6가 자동으로 읽지 않으므로, 로컬 전용 env 파일을 만든 경우 먼저 `source`로 현재 터미널에 로드합니다.

```bash
source perf/k6/.env.local
```

환율 조회, 환전 견적 생성을 테스트합니다. 환전 주문까지 실행하려면 실제 잔액이 변경될 수 있으므로 `RUN_EXCHANGE_ORDER=true`, `KRW_ACCOUNT_ID`, `FX_WALLET_ID`를 명시합니다.

```bash
TARGET_VUS=5 \
EXCHANGE_FROM=KRW \
EXCHANGE_TO=USD \
EXCHANGE_AMOUNT=1000 \
k6 run perf/k6/exchange-load.js
```

```bash
TARGET_VUS=2 \
RUN_EXCHANGE_ORDER=true \
KRW_ACCOUNT_ID=1 \
FX_WALLET_ID=1 \
k6 run perf/k6/exchange-load.js
```

주식 검색, 주식 목록/상세, 보유종목 조회를 읽기 전용으로 테스트합니다. 보유종목 조회까지 포함하려면 `INVESTMENT_ACCOUNT_IDS`를 넘깁니다.

```bash
TARGET_VUS=10 \
STOCK_KEYWORDS=삼성,현대 \
STOCK_CODES=005930 \
INVESTMENT_ACCOUNT_IDS=1,2 \
k6 run perf/k6/investment-readonly.js
```

계좌 이체 테스트는 실제 송금 데이터가 생성되므로 테스트 전용 계좌와 작은 금액으로만 실행합니다. 기본은 매 요청마다 새로운 `Idempotency-Key`를 사용합니다.

```bash
TARGET_VUS=2 \
SENDER_ACCOUNT_ID=1 \
RECEIVER_ACCOUNT_NUMBER=100200300002 \
ACCOUNT_PASSWORD=123456 \
TRANSFER_AMOUNT=1 \
k6 run perf/k6/transfer-load.js
```

같은 멱등성 키를 반복 사용해 중복 요청 처리 경로를 확인하려면 아래처럼 실행합니다.

```bash
TARGET_VUS=2 \
REUSE_IDEMPOTENCY_KEY=true \
IDEMPOTENCY_KEY=transfer-k6-fixed-key \
SENDER_ACCOUNT_ID=1 \
RECEIVER_ACCOUNT_NUMBER=100200300002 \
ACCOUNT_PASSWORD=123456 \
TRANSFER_AMOUNT=1 \
k6 run perf/k6/transfer-load.js
```

청년정책 검색/상세/추천을 테스트합니다. 추천 API는 RAG/LLM 호출 비용과 지연이 클 수 있으므로 낮은 VU부터 시작합니다.

```bash
TARGET_VUS=2 \
POLICY_AGE=25 \
POLICY_REGION=서울 \
POLICY_CATEGORY=금융･복지･문화 \
POLICY_KEYWORD=대출 \
POLICY_QUERY='대학생인데 월세나 전세자금 대출을 지원받고 싶어요.' \
k6 run perf/k6/policy-recommend.js
```
