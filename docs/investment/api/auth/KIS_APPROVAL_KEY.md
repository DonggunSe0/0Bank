# KIS WebSocket 접속키 정책

## 개요

KIS WebSocket API 사용 시 AppKey/AppSecret 대신 WebSocket 전용 접속키(Approval Key)를 사용한다.

WebSocket 연결 수립 시 최초 1회 인증 용도로만 사용된다.

---

## 발급 API

### Endpoint

```http
POST /oauth2/Approval
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
  "secretkey": "{APP_SECRET}"
}
```

---

## 응답

```json
{
  "approval_key": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
}
```

---

# Approval Key 관리 정책

## 발급 시점

WebSocket 연결 직전 최초 `getApprovalKey()` 요청 시 1회 발급한다.

```text
WebSocket 연결 요청
↓
getApprovalKey()
↓
메모리 Approval Key 없음
↓
Approval Key 발급 API 호출
↓
메모리 저장
↓
WebSocket 연결
```

메모리에 Approval Key가 이미 있으면 추가 발급 API를 호출하지 않고 기존 값을 반환한다.

---

## 저장 위치

Approval Key는 DB에 저장하지 않는다.

```text
In-Memory
```

로 관리한다.

---

## 유효 기간

```text
24시간
```

현재 코드는 만료 시각을 별도로 저장하거나 추적하지 않는다.

Approval Key는 WebSocket 연결 수립 시 최초 1회 인증에만 사용하므로, 메모리에 값이 존재하면 그대로 사용한다.

---

## 중요 사항

Approval Key는 WebSocket 연결 수립 시 최초 1회만 사용된다.

따라서

```text
Approval Key 인증
↓
WebSocket 연결 성공
↓
세션 유지
```

상태라면 Approval Key 유효 기간이 만료되어도 연결은 유지된다.

즉,

```text
Approval Key 유효기간 만료
≠
WebSocket 연결 종료
```

이다.

---

## 재발급 정책

현재 구현 기준으로 애플리케이션 실행 중 재발급 로직을 두지 않는다.

### 애플리케이션 재시작

```text
서버 재기동
↓
메모리 Approval Key 초기화
↓
최초 WebSocket 연결 요청 시 Approval Key 발급
```

---

### WebSocket 연결 상실

```text
WebSocket Disconnect
↓
메모리에 저장된 Approval Key 조회
↓
WebSocket 재연결
```

---

## 운영 정책

Approval Key 만료 시간을 기준으로 주기적인 재발급 스케줄링을 수행하지 않는다.

### 이유

Approval Key는 세션 생성 시점에만 사용된다.

이미 연결된 WebSocket 세션에는 영향을 주지 않는다.

따라서

```text
24시간마다 재발급
```

과 같은 스케줄링은 불필요하다.

---

## 구현 예시

```java

@Component
public class KisWebSocketKeyManager {

    private volatile String approvalKey;

    public String getApprovalKey() {
        if (approvalKey == null) {
            synchronized (this) {
                if (approvalKey == null) {
                    approvalKey = requestApprovalKey();
                }
            }
        }
        return approvalKey;
    }
}
```

---

## 장애 복구 흐름

```text
WebSocket Disconnect
↓
메모리에 저장된 Approval Key 조회
↓
WebSocket 재연결
↓
실시간 종목 재구독
↓
정상 서비스 복구
```
