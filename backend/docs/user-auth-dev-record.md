# User/Auth 도메인 개발 기록

---

## 구현 범위

### 1. 회원가입 API

**POST** `/api/v1/auth/signup`

- 이메일 중복 검사
- BCrypt 비밀번호 해싱
- 사용자 정보 저장 (이메일, 이름, 전화번호, 생년월일)
- 약관 동의 항목 일괄 저장 (필수 3개 + 선택 1개)

---

### 2. 3단계 본인인증 시스템

```
[1단계] 신분증 OCR → [2단계] 행안부 진위확인 → [3단계] 1원 송금 인증
```

### 1단계 — 신분증 OCR (비동기)

**POST** `/api/v1/users/me/identity-verification/ocr`

- Google Cloud Vision API (`DOCUMENT_TEXT_DETECTION`) 사용
- 즉시 **202 Accepted** 반환 → OCR은 백그라운드 처리
- `@Async("ocrExecutor")` 전용 스레드 풀 격리 (core: 2, max: 4, queue: 50)
- 추출 항목: 이름, 주민등록번호, 발급일자 (Regex 파싱)
- OCR 완료 즉시 2단계로 체이닝

**Regex 파싱 전략**

| 항목 | 패턴 예시 |
| --- | --- |
| 이름 | `주민등록증` 헤더 다음 줄 한글 2~4자 |
| 주민번호 | `\d{6}-[1-8]\d{6}` (1900년대 지원 포함) |
| 발급일자 | `2024. 11. 21.` → `2024-11-21` 정규화 |

### 2단계 — 행안부 진위확인 (Mock)

- OCR 성공 직후 같은 스레드에서 즉시 체이닝
- Mock 시나리오: 발급일자 불일치, 존재하지 않는 명의, 타임아웃
- 타임아웃 시 `REQUIRES_NEW` 보상 트랜잭션으로 FAILED 커밋 후 메인 트랜잭션 롤백
- **검증 성공 시 주민번호 뒷자리 마스킹**: `011102-3156225` → `011102-*******`

### 3단계 — 1원 송금 인증 (Redis)

**POST** `/api/v1/users/me/identity-verification/one-won`
**POST** `/api/v1/users/me/identity-verification/one-won/verify`

- 4자리 랜덤 코드 생성 → Redis TTL 10분 저장 (`identity:one-won:{verificationId}`)
- Mock 송금 서비스로 계좌 + 코드 로그 출력
- 코드 일치 시 인증 완료 (Redis 키 즉시 삭제, 재사용 방지)
- **5회 오답 시 코드 폐기** → 재송금 요청 필요
- `ONE_WON_PENDING` 상태에서 재시도 허용 (코드 만료/미수신 대응)
- 만료 / 불일치 / 횟수 초과 에러 구분

---

### 3. 로그인 / 토큰 관리

### Access Token — JWT, 1시간

```
Header.Payload.Signature
Payload: { sub: userId, email, iat, exp }
```

- Secret: 환경변수 `JWT_SECRET` 주입 (`${JWT_SECRET:기본값}`)

### Refresh Token — Opaque, 7일

- UUID 랜덤 문자열
- Redis 저장: `refresh:{userId}` → token (TTL 7일)
- 사용자당 단일 세션 보장 (새 로그인 시 덮어씀)

### Refresh Token Rotation 흐름

```
클라이언트: 만료된 AT + RT 전송
서버: 만료된 AT 서명 검증 → userId 추출
     Redis refresh:{userId} 와 RT 비교
     일치 → 새 AT + 새 RT 발급 (기존 RT 무효화)
```

**API**

| Method | Endpoint | Auth | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/signup` | X | 회원가입 |
| POST | `/api/v1/auth/login` | X | 로그인 |
| POST | `/api/v1/auth/refresh` | X | 토큰 재발급 |
| POST | `/api/v1/auth/logout` | O (만료 허용) | 로그아웃 (RT 삭제) |
| GET | `/api/v1/users/me` | O | 내 정보 조회 |
| DELETE | `/api/v1/users/me` | O | 회원 탈퇴 |

---

### 4. 사용자 상태 관리

```
ACTIVE (정상) ─→ DORMANT (휴면)
             └─→ WITHDRAWN (탈퇴)
```

- `UserStatus`: `ACTIVE` / `DORMANT` / `WITHDRAWN`
- 로그인 시 상태 체크 — 휴면/탈퇴 계정 로그인 차단 (403)
- 회원 탈퇴 (`DELETE /api/v1/users/me`) — 상태 `WITHDRAWN` 전환 + Redis RT 삭제
- 필드 선언부 기본값 `ACTIVE` 설정 — 리플렉션 기반 테스트 픽스처와 호환

---

### 5. 약관 동의

**GET** `/api/v1/users/me/consents`
**PATCH** `/api/v1/users/me/consents`

- `TermsType`: `SERVICE_TERMS`, `PERSONAL_INFO`, `FINANCIAL_INFO`, `MARKETING`
- `(user_id, terms_type)` 복합 유니크 제약
- 회원가입 시 필수 3개 동의 검증 (`@AssertTrue`) + 선택 1개 일괄 저장
- 마케팅 동의만 변경 가능 (필수 항목 변경 불가)

---

### 6. 사용자 프로필

**POST** `/api/v1/users/me/profile`
**GET** `/api/v1/users/me/profile`
**PATCH** `/api/v1/users/me/profile`

- `AgeGroup`: `TEENS` / `TWENTIES` / `THIRTIES` / `FORTIES` / `FIFTIES_PLUS`
- `OccupationStatus`: `EMPLOYED` / `SELF_EMPLOYED` / `STUDENT` / `FREELANCER` / `UNEMPLOYED` / `ETC`
- `FinancialInterest`: `SAVINGS` / `INVESTMENT` / `LOAN` / `INSURANCE` / `PENSION` / `FOREIGN_EXCHANGE`
- `financialInterests` — `@ElementCollection` 다중 선택, `clear() + addAll()` 방식으로 Hibernate 추적 유지
- User와 1:1 관계, 중복 등록 시 409

---

### 7. Spring Security

- `spring-boot-starter-security` 도입
- `JwtAuthenticationFilter` (`OncePerRequestFilter`)
  - `Authorization: Bearer {token}` 파싱 → 서명/만료 검증
  - SecurityContext에 userId(Long) 세팅
  - 로그아웃 경로(`/api/v1/auth/logout`)는 `getServletPath()` 기준으로 만료 토큰 허용
- `SecurityConfig`
  - Stateless 세션, CSRF 비활성화
  - `permitAll`: signup, login, refresh, Swagger UI, H2 Console
  - `authenticated`: 그 외 전체
  - CORS: `CORS_ALLOWED_ORIGINS` 환경변수로 허용 도메인 주입
- 컨트롤러 전체 `@AuthenticationPrincipal Long userId` 적용
- `GlobalExceptionHandler` — `AuthenticationException` → 401, `AccessDeniedException` → 403

---

## 인증 상태 흐름

```
OCR_PENDING → OCR_COMPLETED → GOVERNMENT_VERIFIED → ONE_WON_PENDING → ONE_WON_VERIFIED
                                                                     ↘ FAILED
```

---

## 주요 기술적 결정사항

### @Async + @Transactional 타이밍 문제

**문제**: 비동기 스레드가 메인 트랜잭션 커밋 전에 실행되어 DB에서 레코드를 찾지 못함

**해결**: `TransactionSynchronizationManager.registerSynchronization()`의 `afterCommit()` 콜백 안에서 비동기 호출

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        ocrService.processAsync(imageBytes, verificationId);
    }
});
```

### MultipartFile 임시파일 소멸 문제

**문제**: `afterCommit()` 시점에 Tomcat이 임시파일 삭제 → `NoSuchFileException`

**해결**: 메인 스레드에서 `imageFile.getBytes()`로 미리 byte 배열 복사 후 람다에 전달

### @ElementCollection 업데이트 전략

**문제**: `financialInterests = new HashSet<>(request)` 재할당 시 Hibernate 컬렉션 추적 깨짐 → 전체 삭제 후 재삽입 SQL 발생 또는 Orphan 예외

**해결**: 기존 컬렉션 인스턴스를 유지하면서 `clear() + addAll()`

```java
this.financialInterests.clear();
if (financialInterests != null) {
    this.financialInterests.addAll(financialInterests);
}
```

### 로그아웃 만료 토큰 허용

**문제**: `JwtAuthenticationFilter`가 만료 토큰을 거부하면 로그아웃 불가

**해결**: `getServletPath()`로 로그아웃 경로 감지 후 `parseUserIdIgnoreExpiry()` 분기

```java
boolean isLogout = "/api/v1/auth/logout".equals(request.getServletPath());
Long userId = isLogout
        ? jwtProvider.parseUserIdIgnoreExpiry(token)
        : jwtProvider.parseUserId(token);
```

`getRequestURI()` 대신 `getServletPath()` 사용 — Context Path 포함 여부 무관하게 경로 비교

### 테스트 픽스처 호환성

**문제**: 팀원 테스트가 리플렉션으로 no-args 생성자를 직접 호출하여 `status` 필드가 null → NOT NULL 제약 위반

**해결**: `@Builder` 생성자가 아닌 필드 선언부에 기본값 지정

```java
private UserStatus status = UserStatus.ACTIVE;
```

---

## 보안

| 항목 | 구현 방식 |
| --- | --- |
| 비밀번호 저장 | BCrypt 해싱 |
| JWT Secret | 환경변수 `JWT_SECRET` 주입 |
| Refresh Token 재사용 공격 방지 | Rotation — 사용 시 즉시 폐기 + 새 RT 발급 |
| 1원 인증 브루트포스 방지 | Redis 5회 실패 제한, 초과 시 코드 폐기 |
| 주민번호 보호 | 행안부 검증 완료 후 뒷자리 즉시 마스킹 |
| CORS | 환경변수로 허용 도메인 제한 |
| GCP 서비스 계정 키 | `.gitignore` 처리, 환경변수 경로 주입 |

---

## 환경변수

| 변수명 | 설명 | 기본값 |
| --- | --- | --- |
| `JWT_SECRET` | JWT 서명 키 (32자 이상) | dev 기본값 |
| `GCP_KEY_PATH` | Google Cloud Vision 서비스 계정 JSON 경로 | `classpath:gcp-key.json` |
| `CORS_ALLOWED_ORIGINS` | 허용할 Origin 목록 | `http://localhost:3000` |

---

## 인프라 (Docker Compose)

```yaml
services:
  mysql:  # 포트 13306
  redis:  # 포트 6379
```
