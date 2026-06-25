# CODEF `account-inquiry` 외부계좌 연동 13단계 실행 계획

작성일: 2026-06-22  
대상: Spring Boot 백엔드의 실제 CODEF 외부계좌 조회·연동  
사용 키: `codef.account-inquiry`  
제외 키: `codef.one-won-transfer`

## 1. 목표와 완료 기준

사용자가 은행 인증정보를 한 번 입력하면 백엔드가 CODEF에 인증수단을 등록하고, 발급된 `connectedId`로 보유계좌를 조회한 뒤 사용자가 선택한 계좌만 `external_account`에 저장한다.

완료 조건은 다음과 같다.

- 모든 CODEF 계좌조회 호출은 `codef.account-inquiry` 하위 키만 사용한다.
- `codef.one-won-transfer` 키와 1원 송금 코드는 호출하지 않는다.
- 은행 ID/PW와 원본 계좌번호는 DB·로그·API 응답에 남지 않는다.
- 프론트가 계좌번호, 잔액, 계좌종류를 임의로 만들어 연동할 수 없다.
- 동일 사용자·기관·계좌는 중복 저장되지 않는다.
- CODEF 실패, 추가인증 필요, 타임아웃을 우리 서비스의 일관된 오류 응답으로 변환한다.
- DEMO 통합 테스트 후 실제 서비스 키로 전환하는 절차가 재현 가능하다.

## 2. 현재 저장소 상태

이미 있는 기반:

- `CodefAuthClient`: OAuth Access Token 발급 및 캐시
- `ExAccount`, `ExAccountSyncService`: 계좌번호 HMAC/마스킹 및 외부계좌 upsert
- `ExAccountController`: 저장된 외부계좌 목록·상세 조회
- `ExAccountTransactionService`: 거래내역 upsert
- `application-secret.yml`: `account-inquiry`, `one-won-transfer` 키 영역 분리

실제 연동 전에 메워야 하는 간극:

- `CodefAuthClient`는 현재 `${codef.client-id}`, `${codef.client-secret}`을 읽는다. 이를 `${codef.account-inquiry.client-id}`, `${codef.account-inquiry.client-secret}`으로 변경해야 한다.
- CODEF 계정등록, `connectedId` 저장, 개인 보유계좌 조회 클라이언트가 현재 코드에 없다.
- 기존 `POST /candidates`와 `POST /link`는 프론트가 계좌 스냅샷을 직접 보내므로 실제 CODEF 연동의 신뢰 경계로 사용할 수 없다.
- `connectedId`와 연결 기관을 사용자별로 관리하는 영속 모델이 없다.
- CODEF 외부계좌 응답 코드, 추가인증, URL 디코딩을 `domain.codef.exAccount` 안에서 처리하는 계층이 없다.

## 3. 목표 흐름

```text
사용자
  -> 우리 서버: 은행 인증정보 제출
  -> CODEF: 계정등록
  <- CODEF: connectedId
  -> 우리 DB: connectedId 암호화 저장
  -> CODEF: connectedId + organization으로 보유계좌 조회
  <- CODEF: 계좌 목록
  -> 우리 서버: 원본 응답을 단기 후보 세션으로 보관
  <- 사용자: 마스킹된 후보 목록 + candidateToken
  -> 우리 서버: candidateToken + 선택 인덱스 제출
  -> 우리 DB: 선택 계좌 HMAC + 마스킹 + 스냅샷 저장
  <- 사용자: 연동 완료 계좌
```

중요한 신뢰 경계:

- 브라우저는 CODEF `clientId`, `clientSecret`, `publicKey`, `connectedId`를 알지 못한다.
- 브라우저는 원본 계좌번호나 잔액을 다시 제출하지 않는다.
- 서버는 CODEF에서 직접 받은 후보만 저장한다.

### 3.1 CODEF API별 하위 도메인 분리

CODEF 기능은 API 상품별로 하위 도메인을 분리한다. 이번 `account-inquiry` 외부계좌 연동 코드는 모두 다음 위치에 둔다.

```text
backend/src/main/java/com/team10/backend/domain/codef/exAccount
```

분리 원칙:

| CODEF 기능 | 코드 위치 | 사용 설정 |
| --- | --- | --- |
| 외부계좌 조회·연동 | `domain.codef.exAccount` | `codef.account-inquiry` |
| 1원 송금 | 외부계좌와 분리된 CODEF 하위 도메인 | `codef.one-won-transfer` |
| OCR | 외부계좌와 분리된 CODEF 하위 도메인 | OCR 전용 설정 |

각 하위 도메인은 자신의 `Properties`, OAuth 클라이언트, `RestClient`, 요청·응답 DTO, 오류 매핑을 독립적으로 가진다. CODEF API마다 키와 권한, base URL, 타임아웃, 오류 계약이 다를 수 있으므로 범용 `CodefAuthClient`나 하나의 공용 `RestClient`에 모든 API를 연결하지 않는다.

`domain.codef.exAccount` 내부 패키지 책임은 다음과 같다.

| 패키지 | 책임 |
| --- | --- |
| `domain.codef.exAccount.config` | `account-inquiry` 설정 바인딩, 전용 `RestClient` 빈 |
| `domain.codef.exAccount.client` | 전용 OAuth, CODEF 계정등록, 보유계좌 HTTP 호출 |
| `domain.codef.exAccount.controller` | CODEF 연결·후보조회·선택연동 API |
| `domain.codef.exAccount.dto.req` | 우리 API가 받는 연결·추가인증·선택 요청 record |
| `domain.codef.exAccount.dto.res` | 마스킹된 후보·연결상태 응답 record |
| `domain.codef.exAccount.dto.internal` | CODEF 응답을 정규화한 내부 record. 외부 API에 직접 노출하지 않음 |
| `domain.codef.exAccount.entity` | 사용자별 CODEF 외부계좌 연결 영속 모델 |
| `domain.codef.exAccount.repository` | CODEF 연결정보 조회·저장 |
| `domain.codef.exAccount.service` | 계정등록부터 후보 생성, 선택 계좌 연동까지 유스케이스 조립 |
| `domain.codef.exAccount.mapper` | CODEF 필드와 내부 계좌 스냅샷 간 변환 |
| `domain.codef.exAccount.exception` | 외부계좌 조회 전용 결과코드·통신 예외 변환 |
| `domain.codef.exAccount.type` | 연결 상태 등 CODEF 외부계좌 전용 enum |
| `domain.codef.exAccount.crypto` | 비밀번호 RSA 암호화, `connectedId` 저장 암복호화 |
| `domain.codef.exAccount.store` | Redis 후보 토큰 저장소 |

기존 `domain.exAccount`에는 CODEF HTTP 호출, `connectedId`, CODEF 결과코드 처리 코드를 넣지 않는다. 이 도메인은 다음 책임만 유지한다.

- 선택이 끝난 외부계좌 스냅샷 저장
- 계좌번호 HMAC·마스킹
- 동일 계좌 upsert
- 저장된 외부계좌 목록·상세·거래내역 조회

의존 방향은 다음과 같다.

```text
CodefExAccountService
  -> CodefExAccountAuthClient
  -> CodefExAccountClient
  -> CodefExAccountCandidateStore
  -> ExAccountSyncService
```

`ExAccountSyncService`가 `Codef*` 타입에 의존하면 안 된다. `domain.codef.exAccount.mapper`가 CODEF 응답을 현재 `ExAccountLinkReq` 또는 향후 추가할 중립적인 외부계좌 저장 command로 변환한 뒤 호출한다.

컨트롤러 URL에 `external-accounts`가 포함되더라도 Java 클래스의 위치는 `domain.codef.exAccount.controller`로 한다. URL은 사용자 기능을 표현하고, 패키지는 CODEF 외부계좌 연동의 소유권을 표현한다.

## 4. 13단계 실행 계획

### 1단계. 연동 범위와 키 경계를 고정한다

`account-inquiry`를 계좌조회 전용 설정으로 선언하고 `one-won-transfer`와 완전히 분리한다.

권장 설정 구조:

```yaml
codef:
  account-inquiry:
    service-type: ${CODEF_ACCOUNT_INQUIRY_SERVICE_TYPE:DEMO}
    client-id: ${CODEF_ACCOUNT_INQUIRY_CLIENT_ID}
    client-secret: ${CODEF_ACCOUNT_INQUIRY_CLIENT_SECRET}
    public-key: ${CODEF_ACCOUNT_INQUIRY_PUBLIC_KEY}
    base-url: ${CODEF_ACCOUNT_INQUIRY_BASE_URL:https://development.codef.io}
    account-list-path: /v1/kr/bank/p/account/account-list
    bank-transaction-path: /v1/kr/bank/p/account/transaction-list
```

산출물:

- `CodefExAccountProperties`
- `@ConfigurationProperties(prefix = "codef.account-inquiry")`
- `one-won-transfer` 패키지와 빈 이름이 겹치지 않는 전용 설정

완료 조건:

- 계좌조회 테스트에서 `one-won-transfer` 설정을 제거해도 애플리케이션이 정상 동작한다.

### 2단계. 설정 검증과 시크릿 주입을 만든다

`client-id`, `client-secret`, `public-key`, `base-url`에 `@NotBlank`를 적용하고 운영 프로필에서 누락 시 시작을 실패시킨다. 실제 값은 환경변수나 Secret Manager로만 주입한다.

주의:

- 현재 `application-secret.yml`에 실제 키가 있다면 Git 추적 여부를 즉시 확인하고, 추적된 적이 있으면 키를 폐기·재발급한다.
- `account-hash-secret`은 CODEF 키가 아니라 계좌번호 HMAC 키다. 별도 이름인 `security.hmac.secret`으로 관리한다.
- 운영 로그에 Spring 바인딩 값, HTTP body, Authorization 헤더가 출력되지 않게 한다.

완료 조건:

- 키 누락 시작 실패 테스트가 통과한다.
- 저장소 검색으로 실키·은행 비밀번호가 발견되지 않는다.

### 3단계. 계좌조회 전용 OAuth/HTTP 기반을 분리한다

기존 범용 `CodefAuthClient`를 재사용하지 않고 `domain.codef.exAccount.client.CodefExAccountAuthClient`를 만든다. 이 클라이언트는 `codef.account-inquiry` 설정만 주입받는다. OAuth 토큰은 `client_credentials`, `scope=read`로 발급하고 만료 5분 전에 갱신한다.

HTTP 클라이언트 요구사항:

- 연결 타임아웃과 읽기 타임아웃을 별도 설정
- `Authorization: Bearer <token>`
- `Content-Type: application/json`
- 응답 URL 디코딩 후 JSON 파싱
- 토큰·요청 body 전체 로깅 금지

완료 조건:

- 동시 토큰 요청 시 실제 OAuth 호출이 한 번만 발생하는 테스트가 통과한다.

### 4단계. 기관 연결 요청 계약을 정의한다

우리 API:

```http
POST /api/v1/codef/external-accounts/connections
```

ID/PW 방식 예시 요청:

```json
{
  "organization": "0004",
  "businessType": "BK",
  "clientType": "P",
  "loginType": "1",
  "loginId": "사용자 입력",
  "password": "사용자 입력",
  "birthDate": "YYMMDD"
}
```

검증 규칙:

- 서버가 지원하는 기관코드 allowlist 적용
- 현재 구현 범위가 ID/PW라면 `businessType=BK`, `clientType=P`, `loginType=1` 고정
- 기관별 필수값은 전략/정책 객체로 검증
- DTO의 `toString()` 또는 예외 메시지에 `loginId`, `password`가 노출되지 않게 한다.

완료 조건:

- 잘못된 기관, 로그인 방식, 생년월일 형식이 CODEF 호출 전에 거절된다.

### 5단계. 은행 인증정보를 CODEF 공개키로 암호화한다

계정등록 직전에 비밀번호를 `account-inquiry.public-key`로 RSA 암호화한다. 평문은 요청 처리 메모리 안에서만 사용하고 필드·캐시·DB에 저장하지 않는다.

CODEF 계정등록용 `accountList` 구성 예:

```json
{
  "accountList": [
    {
      "countryCode": "KR",
      "businessType": "BK",
      "clientType": "P",
      "organization": "0004",
      "loginType": "1",
      "id": "사용자 입력 ID",
      "password": "RSA 암호문",
      "birthDate": "YYMMDD"
    }
  ]
}
```

완료 조건:

- CODEF 전송 직전 body의 `password`가 평문과 다르다는 테스트가 통과한다.
- 요청/예외 로그에 평문 인증정보가 없다.

### 6단계. CODEF 계정등록을 호출하고 `connectedId`를 받는다

계정등록 API를 호출해 사용자 인증수단을 CODEF에 등록한다. 성공은 HTTP 상태만으로 판단하지 않고 CODEF `result.code == CF-00000`과 `data.connectedId` 존재를 함께 검증한다.

필요 클래스 예:

```text
domain/codef/exAccount/client/CodefExAccountClient.java
domain/codef/exAccount/client/CodefExAccountResponseDecoder.java
domain/codef/exAccount/dto/internal/CodefExAccountConnectionCommand.java
domain/codef/exAccount/dto/internal/CodefExAccountConnectionResult.java
domain/codef/exAccount/exception/CodefExAccountErrorMapper.java
```

추가인증 응답이 오면 실패로 뭉개지 말고 `CODEF_ADDITIONAL_AUTH_REQUIRED`와 추가 입력 메타데이터를 반환한다. 지원 전에는 명시적인 미지원 오류로 종료한다.

완료 조건:

- 성공, 자격증명 오류, 추가인증, CODEF 시스템 오류 fixture 테스트가 통과한다.

### 7단계. 사용자별 CODEF 연결정보를 안전하게 저장한다

별도 엔티티를 만든다.

```text
CodefExAccountConnection
- id
- userId
- organization
- connectedIdEncrypted
- status: ACTIVE | REAUTH_REQUIRED | REVOKED
- lastSyncedAt
- createdAt / updatedAt
```

`connectedId`는 외부 자원 접근에 쓰이는 식별자이므로 평문 저장보다 애플리케이션 레벨 암호화를 권장한다. 검색은 `userId + organization`으로 하고 복호화는 CODEF 호출 직전에만 한다.

제약조건:

```text
UNIQUE(user_id, organization)
```

완료 조건:

- 다른 사용자의 연결정보를 조회할 수 없다.
- DB 덤프에 평문 `connectedId`가 없다.

### 8단계. `connectedId`로 개인 보유계좌를 조회한다

CODEF 개인 은행 보유계좌 상품을 호출한다.

```text
POST {baseUrl}/v1/kr/bank/p/account/account-list
```

요청 핵심값:

```json
{
  "connectedId": "서버에서 복호화한 값",
  "organization": "0004"
}
```

환경별 base URL과 서비스 타입은 CODEF 콘솔 계약을 기준으로 구분한다. 경로는 설정 가능하게 두되 임의의 사용자 입력을 URL에 결합하지 않는다.

완료 조건:

- 연결된 사용자/기관 조합으로만 조회된다.
- 타임아웃과 5xx는 제한된 횟수로만 재시도하며 인증 오류는 재시도하지 않는다.

### 9단계. CODEF 응답을 내부 계좌 모델로 정규화한다

CODEF 응답을 바로 JPA 엔티티로 만들지 말고 중립 모델로 변환한다.

```text
CodefAccountSnapshot
- organization
- accountNumber
- accountName
- accountAlias
- assetType
- balance
- withdrawableAmount
- openedAt
- maturityAt
- lastTransactionAt
```

매핑 원칙:

- 금액 문자열은 `BigDecimal`
- 날짜 형식이 비어 있으면 `null`
- 알 수 없는 계좌유형은 `UNKNOWN`
- 필수 계좌번호가 없는 항목은 전체 요청을 깨지 않고 제외하되 메트릭을 남김
- CODEF 원문 필드명과 우리 enum 변환표를 단위 테스트로 고정

완료 조건:

- 수시입출금·예적금·대출·외화·펀드 fixture가 내부 타입으로 정확히 변환된다.

### 10단계. 서버 소유의 단기 후보 세션을 만든다

CODEF에서 받은 원본 후보를 Redis에 짧게 보관한다.

```text
key: codef:candidate:{userId}:{randomToken}
value: CodefAccountSnapshot 목록
TTL: 5분 권장
```

프론트 응답에는 다음만 준다.

```json
{
  "candidateToken": "일회용 랜덤 토큰",
  "expiresInSeconds": 300,
  "accounts": [
    {
      "index": 0,
      "organization": "0004",
      "accountNumberMasked": "123456****7890",
      "accountName": "KB Star 통장",
      "assetType": "DEMAND",
      "balance": 1500000,
      "linked": false
    }
  ]
}
```

원본 계좌번호는 응답하지 않는다. 후보 토큰은 사용자 ID에 귀속하고, 한 번 연동에 사용하면 폐기한다.

완료 조건:

- 다른 사용자 토큰, 만료 토큰, 재사용 토큰이 모두 거절된다.

### 11단계. 사용자가 선택한 계좌만 연동 저장한다

우리 API:

```http
POST /api/v1/codef/external-accounts/link
```

요청:

```json
{
  "candidateToken": "...",
  "selectedIndexes": [0, 2]
}
```

서버는 Redis 후보를 읽어 기존 `ExAccountSyncService`의 보호·upsert 로직을 호출한다.

저장 규칙:

- 원본 계좌번호 정규화
- `HMAC-SHA-256` 해시 생성
- 화면용 마스킹 생성
- `user_id + organization + account_number_hash`로 중복 방지
- 성공 후 후보 토큰 즉시 삭제
- 여러 계좌 저장은 하나의 트랜잭션으로 처리

기존 `POST /candidates`, `POST /link` 공개 API는 개발용으로 폐기하거나 관리자/내부 전용으로 제한한다. 실제 사용자 연동에서 신뢰하면 안 된다.

완료 조건:

- 동일 계좌 재연동은 새 row를 만들지 않고 스냅샷만 갱신한다.
- 프론트가 잔액이나 계좌번호를 조작할 입력 필드가 없다.

### 12단계. 프론트 연동 UX와 재인증 흐름을 완성한다

화면 순서:

```text
은행 선택 -> 인증정보 입력 -> 조회 중 -> 후보 목록 -> 계좌 선택 -> 연동 완료
```

필수 UX:

- 중복 제출 방지
- CODEF 호출 타임아웃 안내
- 자격증명 오류와 시스템 오류 구분
- 추가인증 필요 상태 표시
- 이미 연동된 계좌 비활성화
- 비밀번호 입력값 즉시 초기화
- 새로고침 시 비밀번호·후보 원문 복원 금지

완료 조건:

- 브라우저 개발자 도구의 응답·스토리지에서 원본 계좌번호, `connectedId`, CODEF 키가 보이지 않는다.

### 13단계. 테스트, 관측성, DEMO 검증 후 운영 전환한다

테스트 순서:

1. 설정 바인딩·키 분리 테스트
2. RSA 암호화 단위 테스트
3. CODEF 응답 디코딩·오류 매핑 테스트
4. 계정등록/보유계좌 WireMock 통합 테스트
5. 후보 토큰 소유권·TTL·일회성 테스트
6. HMAC·마스킹·중복 연동 회귀 테스트
7. 컨트롤러 인증/인가 테스트
8. CODEF DEMO 실제 계정 1개로 smoke test
9. 로그·DB·Redis 민감정보 점검
10. 운영 키와 운영 base URL로 전환 후 제한된 사용자 canary

필수 메트릭:

```text
codef.oauth.requests
codef.connection.requests{organization,result}
codef.account_list.requests{organization,result}
codef.account_list.duration
codef.candidate.expired
external_account.linked
```

로그에는 `requestId`, 사용자 내부 ID의 비식별 추적값, 기관코드, CODEF 결과코드, 처리시간만 남긴다. 은행 ID/PW, 원본 계좌번호, `connectedId`, Access Token, CODEF 키는 금지한다.

운영 전환 중지 조건:

- CODEF 계약의 실제 계정등록 경로·추가인증 규격이 콘솔 상품 문서와 일치하지 않음
- 운영 키 권한에 개인 은행 보유계좌 상품이 없음
- 로그 또는 저장소에서 민감정보가 발견됨
- DEMO 환경에서 후보 조회부터 선택 저장까지 end-to-end가 통과하지 않음

## 5. 권장 우리 API 계약

| 목적 | Method | Path | 비고 |
| --- | --- | --- | --- |
| CODEF 계정등록 + 후보조회 | POST | `/api/v1/codef/external-accounts/connections` | 비밀번호는 이 요청에서만 수신 |
| 추가인증 완료 | POST | `/api/v1/codef/external-accounts/connections/{connectionId}/additional-auth` | 실제 CODEF 규격 확인 후 구현 |
| 후보 재조회 | POST | `/api/v1/codef/external-accounts/candidates/refresh` | 저장된 `connectedId` 사용 |
| 선택 계좌 연동 | POST | `/api/v1/codef/external-accounts/link` | 후보 토큰 + 인덱스만 수신 |
| 연동 계좌 목록 | GET | `/api/v1/external-accounts/accounts` | 기존 API 재사용 |
| 연동 계좌 상세 | GET | `/api/v1/external-accounts/accounts/{id}` | 기존 API 재사용 |

첫 연결 요청에서 계정등록과 후보조회까지 연속 수행하면 프론트 왕복을 줄일 수 있다. 단, 추가인증이 필요한 기관은 중간 상태를 응답해야 한다.

CODEF를 직접 호출하는 API는 `/api/v1/codef/external-accounts` prefix로 분리한다. CODEF 호출 없이 우리 DB만 조회하는 기존 계좌 목록·상세 API는 `/api/v1/external-accounts`를 유지한다. 이 구분으로 외부 연동 API와 내부 조회 API의 장애·권한·로깅 정책을 독립 적용한다.

## 6. 코드 컨벤션과 권장 구현 순서

### 6.1 현재 프로젝트 컨벤션 적용

새 CODEF 코드는 기존 백엔드 스타일을 따른다.

- 기본 패키지: `com.team10.backend.domain.codef.exAccount`
- 프로젝트의 기존 외부계좌 도메인 명칭과 합의된 경로에 맞춰 `exAccount`를 사용한다. 그 아래 `client`, `service`, `type` 등의 패키지는 소문자로 작성한다.
- 요청·응답 DTO는 불변 `record`로 작성하고 각각 `dto.req`, `dto.res`에 둔다.
- API DTO에는 Bean Validation과 Swagger `@Schema`를 함께 적용한다.
- 생성자 주입은 `@RequiredArgsConstructor`를 사용하고 필드 주입은 사용하지 않는다.
- 서비스는 `@Service`, 조회 전용 서비스/메서드는 `@Transactional(readOnly = true)`, 저장 유스케이스는 `@Transactional`을 사용한다.
- 엔티티는 `BaseEntity`를 상속하고 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 사용한다.
- enum은 `domain.codef.exAccount.type`에 두고 DB 저장 시 `@Enumerated(EnumType.STRING)`을 사용한다.
- Repository는 `JpaRepository`를 확장하고 사용자 소유권 조건을 쿼리 메서드에 포함한다.
- 비즈니스 실패는 `CodefExAccountErrorCode implements ErrorCode`와 `BusinessException`으로 통일한다.
- CODEF HTTP 라이브러리 예외는 controller까지 그대로 올리지 않고 `domain.codef.exAccount.exception`에서 변환한다.
- 컨트롤러는 요청 검증과 응답 상태 결정만 담당하며 CODEF 응답 파싱이나 JPA 접근을 하지 않는다.
- `client`는 HTTP 통신과 CODEF 원문 응답 반환까지만 담당하고 비즈니스 저장 트랜잭션을 열지 않는다.
- 로그는 Lombok `@Slf4j`를 사용하되 문자열 연결 대신 placeholder를 사용하고 민감정보를 인자로 전달하지 않는다.
- 매직 문자열인 서비스 타입, 연결 상태, 지원 로그인 방식은 설정 또는 enum/상수로 이동한다.
- 공개 클래스와 메서드는 역할이 이름으로 충분히 드러나게 하고, 구현 설명보다 보안상 이유나 CODEF 제약을 JavaDoc으로 남긴다.

명명 규칙:

| 종류 | 규칙 | 예시 |
| --- | --- | --- |
| Controller | 기능 + `Controller` | `CodefExAccountController` |
| Service | 유스케이스 + `Service` | `CodefExAccountService` |
| OAuth Client | 기능 + `AuthClient` | `CodefExAccountAuthClient` |
| 상품 Client | 기능 + `Client` | `CodefExAccountClient` |
| 설정 | 기능 + `Properties`/`Config` | `CodefExAccountProperties` |
| 요청 DTO | 기능 + `Req` | `CodefExAccountConnectionCreateReq` |
| 응답 DTO | 기능 + `Res` | `CodefExAccountCandidateRes` |
| 내부 DTO | 의미 중심 record | `CodefExAccountSnapshot` |
| Entity | 도메인 명사 | `CodefExAccountConnection` |
| Error enum | 도메인 + `ErrorCode` | `CodefExAccountErrorCode` |

한 클래스에 계정등록, 보유계좌 파싱, Redis 저장, 외부계좌 DB 저장을 모두 넣지 않는다. 다만 인터페이스가 하나뿐인 클래스마다 불필요한 인터페이스를 만들거나 추상 계층을 추가하지도 않는다.

### 6.2 권장 디렉터리 구조

```text
domain/codef/exAccount/
├── client/
│   ├── CodefExAccountAuthClient.java
│   ├── CodefExAccountClient.java
│   └── CodefExAccountResponseDecoder.java
├── config/
│   ├── CodefExAccountProperties.java
│   └── CodefExAccountRestClientConfig.java
├── controller/
│   └── CodefExAccountController.java
├── crypto/
│   ├── CodefExAccountPasswordEncryptor.java
│   └── CodefConnectedIdEncryptor.java
├── dto/
│   ├── internal/
│   │   └── CodefExAccountSnapshot.java
│   ├── req/
│   │   ├── CodefExAccountConnectionCreateReq.java
│   │   └── CodefExAccountLinkReq.java
│   └── res/
│       ├── CodefExAccountCandidateRes.java
│       └── CodefExAccountConnectionRes.java
├── entity/
│   └── CodefExAccountConnection.java
├── exception/
│   ├── CodefExAccountErrorCode.java
│   └── CodefExAccountErrorMapper.java
├── mapper/
│   └── CodefExAccountSnapshotMapper.java
├── repository/
│   └── CodefExAccountConnectionRepository.java
├── service/
│   └── CodefExAccountService.java
├── store/
│   └── CodefExAccountCandidateStore.java
└── type/
    └── CodefExAccountConnectionStatus.java
```

기존 인증·OCR·1원 송금 코드는 이번 작업에서 수정하거나 이동하지 않는다. `domain.codef.exAccount`는 `account-inquiry` 키, OAuth 토큰 캐시, HTTP 설정을 독립 보유한다. 다른 CODEF API와는 코드 중복보다 자격증명 혼용 방지를 우선한다.

### 6.3 구현 순서

1. `config`의 `account-inquiry` 설정과 시작 시 검증
2. `client`의 전용 OAuth·계정등록·보유계좌 호출
3. `exception`의 CODEF 결과코드 매핑
4. `entity`, `repository`, `crypto`의 연결정보 저장
5. `mapper`, `dto.internal`의 계좌 응답 정규화
6. `store`의 일회용 후보 토큰
7. `service`의 전체 연결 유스케이스
8. `controller`, `dto.req`, `dto.res`의 API 계약
9. `ExAccountSyncService` 경계 메서드 연결
10. 단위·통합·보안 테스트

`ExAccountSyncService`의 HMAC/마스킹/upsert 기능은 유지한다. CODEF 서비스에서 JPA 엔티티를 직접 생성하지 말고 반드시 이 서비스를 통해 저장하여 기존 중복 방지와 민감정보 보호 규칙을 재사용한다.

## 7. 오류 매핑 초안

| 상황 | 우리 오류 코드 | HTTP |
| --- | --- | --- |
| CODEF 키/설정 누락 | `CODEF_CONFIGURATION_INVALID` | 서버 시작 실패 |
| 은행 인증정보 불일치 | `CODEF_CREDENTIAL_INVALID` | 422 |
| 추가인증 필요 | `CODEF_ADDITIONAL_AUTH_REQUIRED` | 409 |
| 연결정보 없음/폐기됨 | `CODEF_CONNECTION_NOT_FOUND` | 404 |
| 후보 토큰 만료 | `CODEF_CANDIDATE_EXPIRED` | 410 |
| 후보 토큰 소유자 불일치 | `CODEF_CANDIDATE_FORBIDDEN` | 403 |
| CODEF 타임아웃 | `CODEF_TIMEOUT` | 504 |
| CODEF 일시 장애 | `CODEF_UPSTREAM_UNAVAILABLE` | 503 |
| 알 수 없는 CODEF 응답 | `CODEF_RESPONSE_INVALID` | 502 |

CODEF의 실제 결과코드 목록을 확인한 뒤 `CodefErrorMapper` 테스트 fixture로 고정한다. 클라이언트에 CODEF 원문 메시지를 그대로 반환하지 않는다.

## 8. 이번 범위의 비목표

- `codef.one-won-transfer`를 사용하는 1원 송금
- 외부계좌에서 실제 이체 실행
- 인증서 로그인 전체 지원
- 모든 은행의 추가인증을 한 번에 지원
- CODEF 응답 원문 장기 저장

거래내역 조회는 같은 `account-inquiry` 키와 저장된 `connectedId`를 재사용할 수 있지만, 외부계좌 최초 연동이 안정화된 다음 별도 작업으로 진행한다.

## 9. 공식·업스트림 근거

2026-06-22 확인 기준:

- [CODEF 계정 등록 개발가이드](https://developer.codef.io/common-guide/connected-id/add): 사용자 인증수단 등록과 `connectedId` 흐름의 기준 문서
- [CODEF 추가 인증 개발가이드](https://developer.codef.io/common-guide/add-auth): 기관이 추가 인증을 요구할 때의 후속 처리 기준
- [CODEF 개인 은행 보유계좌 상품](https://developer.codef.io/products/bank/common/p/account): 개인 보유계좌 요청 필드와 응답 필드의 최종 기준
- [CODEF 공식 Node SDK](https://github.com/codef-io/codef-node): `connectedId` 기반 계정 목록·상품 호출 예시
- [CODEF 공식 Python SDK](https://github.com/codef-io/easycodefpy): 개인 보유계좌 경로 `/v1/kr/bank/p/account/account-list`와 요청 예시

CODEF 개발가이드의 상품별 필드와 결과코드는 콘솔 계약·상품 버전에 따라 달라질 수 있다. 구현 시작 시 위 공식 상품 페이지의 입력부, 출력부, 추가인증, 오류코드를 다시 확인하고 fixture에 반영해야 한다.
