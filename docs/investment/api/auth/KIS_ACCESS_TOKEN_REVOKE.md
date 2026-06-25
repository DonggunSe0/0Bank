# AccessToken 폐기 API

## 개요

발급받은 AccessToken을 더 이상 사용하지 않을 경우 명시적으로 폐기한다.

---

## Endpoint

```http
POST /oauth2/revokeP
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
  "appkey": "{APP_KEY}",
  "appsecret": "{APP_SECRET}",
  "token": "{ACCESS_TOKEN}"
}
```

---

## 응답

```json
{
  "code": 200,
  "message": "접근토큰 폐기에 성공하였습니다"
}
```

---

# AccessToken 폐기 정책

## 사용 목적

다음 상황에서 AccessToken을 명시적으로 무효화한다.

```text
더 이상 AccessToken을 사용하지 않을 경우
```

---

## 애플리케이션 종료 시

현재 프로젝트는 애플리케이션 종료 시 AccessToken 폐기를 시도한다.

```text
Application Shutdown
↓
AccessToken 폐기 요청
↓
애플리케이션 종료
```

---

## 실패 처리 정책

AccessToken 폐기 실패는 서비스 장애로 간주하지 않는다.

### 이유

```text
AccessToken 유효기간은 24시간
```

이며

```text
애플리케이션 종료 후
토큰이 자동 만료된다.
```

---

## 예외 상황

### 서버 비정상 종료

```text
kill -9
EC2 장애
프로세스 강제 종료
```

등의 상황에서는 폐기 API 호출이 수행되지 않을 수 있다.

이 경우 별도 복구 작업은 수행하지 않는다.

---

## 운영 정책

### 정상 종료

```text
폐기 API 호출
```

수행

### 비정상 종료

```text
자연 만료 대기
```

---

## 토큰 생명주기

```text
Application Start
↓
최초 API 요청
↓
AccessToken 발급
↓
REST API 호출
↓
필요 시 재발급
↓
Application Shutdown
↓
AccessToken 폐기
```

---

## 구현 예시

```java

@Component
@RequiredArgsConstructor
public class KisTokenManager {

    private final KisAuthClient kisAuthClient;

    private volatile String accessToken;

    @PreDestroy
    public void revokeToken() {

        if (accessToken == null) {
            return;
        }

        try {
            kisAuthClient.revoke(accessToken);
        } catch (Exception e) {
            log.warn("KIS AccessToken revoke failed", e);
        }
    }
}
```

---

## 중요 사항

AccessToken 폐기는 보안 강화를 위한 추가 작업이다.

반드시 성공해야 하는 핵심 비즈니스 로직은 아니다.

```text
폐기 실패
≠
서비스 장애
```

로 취급한다.