# 🏦 YouthBank (청년은행)

> **"흩어진 자산 관리와 청년 맞춤형 복지 혜택을 연결하는 통합 금융 플랫폼"**

현대 청년층이 겪고 있는 자산 관리 파편화와 공공 복지 정책의 정보 격차를 해소하기 위해 기획된 차세대 올인원 금융 가이드 플랫폼입니다. **통합 자산 관리**, **실시간 모의 주식 투자**, 그리고 **RAG 기반 청년정책 맞춤 추천 서비스**를 결합하여 높은 보안성과 뛰어난 편의성을 함께 제공합니다.

---

## 🏗️ 시스템 아키텍처 (System Architecture)

![시스템 아키텍처](./system_architecture_diagram.jpg)

---

## 🌟 핵심 도메인 및 기술적 해결 (Core Domains)

### 1. 🔒 사용자 인증 및 3단계 본인인증 (Auth & Identity)
- **PortOne 실시간 교차 검증**: 회원가입 시 전달받은 `identityVerificationId`로 PortOne 본인확인 API를 동기 호출하여 이름·생년월일·전화번호 조작을 원천 방지합니다.
- **Brute Force 방어**: Redis Lua 스크립트 기반으로 로그인 시도 횟수를 원자적으로 검사하며, 5회 연속 실패 시 패스워드 검증 연산을 스킵하고 즉시 계정을 차단합니다.
- **3단계 본인인증 파이프라인**:
  1. **1단계 (신분증 OCR)**: 주민등록번호 알고리즘 체크섬 검사로 위조 신분증 식별
  2. **2단계 (비동기 폴링)**: 백그라운드 OCR 작업 검증 완료 시까지 상태 전이 제한
  3. **3단계 (1원 송금 및 Redis 검증)**: 계좌로 1원을 전송 후 입금 메모 코드를 Redis에 TTL 5분 조건으로 저장. Redis Lua 스크립트를 통해 조회 대조와 동시에 값을 파기(단 1회만 매칭)하는 강력한 검증 수립

### 2. 💰 코어 뱅킹 및 예적금 상품 (Banking & Savings)
- **이체 데드락(Deadlock) 방지**: 두 계좌 간 동시 송금 시 발생하는 교착 상태를 해결하기 위해 **출금 계좌 ID와 입금 계좌 ID 크기 순서대로 정렬하여 Lock을 획득**하는 락 정렬 알고리즘을 사용합니다.
- **불변 거래 기록**: 거래 성공 시 `TRANSFER_OUT` / `TRANSFER_IN` 로그를 적재하고, 거래 시점의 잔액(`balanceAfter`)을 불변 컬럼에 영구 기록합니다.
- **자동이체 배치 프로세스**: Redis 분산 락 및 `SavingBatchProcessor`를 사용해 다중 인스턴스 환경에서 스케줄러가 적금 자동 납입을 안정적으로 처리합니다.

### 3. 💱 외화 환전 및 다국어 지갑 (Foreign Exchange)
- **5분 환율 락(Rate Lock)**: 실시간 환율 변동 위험에 대비하여 견적 요청 시점의 적용 환율을 5분간 보장하는 `exchange_quotes` 모델을 설계했습니다.
- **이중 기입식 복식 원장**: 단순 잔액 갱신에 그치지 않고, `fx_wallet_ledgers` 테이블에 거래 이전 잔액, 변동액, 거래 후 잔액 및 방향을 기록하여 완벽한 금융 감사 추적성을 확보했습니다.

### 4. 📈 주식 모의투자 및 실시간 시세 (Investment)
- **KIS API 연동 & 실시간 SSE 중계**: 한국투자증권(KIS) API로 주식 마스터 데이터를 정기 파싱해 동기화하고, 웹소켓 세션을 맺어 실시간 호가/체결 데이터를 클라이언트로 스트리밍합니다.
- **시장가/지정가 예약 주문**: FIFO(선입선출) 방식의 스레드 풀 체결 엔진을 기반으로, 지정 가격 도달 시 예약 주문을 자동으로 처리합니다.
- **멱등키 복합 유니크 제약**: `investment_account_id`와 `idempotency_key`를 묶어 DB 복합 유니크 제약을 적용함으로써 네트워크 지연으로 인한 중복 주문 체결을 완벽하게 예방합니다.

### 5. 🔌 CODEF 외부 자산 스크래핑 (CODEF Integration)
- **connectedId AES-256-GCM 암호화 & AAD 검증**: 타행 자산 스크랩을 대행하는 `connectedId` 노출 방지를 위해 양방향 암호화를 수행하고, 복호화 시 추가 인증 데이터(AAD) 검증을 추가해 데이터 변조를 즉시 차단합니다.
- **마스킹 격리 & HMAC Blind Index**: 전체 타행 계좌 리스트는 Redis에 5분간 임시 적재 후 브라우저에는 마스킹된 정보만 노출합니다. 중복 연동 비교 조회를 위해 평문 계좌번호를 단방향 HMAC-SHA-256 해시화한 Blind Index 컬럼으로 인덱싱하고 키 로테이션을 구축했습니다.

### 6. 🤖 RAG 기반 지능형 복지 정책 매칭 (Infrastructure & AI)
- **2단계 Reranking 알고리즘**: QueryDSL을 활용해 나이와 지역을 기반으로 1차 고속 필터링을 수행한 뒤, 고민글의 핵심 키워드 형태소 분석 및 지역 가중치 점수를 합산해 최적의 후보 10개 정책을 선정합니다.
- **Hallucination(환각) 차단 및 Index Mapping**: LLM(Gemini Pro)이 잘못된 세부 정책 정보나 가짜 링크를 생성하지 못하도록, 후보 10개 정책의 인덱스 정보만을 입력하여 공감 조언 답변을 JSON으로 출력받아 백엔드 내에서 실제 DB 정책 정보와 조인 후 반환합니다.
- **AI API 장애 시 Fallback 보장**: LLM API 호출 장애 발생 시 백엔드 단에서 Reranking 1위~4위의 원본 정책 카드 및 기본 추천 멘트를 조합해 `200 OK`로 에러 없이 리턴함으로써 100%의 가용성을 자랑합니다.

---

## 🛠️ 기술 스택 (Tech Stack)

### Backend
- **Language & Runtime**: Java 21
- **Framework**: Spring Boot 4.0.6, Spring Security, Spring Data JPA
- **Query / Search**: QueryDSL-JPA (v7.1)
- **Database**: MySQL 8.x, Flyway Migration, H2 (Local Test)
- **Cache & Lock**: Redis (Spring Data Redis), Redis Lua Script
- **Realtime Stream**: Spring WebSocket, SSE (Server-Sent Events)
- **Testing**: JUnit 5, Mockito, Testcontainers, JaCoCo (Coverage 80% Gate)
- **Monitoring**: Spring Actuator, Micrometer Prometheus

### Frontend
- **Framework**: Next.js 16.2.6 (React 19, App Router)
- **Language**: TypeScript
- **Styling**: Tailwind CSS v4.2.0, shadcn/ui, Base UI
- **Icons & UI Utilities**: Lucide React, Sonner (Toast), clsx, tailwind-merge

---

## 📂 프로젝트 구조 (Directory Structure)

```text
├── backend
│   ├── src/main/java/com/team10/backend
│   │   ├── domain                         # 도메인 주도 설계 (DDD) 구조
│   │   │   ├── account                    # 코어 뱅킹 입출금 계좌
│   │   │   ├── codef                      # CODEF API 연동 공통 모듈
│   │   │   ├── exAccount                  # 외부 자산(타행) 동기화
│   │   │   ├── exchange                   # 외화 지갑 및 실시간 환전
│   │   │   ├── investment                 # 주식 모의투자 (마켓 홀리데이, 주식, 거래)
│   │   │   ├── saving                     # 예적금 상품 및 만기/자동이체 배치
│   │   │   ├── transaction                # 불변 거래내역 관리
│   │   │   ├── transfer                   # 계좌 송금 및 락 제어
│   │   │   ├── user                       # 회원 관리 및 3단계 본인인증
│   │   │   └── youngPolicy                # RAG 청년 정책 추천 (Gemini RAG)
│   │   └── global                         # 공통 예외 처리, 보안, 설정 파일
│   └── src/test/java/com/team10/backend   # 단위/통합/동시성/멱등성 QA 테스트 코드
│
└── frontend
    ├── app
    │   ├── (auth)                         # 로그인/회원가입 라우트
    │   └── (app)                          # 본 서비스 페이지 라우트
    │       ├── dashboard                  # 종합 대시보드
    │       ├── accounts                   # 내 계좌 관리
    │       ├── identity                   # 3단계 본인인증
    │       ├── savings                    # 예적금 가입
    │       ├── exchange                   # 외화 환전 지갑
    │       ├── stocks                     # 실시간 주식 호가판
    │       ├── investment-accounts        # 주식 계좌
    │       ├── transactions               # 거래내역 필터링
    │       └── youth-policies             # 청년 정책 고민 매칭 AI
    └── components                         # shadcn/ui 및 공통 컴포넌트
```

---

## Branch Naming Convention

- `main`: 배포 브랜치
- `develop`: 개발 통합 브랜치

### 작업 브랜치

- `feature/{기능명}`: 기능 개발
- `fix/{수정내용}`: 버그 수정
- `hotfix/{수정내용}`: 배포 환경 긴급 수정
- `refactor/{대상}`: 기능 변경 없는 코드 개선
- `docs/{문서명}`: 문서 수정
- `chore/{작업명}`: 설정, 빌드, 환경 구성 작업
- `test/*`: 테스트 코드 추가 또는 수정

### 예시

- `feature/auth`
- `feature/transfer`
- `fix/transfer-validation`
- `hotfix/deploy-error`
- `refactor/account-service`
- `docs/readme`
- `chore/github-actions`
- `test/transfer`

## Flyway Migration Convention

- 마이그레이션 파일 위치는 `backend/src/main/resources/db/migration`을 사용한다.
- 파일명은 `V{버전}__{설명}.sql` 형식을 지킨다. 예: `V2__add_user_last_login_at.sql`
- 버전과 설명 사이에는 언더스코어 2개(`__`)를 사용한다.
- 한 번 `main` 또는 `develop`에 반영된 마이그레이션 파일은 수정하지 않는다.
- 이미 반영된 스키마를 변경해야 하면 기존 파일을 고치지 말고 새 버전 파일을 추가한다.
- dev/prod 환경에서는 Flyway가 스키마 생성과 변경을 담당하고, Hibernate는 `ddl-auto: validate`만 사용한다.
- 운영 DB에 직접 DDL을 수동 적용하지 않는다. 필요한 변경은 반드시 Flyway SQL로 남긴다.
- `flyway_schema_history` 테이블은 Flyway 관리 테이블이므로 직접 수정하거나 삭제하지 않는다.
- nullable 변경, unique/index 추가, FK 변경, 금액 컬럼 precision/scale 변경은 PR에서 반드시 리뷰한다.
- 기존 데이터가 있는 테이블에 `NOT NULL` 컬럼을 추가할 때는 `NULL 허용 추가 -> 데이터 보정 -> NOT NULL 변경` 순서로 작성한다.
