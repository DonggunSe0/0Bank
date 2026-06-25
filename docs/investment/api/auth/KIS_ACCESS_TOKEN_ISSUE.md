# KIS AccessToken 정책

## 개요

KIS Open API 호출을 위해 AccessToken을 발급받는다.

모든 REST API 호출 시 Authorization Header에 Bearer Token 형태로 전달한다.

---

## 발급 API

### Endpoint

```http
POST /oauth2/tokenP
```

### 실전 서버

```text
https://openapi.koreainvestment.com:9443
```

### 모의 서버

```text
https://openapivts.koreainvestment.com:29443
```

---

## 요청

### Header

```http
Content-Type: application/json
```

### Body

```json
{
  "grant_type": "client_credentials",
  "appkey": "{APP_KEY}",
  "appsecret": "{APP_SECRET}"
}
```

---

## 응답

```json
{
  "access_token": "...",
  "access_token_token_expired": "2026-06-16 12:00:00",
  "token_type": "Bearer",
  "expires_in": 86400
}
```

---

# AccessToken 정책

## 유효 기간

```text
24시간
```

---

## 재발급 제한 정책

KIS 정책상

```text
최초 발급 후 6시간 이내 재발급 요청 시
신규 토큰이 아닌 기존 토큰을 반환한다.
```

즉,

```text
Token 발급
↓
6시간 이내 재발급 요청
↓
동일 토큰 반환
```

이다.

---

## 토큰 관리 방식

토큰 만료 시각은 응답값의

```text
access_token_token_expired
```

필드를 기준으로 관리한다.

---

## 저장 위치

AccessToken은 DB에 저장하지 않는다.

```text
In-Memory
```

로 관리한다.

---

## 재발급 정책

애플리케이션 시작 시 미리 발급하지 않는다.

최초 API 호출 시 필요할 경우 발급한다.

```text
Application Start
↓
AccessToken 없음
↓
최초 API 요청
↓
토큰 발급
↓
API 호출
```

---

## 재발급 시점

다음 조건 중 하나를 만족하면 재발급한다.

### 1. AccessToken 미존재

```text
accessToken == null
```

### 2. 만료 임박

```text
expiresAt - 30분 <= 현재 시각
```

---

## 스케줄링 기반 갱신을 사용하지 않는 이유

### API 호출 시 검증 방식

```text
토큰 필요
↓
만료 여부 확인
↓
필요 시 재발급
```

구조로 충분하다.

---

### 스케줄링 방식의 문제

```text
언제 갱신할지 계산
↓
스케줄 등록
↓
예외 처리
```

가 필요하다.

반면

```text
호출 시 검증
```

은 구현이 단순하고 유지보수가 쉽다.

---

## 동시성 제어

여러 요청이 동시에 토큰 재발급을 시도하는 상황을 방지한다.

---

### 선택

```text
synchronized
```

---

### 선택 이유

- 구현 단순
- CPU Busy Waiting 없음
- 예외 발생 시 자동 락 해제

---

### 미선택

#### ReentrantLock

```text
단순 로직 대비 복잡도 증가
```

#### CAS

```text
Busy Waiting 발생 가능
```

#### CompletableFuture

```text
토큰 재발급 문제에 비해 과도한 설계
```

---

## 구현 예시

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

    private boolean needRefresh() {

        return accessToken == null
                || expiresAt.minusMinutes(30)
                .isBefore(LocalDateTime.now());
    }
}
```

---

## Authorization Header

모든 인증 필요 API 호출 시

```http
Authorization: Bearer {ACCESS_TOKEN}
```

형태로 전달한다.

---

## 장애 복구 흐름

```text
API 요청
↓
토큰 만료 감지
↓
토큰 재발급
↓
요청 재시도
↓
정상 처리
```