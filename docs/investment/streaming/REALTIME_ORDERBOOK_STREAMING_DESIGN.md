# 실시간 호가 스트리밍 서버 설계

## 목적

KIS 국내주식 실시간호가 WebSocket(`H0STASP0`)을 우리 서버가 수신하고, 사용자는 우리 서버와 SSE(Server-Sent Events)로 실시간 호가를 조회한다.

이 문서는 다음 요구 사항을 기준으로 서버 측 구현 방향을 정의한다.

- 클라이언트와 우리 서버 간 실시간 통신은 SSE를 사용한다.
- 클라이언트가 실시간 호가 페이지에 진입하면 SSE 스트림 생성과 종목 구독을 하나의 API로 처리한다.
- 클라이언트가 실시간 호가 페이지를 이탈하면 SSE 스트림 종료와 종목 구독 해지를 하나의 API로 처리한다.
- 한 스트림, 즉 한 탭은 동시에 최대 1개 종목만 구독한다.
- 한 사용자는 여러 탭을 열 수 있으므로 여러 스트림을 가질 수 있다.
- 같은 사용자의 서로 다른 스트림은 서로 다른 종목을 구독할 수 있다.
- 같은 스트림에서 구독 종목을 교체하지 않는다.
- 다른 종목 페이지로 이동하려면 기존 스트림을 종료하고 새 종목으로 새 스트림을 생성한다.
- 우리 서버와 KIS 간 통신은 WebSocket을 사용한다.
- KIS WebSocket 구독 수 제한을 고려한다.
- 특정 종목을 구독한 스트림이 0개가 되면 KIS 구독을 해지한다.
- 멀티 인스턴스 환경에서도 중복 KIS 구독과 데이터 누락을 최소화한다.

---

## 핵심 정책

### 스트림 단위 단일 구독

구독 상태의 중심은 `userId`가 아니라 `streamId`다.

`streamId`는 사용자가 실시간 호가 페이지에 진입할 때 생성되는 SSE 세션 식별자이며, 브라우저 탭 하나의 실시간 호가 수신 생명주기와 대응한다.

```text
userId 1
  ├─ streamId A -> 005930
  └─ streamId B -> 000660

userId 2
  └─ streamId C -> 005930
```

한 스트림은 하나의 종목만 구독한다. 하지만 한 사용자는 여러 탭을 열 수 있으므로 여러 스트림을 가질 수 있다.

### 종목 변경 정책

같은 스트림에서 구독 종목을 변경하지 않는다.

프론트엔드가 다른 종목 페이지로 이동하려면 다음 순서를 따른다.

```text
기존 종목 페이지 이탈
↓
DELETE /streams/{streamId}
↓
기존 SSE 연결 종료 + 기존 종목 구독 해지
↓
새 종목 페이지 진입
↓
GET /{stockCode}/stream
↓
새 SSE 연결 생성 + 새 종목 구독
```

즉 종목 전환은 "구독 교체"가 아니라 "기존 스트림 종료 후 신규 스트림 생성"으로 처리한다.

---

## 전체 구조

```text
Frontend
  ├─ 종목 페이지 진입 시 SSE 스트림 생성 + 종목 구독
  └─ 종목 페이지 이탈 시 SSE 스트림 종료 + 종목 구독 해지

Backend Instance A
  ├─ 로컬 SseEmitter Registry
  ├─ Redis 구독 상태 저장
  ├─ Redis Pub/Sub 구독
  └─ KIS WebSocket leader 후보

Backend Instance B
  ├─ 로컬 SseEmitter Registry
  ├─ Redis 구독 상태 저장
  ├─ Redis Pub/Sub 구독
  └─ KIS WebSocket leader 후보

Redis
  ├─ streamId별 구독 상태 저장
  ├─ stockCode별 구독 stream 목록 저장
  ├─ stream owner instance 저장
  ├─ leader lock 저장
  └─ Pub/Sub 채널 제공

KIS
  └─ WebSocket H0STASP0
```

---

## 통신 방식 선택

### 클라이언트와 서버: SSE

호가 조회는 서버에서 클라이언트로 데이터가 계속 흘러가는 단방향 스트리밍에 가깝다.

따라서 양방향 통신이 필요한 WebSocket보다 SSE가 더 단순하고 적합하다.

- 브라우저에서 HTTP 기반으로 처리 가능하다.
- 서버에서 `SseEmitter`로 구현할 수 있다.
- 자동 재연결, 이벤트 타입, 이벤트 ID를 활용할 수 있다.
- 스트림 생성과 종목 구독을 하나의 진입 API로 묶을 수 있다.
- 스트림 종료와 종목 구독 해지를 하나의 이탈 API로 묶을 수 있다.

### 서버와 KIS: WebSocket

KIS 실시간호가 API는 WebSocket 기반으로 제공된다.

- 연결 URL
    - 실전: `ws://ops.koreainvestment.com:21000`
    - 모의: `ws://ops.koreainvestment.com:31000`
- TR ID: `H0STASP0`
- 구독: `tr_type=1`
- 구독 해지: `tr_type=2`

### 멀티 인스턴스 동기화: Redis

멀티 인스턴스 환경에서는 각 서버 인스턴스가 자기 메모리에 연결된 SSE 클라이언트만 알 수 있다.

따라서 전체 구독 상태와 KIS WebSocket 담당 인스턴스 선출은 외부 저장소가 필요하다. 현재 프로젝트에서 Redis를 이미 사용하고 있으므로 Redis를 기준 저장소와 이벤트 전파 수단으로 사용한다.

---

## 인증 방식

현재 프로젝트의 인증 구조는 다음과 같다.

- Access Token은 로그인 응답 JSON body로 반환된다.
- Refresh Token은 HttpOnly Cookie로 반환된다.
- `JwtAuthenticationFilter`는 `Authorization: Bearer {accessToken}` 헤더를 읽어 인증한다.

브라우저 기본 `EventSource`는 `Authorization` 헤더를 직접 설정할 수 없다.

따라서 프론트엔드에서는 SSE 연결 시 기본 `EventSource` 대신 `fetch-event-source` 같은 fetch 기반 SSE 클라이언트를 사용하는 방향이 적합하다.

```text
fetch-event-source
  -> Authorization 헤더 설정 가능
  -> 현재 JWT 인증 구조 유지 가능
```

서버는 SSE 엔드포인트도 기존 REST API와 동일하게 JWT 인증을 적용한다.

---

## 주요 컴포넌트

### RealtimeOrderbookController

클라이언트와 직접 맞닿는 REST/SSE API를 제공한다.

예상 엔드포인트:

```http
GET /api/v1/investment/realtime/orderbooks/{stockCode}/stream
DELETE /api/v1/investment/realtime/orderbooks/streams/{streamId}
```

역할:

- 종목 검증
- SSE 스트림 생성
- `streamId` 발급
- 스트림 생성과 동시에 해당 종목 구독 등록
- 스트림 종료와 동시에 해당 종목 구독 해지

`GET /{stockCode}/stream`은 단순 조회 API가 아니라 장시간 유지되는 SSE 연결이다. 이 API가 성공하면 새 스트림과 해당 종목 구독이 함께 생성된다.

`DELETE /streams/{streamId}`는 해당 스트림을 종료하고 그 스트림이 구독하던 종목도 함께 해지한다.

### SseEmitterRegistry

각 인스턴스 로컬 메모리에서 SSE 연결과 라우팅 인덱스를 관리한다.

예상 구조:

```text
streamId -> SseEmitter
streamId -> userId
streamId -> stockCode
stockCode -> Set<streamId>
```

`stockCode -> Set<streamId>`는 같은 종목을 구독한 여러 탭 또는 여러 사용자의 SSE 연결에 빠르게 전송하기 위한 로컬 라우팅 인덱스다.

역할:

- 현재 인스턴스에 연결된 SSE 클라이언트 관리
- streamId별 구독 종목을 최대 1개로 유지
- 특정 종목을 구독 중인 로컬 emitter 조회
- emitter timeout/error/completion 시 로컬 상태 제거
- 연결 종료 시 Redis 구독 상태 삭제 요청

### RedisSubscriptionStore

전체 인스턴스 기준 구독 상태의 source of truth 역할을 한다.

Redis Pub/Sub만으로 구독 상태를 관리하면 안 된다. Pub/Sub은 메시지 유실 가능성이 있고 상태를 저장하지 않기 때문이다.

따라서 구독 상태는 Redis key/set/lease로 저장하고, Pub/Sub은 상태 변경을 즉시 알리는 트리거로만 사용한다.

서버 인스턴스는 기본적으로 Redis Pub/Sub 채널을 구독한다. 테스트 프로필에서는 일반 Spring context 테스트가 Redis listener에 묶이지 않도록 다음 설정으로 비활성화할 수 있다.

```yaml
investment:
  realtime:
    redis-pub-sub:
      enabled: false
```

예상 Redis key:

```text
kis:orderbook:stream:{streamId}:user
  -> userId, TTL : 스트림이 해당 사용자의 것인지 검증

kis:orderbook:stream:{streamId}:stock
  -> stockCode, TTL : 구독 해제 시 사용

kis:orderbook:stream:{streamId}:owner
  -> instanceId, TTL : 스트림 보유 인스턴스 확인

kis:orderbook:user:{userId}:streams
  -> Set<streamId> : 사용자 보유 스트림 [ 로그아웃 시 ]

kis:orderbook:stock:{stockCode}:streams
  -> Set<streamId> : 특정 종목 구독중인 스트림 목록, 시세 전파 시 활용

kis:orderbook:lease:{streamId}
  -> stockCode, TTL : 스트림의 비상 종료에 대비한 lease

kis:orderbook:leader
  -> instanceId, TTL : 웹소켓 통신을 담당하는 leader 인스턴스 락
```

TTL을 두는 이유:

- 클라이언트가 브라우저를 닫아 REST 해지 요청을 보내지 못할 수 있다.
- 서버 인스턴스가 비정상 종료될 수 있다.
- 네트워크 장애로 SSE completion 콜백이 즉시 실행되지 않을 수 있다.

TTL이 만료되면 해당 streamId의 구독은 정리 대상이 된다.

`stockCode:{stockCode}:streams` set은 빠른 조회를 위한 인덱스다. 최종 활성 여부는 `lease:{streamId}`가 존재하는지 함께 확인해야 한다.

### Redis Pub/Sub

두 종류의 이벤트를 발행한다.

```text
kis:orderbook:subscription-changed
kis:orderbook:orderbook-updated
```

#### subscription-changed

구독 상태가 변경되었음을 leader와 다른 인스턴스에 즉시 알리는 이벤트다.

이 이벤트 자체를 신뢰 상태로 사용하지 않는다. 이벤트를 받은 leader는 반드시 Redis 상태를 다시 조회한다.

스트림 시작:

```json
{
  "streamId": "stream-id",
  "userId": 1,
  "stockCode": "005930",
  "eventType": "STARTED"
}
```

스트림 종료:

```json
{
  "streamId": "stream-id",
  "userId": 1,
  "stockCode": "005930",
  "eventType": "ENDED"
}
```

스트림 종료 이벤트를 받은 인스턴스는 자기 로컬에 해당 `streamId`가 있으면 emitter를 완료하고 로컬 인덱스를 정리한다. DELETE 요청이 SSE 연결을 보유한 인스턴스와 다른 인스턴스로 라우팅될 수
있기 때문이다.

#### orderbook-updated

KIS에서 수신한 실시간 호가 스냅샷 갱신 데이터를 모든 인스턴스에 전파하는 이벤트다.

```json
{
  "stockCode": "005930",
  "businessTime": "093730",
  "asks": [],
  "bids": [],
  "totalAskQuantity": 1000,
  "totalBidQuantity": 1200
}
```

각 인스턴스는 이 메시지를 받으면 자기 로컬 `SseEmitterRegistry`에서 해당 종목을 구독 중인 emitter에게만 SSE로 전송한다.

### KisWebSocketLeader

Redis lock을 기반으로 KIS WebSocket 담당 인스턴스를 하나만 선출한다.

```text
SET kis:orderbook:leader {instanceId} NX PX {ttl}
```

leader 역할:

- KIS WebSocket 연결 생성
- KIS WebSocket 접속키 발급
- Redis 구독 상태와 KIS 실제 구독 상태 reconcile
- 신규 종목에 대해 `tr_type=1` 구독 메시지 전송
- 구독 stream이 0개인 종목에 대해 `tr_type=2` 구독 해지 메시지 전송
- KIS 수신 데이터를 파싱해 Redis orderbook-updated 채널로 발행

leader가 죽으면 lock TTL이 만료되고, 다른 인스턴스가 leader를 획득한다.

새 leader는 Redis에 남아 있는 현재 구독 상태를 기준으로 KIS WebSocket을 다시 연결하고 필요한 종목을 재구독한다.

### KisOrderbookWebSocketClient

KIS와 직접 WebSocket 통신을 수행한다.

역할:

- WebSocket 연결 수립
- KIS approval key 포함 구독 메시지 생성
- `tr_type=1` 구독 전송
- `tr_type=2` 구독 해지 전송
- `PINGPONG` 수신 시 응답
- `^` 구분 실시간 payload 파싱

---

## 스트림 생성 및 종목 구독 흐름

```text
1. 클라이언트가 종목 페이지에 진입한다.
2. 클라이언트가 GET /{stockCode}/stream SSE API를 호출한다.
3. 서버는 인증 사용자와 종목 존재 여부, 거래 가능 상태를 검증한다.
4. 서버는 streamId를 생성하고 SseEmitter를 로컬 Registry에 저장한다.
5. 로컬 Registry에 streamId와 stockCode 관계를 저장한다.
6. RedisSubscriptionStore에 streamId 기준 구독 상태와 lease를 저장한다.
7. Redis의 stockCode별 stream set에 streamId를 추가한다.
8. Redis Pub/Sub으로 subscription-changed STARTED 이벤트를 발행한다.
9. 서버는 stream-created SSE 이벤트로 streamId를 클라이언트에게 전달한다.
10. leader 인스턴스는 STARTED 이벤트를 즉시 수신한다.
11. leader는 Redis 기준으로 해당 stockCode의 활성 stream 수를 다시 조회한다.
12. 활성 stream 수가 1개 이상이고 KIS에 아직 구독 중이 아니면 tr_type=1 메시지를 전송한다.
```

핵심은 스트림 생성과 종목 구독이 같은 API 안에서 함께 처리된다는 점이다.

---

## 스트림 종료 및 종목 구독 해지 흐름

```text
1. 클라이언트가 종목 페이지에서 이탈하기 전에 DELETE /streams/{streamId} API를 호출한다.
2. 서버는 streamId가 인증 사용자의 스트림인지 검증한다.
3. Redis에서 streamId의 stockCode를 조회한다.
4. RedisSubscriptionStore에서 streamId 기준 구독 상태와 lease를 삭제한다.
5. Redis의 stockCode별 stream set에서 streamId를 제거한다.
6. Redis Pub/Sub으로 subscription-changed ENDED 이벤트를 발행한다.
7. 해당 streamId를 로컬에 가진 인스턴스는 SseEmitter를 완료하고 로컬 Registry를 정리한다.
8. leader 인스턴스는 ENDED 이벤트를 즉시 수신한다.
9. leader는 Redis 기준으로 해당 stockCode의 활성 stream 수를 다시 조회한다.
10. 활성 stream 수가 0개이고 KIS에 구독 중이면 tr_type=2 메시지를 전송한다.
```

프론트엔드는 DELETE 요청 후 SSE 연결을 abort한다. 브라우저 종료나 네트워크 장애로 DELETE 요청이 실패할 수 있으므로 서버는 `SseEmitter` completion/error/timeout과
Redis lease TTL로도 정리한다.

---

## 다른 종목 페이지 이동 흐름

같은 스트림에서 종목을 바꾸지 않는다.

```text
1. 현재 종목 페이지에서 DELETE /streams/{streamId} 호출
2. 기존 SSE 연결 종료
3. 라우팅으로 다른 종목 페이지 이동
4. 새 종목 페이지에서 GET /{newStockCode}/stream 호출
5. 새 SSE 연결 생성
6. 새 종목 구독 등록
```

이 정책을 사용하면 스트림 하나의 생명주기와 종목 하나의 구독 생명주기가 일치한다.

---

## 실시간 호가 전송 흐름

```text
1. leader 인스턴스가 KIS WebSocket으로 실시간 호가 payload를 수신한다.
2. leader는 payload를 RealtimeOrderbookSnapshot DTO로 변환한다.
3. leader는 Redis orderbook-updated 채널로 DTO를 발행한다.
4. 모든 인스턴스는 orderbook-updated 채널을 구독하고 있다.
5. 각 인스턴스는 자기 로컬 Registry에서 해당 stockCode를 구독 중인 streamId 목록을 조회한다.
6. 해당 streamId의 SseEmitter들에게 orderbook 이벤트를 전송한다.
```

KIS WebSocket은 leader 하나만 유지하지만, SSE 클라이언트는 여러 인스턴스에 분산되어 있을 수 있다.

따라서 KIS 수신 데이터를 Redis Pub/Sub으로 모든 인스턴스에 fan-out해야 한다.

---

## Reconcile 전략

Pub/Sub 이벤트는 실시간성을 확보하기 위한 트리거다.

하지만 다음 상황에서는 이벤트가 유실되거나 처리되지 않을 수 있다.

- leader 인스턴스 재시작
- Redis 연결 순간 장애
- 서버 인스턴스 비정상 종료
- Pub/Sub 메시지 수신 중 예외 발생

따라서 leader는 이벤트 기반 reconcile 외에도 주기적 전체 reconcile을 수행한다.

```text
이벤트 기반 reconcile
  -> 이벤트의 stockCode를 즉시 확인
  -> 실시간성 확보

주기적 전체 reconcile
  -> Redis의 전체 활성 종목 목록 확인
  -> KIS 구독 목록과 비교
  -> 유실/장애 복구
```

주기적 reconcile 간격은 너무 짧을 필요는 없다. 예를 들어 5초에서 30초 사이에서 운영 부하와 복구 속도를 보고 조정한다.

---

## KIS 구독 제한 처리

KIS WebSocket 구독 가능 종목 수 제한이 있으므로 설정값으로 관리한다.

```yaml
kis:
  websocket-max-subscriptions: 41
```

구독 제한은 stream 수나 사용자 수가 아니라 KIS에 실제로 구독 중인 고유 종목 수 기준이다.

예를 들어 여러 탭과 여러 사용자가 모두 `005930`을 구독하면 KIS 구독 수는 1개다.

새로운 stream이 기존에 KIS 구독 중이지 않은 종목을 구독하려는 시점에, 고유 활성 종목 수가 제한을 초과하면 스트림 생성 요청은 실패시킨다.

예상 응답:

```http
409 Conflict
```

---

## API 초안

### 스트림 생성 및 종목 구독

```http
GET /api/v1/investment/realtime/orderbooks/{stockCode}/stream
Authorization: Bearer {accessToken}
Accept: text/event-stream
```

동작:

- 종목을 검증한다.
- 새 `streamId`를 생성한다.
- SSE 연결을 생성한다.
- 해당 `streamId`가 `{stockCode}`를 구독하도록 Redis와 로컬 Registry에 저장한다.
- `subscription-changed STARTED` 이벤트를 발행한다.
- 연결 직후 `stream-created` SSE 이벤트로 `streamId`를 내려준다.

```text
event: stream-created
data: {"streamId":"...","stockCode":"005930"}
```

### 스트림 종료 및 종목 구독 해지

```http
DELETE /api/v1/investment/realtime/orderbooks/streams/{streamId}
Authorization: Bearer {accessToken}
```

동작:

- `streamId`가 인증 사용자의 스트림인지 검증한다.
- 해당 stream의 구독 상태와 lease를 삭제한다.
- `subscription-changed ENDED` 이벤트를 발행한다.
- 해당 stream의 SSE emitter를 종료한다.

---

## SSE 이벤트 초안

### stream-created

SSE 연결 직후 전송한다.

```json
{
  "streamId": "stream-id",
  "stockCode": "005930"
}
```

### orderbook

KIS 실시간 호가 수신 시 전송한다.

```json
{
  "stockCode": "005930",
  "businessTime": "093730",
  "timeType": "0",
  "asks": [
    {
      "level": 1,
      "price": 71900,
      "quantity": 100
    }
  ],
  "bids": [
    {
      "level": 1,
      "price": 71800,
      "quantity": 200
    }
  ],
  "totalAskQuantity": 1000,
  "totalBidQuantity": 1200
}
```

### heartbeat

연결 유지를 위해 주기적으로 전송한다.

```json
{
  "timestamp": "2026-06-18T11:30:00"
}
```

---

## 구현 단계

### 1단계: 호가 DTO와 KIS payload 파서

- `RealtimeOrderbookSnapshot`
- `RealtimeOrderbookLevel`
- `KisOrderbookMessageParser`

브라우저 검증 도구에서 확인한 `^` 구분 payload 파싱 방식을 서버 코드로 옮긴다.

### 2단계: SSE 로컬 Registry

- `SseEmitter` 생성/저장
- `streamId` 발급
- stream별 userId 관리
- stream별 구독 종목을 최대 1개로 관리
- stockCode별 emitter 조회
- timeout/error/completion 정리

### 3단계: SSE/REST Controller

- 스트림 생성 및 종목 구독 API
- 스트림 종료 및 종목 구독 해지 API

### 4단계: Redis 구독 상태 저장소

- streamId 기준 userId 저장
- streamId 기준 stockCode 저장
- streamId 기준 owner instance 저장
- stockCode 기준 구독 stream 목록 저장
- stream lease 저장
- 구독 stream 수 조회
- 활성 종목 목록 조회
- TTL 갱신
- 명시적 삭제

### 5단계: Redis Pub/Sub

- subscription-changed 이벤트 발행/수신
- orderbook-updated 이벤트 발행/수신
- orderbook-updated 수신 시 로컬 SSE emitter에게 전송
- ENDED 이벤트 수신 시 해당 streamId의 로컬 emitter 종료

### 6단계: KIS WebSocket leader

- Redis lock 기반 leader 선출
- leader lock TTL 갱신
- leader 상실 시 WebSocket 종료
- leader 획득 시 Redis 상태 기준 전체 재구독

### 7단계: KIS WebSocket 구독/해지

- `tr_type=1` 구독 메시지 전송
- `tr_type=2` 구독 해지 메시지 전송
- KIS 구독 목록 로컬 관리
- 구독 한도 검사

### 8단계: Reconcile과 장애 복구

- subscription-changed 이벤트 기반 단건 reconcile
- 주기적 전체 reconcile
- SSE 연결 종료 누락 시 lease TTL 만료로 정리

### 9단계: 테스트

- payload 파서 단위 테스트
- SseEmitterRegistry 단위 테스트
- RedisSubscriptionStore 테스트
- 스트림 생성/종료 서비스 테스트
- leader reconcile 테스트
- 컨트롤러 테스트

---

## 주의 사항

- 한 스트림은 동시에 최대 1개 종목만 구독한다.
- 한 사용자는 여러 스트림을 가질 수 있다.
- 같은 스트림에서 종목을 교체하지 않는다.
- 다른 종목으로 이동하려면 기존 스트림 종료 후 새 스트림을 생성한다.
- 스트림 생성 API는 SSE 연결 생성과 종목 구독을 함께 처리한다.
- 스트림 종료 API는 SSE 연결 종료와 종목 구독 해지를 함께 처리한다.
- Redis Pub/Sub 이벤트만으로 구독 상태를 판단하지 않는다.
- KIS WebSocket 연결은 leader 인스턴스 하나만 유지한다.
- leader가 이벤트를 받으면 Pub/Sub payload를 그대로 믿지 않고 Redis 상태를 다시 조회한다.
- 구독 stream이 0개가 된 종목은 `tr_type=2`로 KIS 구독을 해지한다.
- 기본 브라우저 `EventSource`는 Authorization 헤더를 설정할 수 없으므로 프론트엔드에서는 `fetch-event-source` 사용을 전제로 한다.
- KIS 구독 제한은 코드 상수보다 설정값으로 관리한다.
- SSE 연결이 끊겼을 때 REST 해지 요청이 오지 않아도 Redis lease TTL로 정리될 수 있어야 한다.
