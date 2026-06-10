# MoneyStory — User/Auth 도메인 개발 기록

> 청소년 금융 성장 플랫폼 MoneyStory의 회원/인증 도메인 구현 기록

---

## 기술 스택

| 항목 | 기술 |
|------|------|
| Language | Java 25 |
| Framework | Spring Boot 4 |
| Database | MySQL 8.0 |
| Cache | Redis 7.2 |
| OCR | Google Cloud Vision API |
| 인증 | JWT (Access) + Opaque Token (Refresh, Redis) |
| 암호화 | BCrypt (spring-security-crypto) |
| 비동기 | @Async + ThreadPoolTaskExecutor |

---

## 구현 범위

### 1. 회원가입 API

**POST** `/api/v1/auth/signup`

- 이메일 중복 검사
- BCrypt 비밀번호 해싱
- 사용자 정보 저장 (이메일, 이름, 전화번호, 생년월일)

---

### 2. 3단계 본인인증 시스템

```
[1단계] 신분증 OCR → [2단계] 행안부 진위확인 → [3단계] 1원 송금 인증
```

#### 1단계 — 신분증 OCR (비동기)

**POST** `/api/v1/users/me/identity-verification/ocr`

- Google Cloud Vision API (`DOCUMENT_TEXT_DETECTION`) 사용
- 즉시 **202 Accepted** 반환 → OCR은 백그라운드 처리
- `@Async("ocrExecutor")` 전용 스레드 풀 격리 (core: 2, max: 4, queue: 50)
- 추출 항목: 이름, 주민등록번호, 발급일자 (Regex 파싱)
- OCR 완료 즉시 2단계로 체이닝

**Regex 파싱 전략**

| 항목 | 패턴 예시 |
|------|----------|
| 이름 | `주민등록증` 헤더 다음 줄 한글 2~4자 |
| 주민번호 | `\d{6}-[1-4]\d{6}` |
| 발급일자 | `2024. 11. 21.` → `2024-11-21` 정규화 |

#### 2단계 — 행안부 진위확인 (Mock)

- OCR 성공 직후 같은 스레드에서 즉시 체이닝
- Mock 시나리오: 발급일자 불일치, 존재하지 않는 명의, 타임아웃
- 타임아웃 시 `REQUIRES_NEW` 보상 트랜잭션으로 FAILED 커밋 후 메인 트랜잭션 롤백
- **검증 성공 시 주민번호 뒷자리 마스킹**: `011102-3156225` → `011102-*******`

#### 3단계 — 1원 송금 인증 (Redis)

**POST** `/api/v1/users/me/identity-verification/one-won`
**POST** `/api/v1/users/me/identity-verification/one-won/verify`

- 4자리 랜덤 코드 생성 → Redis TTL 10분 저장 (`identity:one-won:{verificationId}`)
- Mock 송금 서비스로 계좌 + 코드 로그 출력
- 코드 일치 시 인증 완료 (Redis 키 즉시 삭제, 재사용 방지)
- 만료 / 불일치 에러 구분

---

### 3. 로그인 / 토큰 관리

#### Access Token — JWT, 1시간

```
Header.Payload.Signature
Payload: { sub: userId, email, iat, exp }
```

#### Refresh Token — Opaque, 7일

- UUID 랜덤 문자열
- Redis 저장: `refresh:{userId}` → token (TTL 7일)
- 사용자당 단일 세션 보장 (새 로그인 시 덮어씀)

#### Refresh Token Rotation 흐름

```
클라이언트: 만료된 AT + RT 전송
서버: 만료된 AT 서명 검증 → userId 추출
     Redis refresh:{userId} 와 RT 비교
     일치 → 새 AT + 새 RT 발급 (기존 RT 무효화)
```

**API**

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/auth/signup` | 회원가입 |
| POST | `/api/v1/auth/login` | 로그인 |
| POST | `/api/v1/auth/refresh` | 토큰 재발급 |
| POST | `/api/v1/auth/logout` | 로그아웃 (RT 삭제) |

---

## 인증 상태 흐름

```
OCR_PENDING → OCR_COMPLETED → GOVERNMENT_VERIFIED → ONE_WON_PENDING → COMPLETED
                                                                      ↘ FAILED
```

---

## 주요 기술적 결정사항

### @Async + @Transactional 타이밍 문제

**문제**: 비동기 스레드가 메인 트랜잭션 커밋 전에 실행되어 DB에서 레코드를 찾지 못함

**해결**: `TransactionSynchronizationManager.registerSynchronization()` 의 `afterCommit()` 콜백 안에서 비동기 호출

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

### 보안

- 주민번호 뒷자리 행안부 검증 완료 후 즉시 마스킹 처리
- Google Cloud 서비스 계정 JSON 키 `.gitignore` 처리

---

## 인프라 (Docker Compose)

```yaml
services:
  mysql:  # 포트 13306
  redis:  # 포트 6379
```
