# Investment Domain Development Guide

## 목적

본 문서는 Investment 도메인 개발 시 반드시 준수해야 하는 아키텍처, 설계 원칙 및 구현 규약을 정의한다.

---

# 서비스 범위

## 지원 범위

- 국내 주식
- KOSPI
- KRX 정규장
- 모의 투자

## 미지원 범위

- 해외 주식
- 실거래
- 코스닥
- 코넥스
- NXT
- 시간외 거래
- 공매도
- 신용거래

---

# 패키지 구조 규약

## 외부 API 호출

```text
investment/client
```

예시

```text
investment
 └─ client
     ├─ auth
     │   ├─ KisAuthClient
     │   ├─ KisAccessTokenManager
     │   ├─ KisWebSocketApprovalKeyManager
     │   └─ KisWebSocketClient
     └─ marketholiday
         └─ KisHolidayClient
```

---

## 예외 코드

```text
exception
```

예시

```md
public enum InvestmentErrorCode
```

---

# HTTP Client 정책

우선순위

```text
RestClient
↓
CompletableFuture
↓
WebClient
```

---

## RestClient

기본 선택지

```text
동기
블로킹
```

---

## CompletableFuture

필요 시

```text
병렬 처리
비동기 처리
```

를 위해 사용한다.

주의

```text
외부 API 호출 자체는 여전히 Blocking I/O
```

이다.

---

## WebClient

다음 조건 충족 시 도입 검토

- 다수 외부 API 병렬 호출
- 고동시성 환경
- 병렬 비동기 논블로킹 I/O 필요

---

# KIS 인증 정책

## AccessToken

재발급은 API 호출 시 수행한다.

스케줄링 기반 갱신을 사용하지 않는다.

### 이유

- 불필요한 스케줄러 관리 비용
- API 호출 시점에만 유효성 검증 필요

### 구현 방식

```java

@Component
public class KisTokenManager {

    private volatile String accessToken;
    private volatile LocalDateTime expiresAt;

    public String getAccessToken() {

        if (needRefresh()) {

            synchronized (this) {

                if (needRefresh()) {
                    refreshToken();
                }
            }
        }

        return accessToken;
    }
}
```

---

## AccessToken 동시성 제어

선택

```text
synchronized
```

### 선택 이유

- 구현이 단순함
- CPU Busy Waiting 없음
- 예외 발생 시 자동 락 해제

### 선택하지 않은 방식

#### ReentrantLock

- 단순 로직 대비 복잡도 증가

#### CAS

- Busy Waiting 발생 가능

#### CompletableFuture

- 과도한 설계

---

## WebSocket 접속키

WebSocket 연결 직전 최초 요청 시 발급한다.

```text
getApprovalKey()
↓
메모리 접속키 없음
↓
접속키 발급
↓
메모리 보관
```

메모리에 접속키가 이미 있으면 추가 API 호출 없이 기존 값을 반환한다.

현재 구현 기준으로 접속키 만료 시간 기반 재발급 로직은 두지 않는다.

WebSocket 연결 상실 시

```text
기존 접속키 조회
↓
재연결
```

수행

---

# 애플리케이션 생명주기

## 시작 시

### 수행 작업

- 종목 마스터 파일 다운로드
- 종목 정보 Upsert
- 휴장일 정보 동기화
- 스케줄러 활성화

WebSocket 접속키는 시작 시 선발급하지 않고 최초 WebSocket 연결 요청 시 lazy 발급한다.

---

## 종료 시

### 수행 작업

- STOMP 세션 정리
- AccessToken 폐기 요청

---

# 종목 데이터 관리

## 데이터 출처

KIS 종목 마스터 파일

---

## 동기화

### 시작 시

1회 수행

### 운영 중

```text
1일 1회
```

수행

---

## 저장 방식

```text
Upsert
```

---

# 휴장일 관리

## 데이터 출처

국내휴장일조회 API

---

## 저장 방식

```text
DB 영속화
+
메모리 캐시
```

---

## 사용 목적

- 거래 가능 여부 검증
- 정규장 여부 검증

---

# 거래 정책

## 거래 가능 시간

```text
09:00 ~ 15:20
```

---

## 추가 검증

- 휴장일 여부
- 거래정지 종목 여부

---

# 시장가 주문

## 처리 방식

실시간 호가 기준

```text
최선 호가
↓
수량 차감
↓
순차 체결
```

주문 수량이 잔량보다 많을 경우

```text
잔여 수량 취소
```

---

# 지정가 주문

## 등록

24시간 가능

---

## 체결

정규장 시간에만 수행

---

## 상태

```md
PENDING
PARTIALLY_FILLED
FILLED
CANCELLED
```

---

## 체결 방식

```text
실시간 호가 이벤트 수신
↓
체결 조건 만족
↓
즉시 체결
```

---

## 구현 원칙

스케줄링 기반 Polling보다

```text
이벤트 기반 처리
```

를 우선 고려한다.

---

## 메시지 처리 후보

- Redis Pub/Sub
- RabbitMQ
- Kafka

---

# 계좌 정책

## 접근 정책

모든 계좌 조회 및 거래 요청 시

```text
계좌 비밀번호 검증
```

필수

---

## 저장 정책

```text
BCrypt
```

해시 저장

---

# 동시성 정책

모든 비즈니스 로직은

```text
DB 레벨
+
애플리케이션 레벨
```

동시성 문제를 검토한다.

---

## 검토 대상

- 주문
- 체결
- 계좌 잔고
- 보유 종목

---

## 적극 활용 가능 기술

### DB

- Pessimistic Lock
- Optimistic Lock
- CASE
- Bulk Update

### Application

- synchronized
- ReentrantLock
- CAS

---

# 성능 최적화 원칙

우선순위

```text
정확성
↓
동시성 안정성
↓
성능
```

---

성능 문제 발생 시

- 비동기 처리
- 메시지 큐
- 병렬 처리

를 도입한다.

초기 구현 단계에서는 과도한 최적화를 지양한다.
