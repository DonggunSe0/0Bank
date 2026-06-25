# 코드에 들어간 CS·보안·성능 지식 총정리

> 기존에 작성된 `domain-user-security-summary.md`, `domain-user-codef-code-guide.md`, `flyway-adoption.md`,
> `transfer/idempotency.md`와 내용이 겹칠 수 있다. 이 문서는 그 문서들을 대체하지 않고, **지금까지
> 코드에 실제로 들어간 모든 CS/보안/성능 관련 장치를 한 곳에 모아 "왜 이렇게 만들었는가"까지** 코드 기준으로
> 다시 정리한 것이다. 범위는 주로 `global/*` 공통 인프라와 `domain/user`, `domain/codef`이며, 코드를 읽으며
> 확인한 사실만 적었다(추측 없음).

---

## 0. 왜 이 구조인가 — 한 줄 요약

이 백엔드는 "상태를 어디에 두는가"를 철저히 나눈다. **인증 상태는 Redis**(블랙리스트, RT, 로그인 실패 카운터,
분산 락), **비즈니스 데이터는 MySQL**(Flyway로 버전 관리), **요청 컨텍스트는 JWT 클레임**(stateless),
**동시성 보장이 필요한 짧은 연산은 Redis Lua 스크립트**(원자성)로 처리한다. 이 분리가 이후 모든 절의
전제가 된다.

---

## 1. 인증/인가 아키텍처

### 1.1 JWT 클레임 구조와 무상태 인증

`JwtProvider`(`global/jwt/JwtProvider.java`)가 발급하는 Access Token의 클레임은 `sub`(userId),
`email`, `jti`(UUID), `iat`, `exp` 다섯 개뿐이다. `jti`를 넣는 이유는 JWT 자체는 서버가 들고 있지 않으므로
"이 토큰 한 장만 즉시 무효화"하려면 토큰을 식별할 고유 키가 필요하기 때문이다(아래 1.4).

`parseUserIdIgnoreExpiry()`는 서명은 검증하지만 만료(`exp`)는 무시하고 클레임을 읽는다. 일반적인
`parseUserId()`와 분리해 둔 이유는 refresh 흐름 자체가 "AT는 만료됐지만 서명은 유효한 상태"를 다뤄야
하기 때문 — 만료된 토큰도 위조되지 않았다면 누구의 토큰인지는 신뢰할 수 있다는 점을 이용한다.
`parseTokenClaims()`는 userId와 jti를 한 번의 파싱으로 같이 꺼내는데, 주석에 "이중 HMAC 검증 방지"라고
적혀 있다 — JWT 파싱(서명 검증)은 HMAC 연산이라 비용이 들고, 같은 토큰을 두 번 파싱하면 그 연산을 두 번
하는 것과 같다. 호출부마다 따로 `parseUserId` + `extractJti`를 부르면 같은 토큰을 두 번 검증하게 되므로,
한 번의 파싱 결과를 재사용하도록 묶어 둔 것.

### 1.2 AT/RT 분리 보관 — XSS와 CSRF를 각각 다른 메커니즘으로 방어

`AuthController.buildRtCookie()` (`domain/user/controller/AuthController.java`)를 보면 로그인 응답에서
**Access Token은 JSON 바디로, Refresh Token은 `Set-Cookie` 헤더로** 내려보낸다. 이건 단순한 구현 선택이
아니라 두 토큰이 노출됐을 때의 피해 범위가 다르기 때문에 보관 위치를 다르게 잡은 것이다.

- AT는 바디로 줘서 프론트가 메모리에 들고 매 요청 `Authorization` 헤더에 직접 박아 보낸다. JS가 접근
  가능한 저장소(메모리)에 있으므로 XSS에 노출되더라도, AT는 수명이 1시간(`jwt.access-token-expiration-seconds`)
  으로 짧고 jti 블랙리스트로 개별 무효화가 가능해 피해가 제한적이다.
- RT는 `httpOnly(true)`로 내려가 JS가 절대 읽을 수 없다 → **XSS로 RT 탈취 불가능**. 대신 쿠키이므로
  CSRF(다른 사이트가 사용자 브라우저를 시켜 쿠키를 자동으로 실어 보내는 공격) 표적이 될 수 있는데,
  이를 `sameSite("Strict")`로 막는다 — 다른 출처(origin)에서 시작된 요청에는 이 쿠키가 전혀 동봉되지 않는다.
  거기다 `path("/api/v1/auth")`로 쿠키 자체의 도달 범위를 인증 엔드포인트로만 좁혀, 혹시 다른 API가
  쿠키를 의도치 않게 읽는 경로조차 차단한다.
- `secure(cookieSecure)`는 `${cookie.secure:false}` — dev는 HTTP라 false, `application-prod.yml`에서
  `cookie.secure: true`로 덮어써 운영(HTTPS 전용)에서는 평문 채널로 쿠키가 새 나가지 않게 한다. 이
  플래그를 환경별로 분리한 이유는, dev에서 `secure=true`로 박아두면 HTTP localhost 테스트에서 쿠키가
  아예 전송되지 않아 로컬 개발이 막히기 때문 — 보안과 개발 편의 사이의 절충을 프로파일로 해결.
- 로그아웃은 `expireRtCookie()`로 동일한 속성(httpOnly/sameSite/secure/path)에 `maxAge(0)`만 다르게
  줘서 즉시 만료시킨다. 속성이 하나라도 다르면(예: path 다름) 브라우저가 "다른 쿠키"로 인식해 기존
  쿠키를 지우지 못하므로, 발급 시 쿠키와 만료 시 쿠키의 속성을 동일하게 맞춘 것이 중요하다.

### 1.3 Refresh Token Rotation과 원자적 소비 — Race Condition 방지의 교과서적 사례

`RefreshTokenService`(`global/jwt/RefreshTokenService.java`)는 RT를 JWT가 아니라 **opaque UUID**로
발급하고 Redis에 `refresh:{userId}` → 토큰 값으로 저장한다. JWT로 만들지 않은 이유는 RT는 클레임을
읽을 필요가 없고(서버가 Redis에 들고 있는 값과 일치하는지만 보면 됨) 그냥 추측 불가능한 랜덤 값이면
충분하기 때문 — 불필요하게 자기서술적(self-describing)인 토큰을 만들 이유가 없다.

핵심은 `validateAndConsume()`이 **GET-then-DELETE를 두 번의 Redis 호출이 아니라 `getAndDeleteIfMatchScript`
Lua 스크립트 한 번으로** 수행한다는 점이다. 만약 "GET해서 값 비교 → 같으면 DELETE"를 애플리케이션
코드에서 두 단계로 나눠 하면, 두 요청이 동시에 같은 RT로 refresh를 호출했을 때 둘 다 GET에서 유효한
값을 읽고 둘 다 DELETE를 통과하는 **TOCTOU(Time-of-check to time-of-use) race condition**이 발생한다 —
RT 하나가 두 번 쓰이게 되는 것. Lua 스크립트는 Redis 안에서 단일 스레드로 원자적으로 실행되므로
"읽고 비교하고 지우는" 세 동작이 다른 클라이언트의 명령과 끼어들 여지 없이 한 단위로 끝난다. 주석에
"동시 요청 중 하나만 성공시킨다"라고 명시된 게 이 의도다. `issue()`는 기존 RT를 무조건 덮어써
"유저당 RT 1개"를 강제한다(다중 세션을 의도적으로 허용하지 않는 설계).

### 1.4 Access Token 무효화 — 자기 소멸하는 블랙리스트

`TokenBlocklistService`(`global/jwt/TokenBlocklistService.java`)는 `block(jti)` 시 Redis에
`blocklist:{jti}` → `"1"`을 쓰는데, **TTL을 고정값이 아니라 "그 토큰이 원래 만료되기까지 남은 시간"**으로
준다(`JwtProvider.getRemainingExpirySeconds`). 이렇게 하면 블랙리스트 엔트리가 토큰의 자연 만료
시점과 정확히 같은 시각에 Redis에서 자동으로 사라진다 — 별도의 정리(GC) 배치가 필요 없는 self-cleaning
자료구조다. TTL을 영구로 잡으면 메모리가 무한히 쌓이고, 너무 짧게 잡으면 토큰이 아직 유효한데
블랙리스트가 먼저 풀려버리는 문제가 생기므로, "토큰의 남은 수명"이 유일하게 정확한 TTL 값이다.

`JwtAuthenticationFilter`는 매 요청마다 `isBlocked(jti)`를 확인해 블랙리스트에 있으면
`SecurityContextHolder`를 비운다. 로그아웃/비밀번호 변경/탈퇴가 이 메커니즘을 쓰는 이유는, RT만
지우는 것으로는 부족하기 때문이다 — `UserService.changePassword()`의 주석대로 "RT만 지우면 탈취자가
AT 만료 전까지 계속 접근 가능"하다. RT 삭제는 "더 이상 새 AT를 발급받지 못하게" 막을 뿐, 이미 발급된
AT는 자기 만료 시각까지 그대로 유효하므로, 즉시 차단하려면 현재 AT의 jti를 블랙리스트에 넣어야 한다.

### 1.5 로그인 실패 잠금 — 슬라이딩 윈도우 카운터

`LoginAttemptService`(`domain/user/service/LoginAttemptService.java`)는 `login:fail:{email}` 키에
실패할 때마다 `incrAndExpireScript`로 INCR + EXPIRE(30분)를 같이 실행한다. 이 스크립트는 **매 실패마다
TTL을 다시 30분으로 늘린다**(sliding window) — 뒤에 나오는 일일 한도 카운터(`incrWithExpireIfNewScript`,
최초 1회만 EXPIRE)와는 의도적으로 다른 동작이다. 로그인 실패는 "마지막 실패로부터 30분"이 의미가
있고(공격이 끊긴 뒤에는 빠르게 풀려야 사용자 불편이 없음), 일일 한도는 "달력상 하루"가 의미가 있으므로
(자정에 한꺼번에 리셋되어야 함) 같은 INCR+TTL 패턴이라도 EXPIRE를 거는 시점을 다르게 짠 것 — 같은
원자성 도구라도 도메인 요구사항에 따라 다른 변종을 쓴 사례.

5회 실패 시 잠금이고, `clearFailures()`는 로그인 성공 시 호출돼 카운터를 지운다.

### 1.6 계정 열거(Account Enumeration) 공격 방지

`UserService.login()`의 순서를 보면 **비밀번호 검증 → 그 다음에 상태(DORMANT/WITHDRAWN) 체크**로 되어
있다. 만약 순서를 반대로(이메일 존재 확인 → 비밀번호 확인) 했다면, 공격자가 "이메일 미가입"과
"이메일은 있는데 비밀번호 틀림"을 응답 메시지나 타이밍 차이로 구분해 가입된 이메일 목록을 추려낼 수
있다(계정 열거). 비밀번호 검증을 먼저 통과해야만 상태 분기를 타게 만들어, 존재하지 않는 이메일과
비밀번호가 틀린 이메일이 동일한 실패 메시지/처리 경로를 거치게 한다.

### 1.7 도메인 스코프 예외 처리

`GlobalExceptionHandler`(`global/exception/`)는 어떤 도메인 패키지도 import하지 않는다 — 공통
인프라(검증 오류, 알 수 없는 예외 등)만 다룬다. 반면 `JwtException`(refresh 흐름에서만 컨트롤러까지
전파됨)은 `UserExceptionHandler`(`@RestControllerAdvice(basePackages = "com.team10.backend.domain.user")`)
가 처리한다. `JwtAuthenticationFilter`도 같은 `JwtException`을 잡지만 그건 시큐리티 필터 단계라
컨트롤러에 도달하기 전에 끝난다 — 같은 예외 타입이 발생 위치에 따라 의미가 다르므로(필터 단계의
401 vs 비즈니스 흐름의 "refresh 토큰 무효"), 전역 핸들러에 도메인 지식을 묻히지 않고 도메인별 advice로
나눈 것이 이 아키텍처의 핵심이다.

---

## 2. 암호화/해싱 — "용도에 맞는 프리미티브 선택"이라는 CS 지식

이 코드베이스에는 민감정보를 다루는 방식이 네 가지가 있고, 각각 풀어야 하는 문제가 다르기 때문에
서로 다른 알고리즘을 쓴다. 이걸 한 가지로 통일하지 않은 게 오히려 올바른 설계다.

| 데이터 | 필요한 성질 | 선택한 도구 | 위치 |
|---|---|---|---|
| 로그인 비밀번호 | 단방향 + 느림(브루트포스 저항) + salt 내장 | `BCryptPasswordEncoder` | `SecurityConfig`, `UserService` |
| 주민등록번호(중복 인증 탐지용) | 단방향 + 결정적(동등 비교 가능) + 키 로테이션 | `HmacHasher` (다중 키 버전) | `global/crypto/HmacHasher.java` |
| (별도) 블라인드 인덱스 | 단방향 + 결정적, 단일 키 | `HmacSha256Hasher` | `global/security/HmacSha256Hasher.java` |
| CODEF connectedId | **가역** + 위변조 탐지(인증) | AES-256-GCM | `CodefConnectedIdEncryptor` |
| 은행 비밀번호(CODEF 전송용) | **가역** + 제3자 공개키만으로 암호화 | RSA/PKCS1Padding | `CodefExAccountPasswordEncryptor` |

### 2.1 BCrypt — 비밀번호는 왜 느려야 하는가

`SecurityConfig`가 `BCryptPasswordEncoder` 빈을 등록하고 `UserService`가 회원가입/비밀번호 변경에
쓴다. BCrypt를 고른 이유는 평문 SHA-256 같은 빠른 해시와 달리 **의도적으로 느리게(adaptive cost)**
설계되어 있어, DB가 유출되더라도 초당 시도 가능한 브루트포스 횟수가 현저히 줄어든다. 또 salt가
해시 결과 안에 내장되어 저장되므로 같은 비밀번호라도 사용자마다 해시 값이 달라져 레인보우 테이블
공격이 무력화된다.

### 2.2 HMAC 블라인드 인덱스 — "검색은 되지만 복호화는 안 되는" 해시

주민등록번호는 평문으로 저장하면 안 되지만, "이 주민번호로 이미 인증한 적이 있는가"를 나중에
동등 비교하려면 단순 마스킹(`123456-*******`)만으로는 검색이 안 된다. `HmacHasher.hash()`는 비밀키로
HMAC-SHA256을 떠서 같은 입력이면 항상 같은 출력(결정적)을 내므로 `WHERE hash = ?` 형태의 동등 검색이
가능하면서도, 키 없이는 원문을 복원할 수 없다(단방향) — "암호화"가 아니라 "블라인드 인덱스"라고
부르는 이유다. `IdentityVerification.completeOcr()`의 주석대로 주민번호 원문은 애플리케이션 어디에도
영속화되지 않고, OCR 응답을 받은 그 순간 마스킹된 표시값 + 해시만 남긴다 — 원문이 메모리를 떠나는
즉시 사라지게 설계.

`HmacHasher`는 `Map<버전, 키>` 구조로 키 로테이션을 지원한다. `hash()`는 항상 **현재 활성 버전**으로
새로 해싱해 `"{version}:{base64}"` 형태로 저장하고, `matches()`는 저장된 문자열의 버전 접두사를 읽어
**그 버전의 키로** 다시 해싱해 비교한다. 키를 롤링해도 과거에 저장된 값들이 깨지지 않는 이유는 이
버전 태깅 덕분 — 옛 키를 삭제하지 않고 `keysByVersion`에 남겨두기만 하면 과거 데이터도 계속 검증
가능하다. (이전 코드 리뷰에서 Gemini가 지적한 버그: 콜론(`:`) 존재 여부만으로 버전 형식인지 판단하지
않고 `containsKey`까지 같이 봤을 때, 폐기된 키 버전이 우연히 레거시 포맷으로 잘못 해석되는 문제가
있어 조건을 콜론 존재 여부만으로 단순화해 수정했다.)

별도로 `HmacSha256Hasher`(단일 키, `HexFormat`으로 hex 인코딩)가 존재하는데 이건 키 로테이션이
필요 없는 곳에 쓰이는 **별개의 해셔**다 — 두 클래스를 혼동하지 않아야 한다. 하나는 다중 키
버전(`global/crypto`), 하나는 단일 키(`global/security`)로 패키지도 분리되어 있다.

### 2.3 AES-256-GCM — 인증된 암호화(AEAD)

`CodefConnectedIdEncryptor`는 CODEF connectedId(은행 계좌 조회를 위한 일종의 세션 식별자, 나중에
다시 CODEF API 호출에 평문으로 써야 해서 **가역**이어야 함)를 AES/GCM/NoPadding으로 암호화한다.
GCM을 고른 이유는 암호화와 동시에 **인증 태그(128bit)**가 같이 생성돼, 복호화 시 ciphertext가
조금이라도 변조됐으면 `AEADBadTagException`이 던져진다 — CBC 같은 비인증 모드는 변조된 ciphertext를
그냥 "이상한 평문"으로 복호화해버려 변조 자체를 감지하지 못한다. 여기에 더해 `updateAAD()`로
`"codef-connected-id:{keyVersion}"`을 AAD(Additional Authenticated Data)로 묶는데, 이건 ciphertext에
포함되진 않지만 인증 태그 계산에 들어가므로 **다른 키 버전으로 암호화된 값을 엉뚱한 버전이라고
속여서 복호화 시도하면 태그 검증에서 막힌다** — 키 버전 혼동 공격까지 차단. IV(nonce)는 매 암호화마다
`SecureRandom`으로 새로 12바이트를 뽑아 같은 평문도 암호문이 매번 달라지게 만든다(IV 재사용은 GCM의
가장 위험한 실수).

### 2.4 RSA — "내가 복호화할 필요가 없는" 암호화

`CodefExAccountPasswordEncryptor`는 사용자가 입력한 은행 비밀번호를 **CODEF가 제공한 RSA 공개키**로
암호화해 CODEF에 전달한다. 비대칭키를 쓰는 이유는 명확하다 — 우리 서버는 이 비밀번호를 복호화할
필요가 전혀 없고(복호화는 CODEF 쪽 개인키로만 가능), 오직 "전송 중 가로채기 방지"만 필요하기
때문이다. 대칭키(AES)를 쓰면 우리도 그 키를 들고 있어야 하는데, 그 키로 복호화해서 우리가 비밀번호를
볼 이유가 없으므로 공개키 암호화가 정확히 맞는 도구다. 생성자에서 키 파싱이 실패해도 앱 기동이
죽지 않게 `isValid` 플래그로 감싸 두고, 실제 `encrypt()` 호출 시점에만 예외를 던진다 — "테스트/dev
환경의 mock 키 때문에 전체 애플리케이션 컨텍스트 로딩이 실패하면 안 된다"는 운영 판단.

---

## 3. 입력 검증 — Defense in Depth

### 3.1 파일 업로드: 헤더는 거짓말을 할 수 있다

`IdentityVerificationService.validateImage()`는 세 단계를 거친다 — 크기(10MB) 체크, `Content-Type`
헤더 체크, 그리고 `hasValidImageSignature()`로 **실제 파일 바이트의 매직바이트**(JPEG `FF D8 FF`,
PNG `89 50 4E 47 0D 0A 1A 0A`)를 확인한다. 세 번째 단계가 핵심인 이유는 `Content-Type` 헤더가
HTTP 요청의 일부로 **클라이언트가 임의로 지정**할 수 있는 값이라는 점이다 — 악성 실행파일에
`Content-Type: image/jpeg`만 붙여 보내면 두 번째 검증은 통과한다. 파일의 앞 8바이트를 직접 읽어
포맷별 고정 시그니처와 비교하는 건 클라이언트가 조작할 수 없는 유일한 단서이기 때문.

### 3.2 멱등성 키 화이트리스트

`IdempotencyService.validateIdempotencyKey()`는 `^[A-Za-z0-9._:-]{1,100}$` 정규식으로 멱등성 키를
검증한다. 클라이언트가 멱등성 키를 자유 문자열로 보낼 수 있게 허용하면 그 값이 DB 컬럼에 그대로
들어가는데, 화이트리스트(허용 문자만 명시)가 블랙리스트(금지 문자만 나열)보다 안전한 이유는
"예상 못 한 특수문자"가 항상 새로 생겨날 수 있기 때문 — 허용할 문자만 정의해두면 그 외 모든
입력이 자동으로 차단된다.

### 3.3 검증 실패의 로그 마스킹

`GlobalExceptionHandler`는 `MethodArgumentNotValidException` 처리 시 거부된 필드 값을 로그로
남기기 전에 `maskIfSensitive()`를 거친다. 필드명에 `password|secret|token|otp|pin|credential`
중 하나라도 포함되면 값 자체를 로그에서 가린다 — 검증 실패 로그는 디버깅에 필요하지만, 검증에
실패한 값이라고 해서 민감하지 않은 건 아니다(예: 비밀번호 형식이 틀려서 막혔어도 그 비밀번호
후보 문자열 자체는 민감 정보).

---

## 4. 민감정보 로그 마스킹 패턴 모음

코드 전체에서 반복되는 마스킹 규칙들:

- 계좌번호: `CodefBankTransferService.maskAccountNumber()` — 앞 최대 6자리 + 뒤 4자리만 노출, 나머지
  `*` (기존 `ExAccountCandidateRes.maskAccountNumber`와 동일한 컨벤션을 따름 — 마스킹 표시 형식을
  코드베이스 전역에서 통일).
- 주민등록번호: `CodefOcrClient.maskIdentity()`(로그용, 앞 6자리만) / `IdentityVerification.mask()`
  (DB 저장용, 앞 6자리 + `-*******`) — 둘 다 "생년월일 부분(앞 6자리)은 식별에 필요하지만 뒷자리
  일련번호는 절대 노출하지 않는다"는 동일한 정책을 로그용과 영속화용으로 각각 구현.
- 1원 송금 인증코드(OTP)는 **의도적으로 평문 로그 노출** — `CodefBankTransferService`의 주석에
  "시연 목적상 의도적으로 평문 노출"이라고 명시. 보안 원칙의 예외를 둘 때는 이유를 코드에 남겨야
  나중에 "왜 여긴 마스킹 안 했지?"라는 오해를 막을 수 있다는 점에서 좋은 사례.
- 500 에러: `GlobalExceptionHandler`의 catch-all `Exception` 핸들러는 클라이언트에게 항상 동일한
  일반 메시지만 반환하고 내부 스택트레이스/메시지는 서버 로그에만 남긴다 — 내부 구현 디테일이
  에러 응답을 통해 외부로 새 나가는 것(information disclosure)을 차단.

---

## 5. 동시성 제어와 Race Condition 방지 — 이 코드베이스의 핵심 CS 주제

### 5.1 Redis Lua 스크립트로 원자성 보장

`RedisScriptConfig`(`global/config/`)는 세 개의 `RedisScript<Long>` 빈을 정의한다.

1. **`incrWithExpireIfNewScript`** — INCR 후, 그 키가 "방금 새로 생성된" 경우에만(즉 INCR 결과가 1일
   때) EXPIRE를 건다. 달력 자정 리셋 카운터(OCR 일일 한도, 1원 송금 일일 한도)에 쓰인다 — 하루의
   "첫" 요청이 그날의 만료 시각(`DailyResetClock.secondsUntilNextMidnight()`)을 정하고, 이후 같은 날
   요청들은 TTL을 건드리지 않아 모든 사용자가 정확히 자정에 리셋된다.
2. **`incrAndExpireScript`** — INCR마다 매번 EXPIRE를 다시 건다(슬라이딩 윈도우). 로그인 실패 잠금,
   1원 인증 시도 횟수 제한에 쓰인다 — "마지막 행위로부터 N분"이 의미 있는 경우.
3. **`getAndDeleteIfMatchScript`** — 주어진 값이 현재 저장된 값과 일치할 때만 원자적으로 DELETE(compare-and-delete).
   RT rotation 소비(5.5 항목과 별개 — 1.3 참고)와 분산 락 해제(5.2) 양쪽에 재사용된다.

왜 이 연산들을 애플리케이션 레벨에서 "GET 하고 → 조건 보고 → SET/DEL"로 짜지 않고 Lua로 Redis 안에서
실행하는가: Redis 명령은 기본적으로 단일 명령 단위로는 원자적이지만, **명령 두 개 사이에는 다른
클라이언트의 명령이 끼어들 수 있다.** Lua 스크립트는 Redis 서버 안에서 한 번에 끝까지 실행되는
단일 단위이므로, 스크립트 내부의 모든 단계가 "분리 불가능한 한 동작"이 된다 — DB 트랜잭션의 원자성
보장과 본질적으로 같은 개념을 Redis 레벨에서 구현한 것.

`RedisScriptInitializer`(`ApplicationListener<ApplicationReadyEvent>`)는 앱 기동 시 정의된 모든
`RedisScript` 빈을 `SCRIPT LOAD`로 Redis에 미리 적재하고 SHA를 로그로 남긴다. 이렇게 하면 런타임에는
스크립트 본문 전체를 매번 네트워크로 보내는 `EVAL` 대신 SHA만 보내는 `EVALSHA`를 쓸 수 있어 페이로드가
줄어든다(스크립트가 캐시 미스로 사라졌을 때를 대비한 폴백은 Spring Data Redis의
`DefaultScriptExecutor`가 처리).

### 5.2 분산 락 — SETNX와 안전한 해제

`CodefAuthClient.fetchWithDistributedLock()`은 여러 서버 인스턴스가 동시에 CODEF OAuth 토큰을
발급받는 것을 막기 위해 `setIfAbsent(lockKey, lockValue, LOCK_TTL)`(Redis `SET key value NX EX`에
대응)로 락을 잡는다. 핵심은 **락 값에 매번 새 UUID를 넣고, 해제할 때 그 UUID가 여전히 자신이 쓴
값과 같은지 확인 후 지운다**(`getAndDeleteIfMatchScript` 재사용)는 점이다. 만약 단순히 `DEL lockKey`로
무조건 지웠다면: 인스턴스 A가 락을 잡고 발급 호출이 `LOCK_TTL`(10초)보다 오래 걸려 락이 자동
만료되고, 그 사이 인스턴스 B가 새로 락을 잡았는데, A가 뒤늦게 작업을 마치고 무조건 DEL을 하면
**B의 락을 A가 지워버리는** 사고가 난다 — 그 직후 C까지 동시에 락을 잡게 되는 연쇄 실패. 자신이
쓴 값인지 비교 후 지우는 패턴(분산 락계의 "compare-and-delete")이 이걸 막는다.

락을 못 잡은 인스턴스는 무한정 기다리지 않고, **지수 백오프 + jitter**로 최대 6회 재확인하며
Redis 공유 캐시(L2)를 폴링한다(`nextBackoff()`). 단순 고정 간격으로 재시도하면 락을 기다리던 여러
인스턴스가 거의 동시에 재확인을 시도해 또 한 번 몰리는 **thundering herd**가 생기는데, jitter(half +
랜덤(0~half))로 각 인스턴스의 재시도 타이밍을 흩뜨려 이를 완화한다. 끝까지 락을 못 잡으면(보유
인스턴스 지연/실패 대비) 가용성을 위해 직접 발급을 시도한다 — 일관성보다 가용성을 우선한 선택이며,
드물게 중복 발급이 발생할 수 있음을 주석에 명시.

### 5.3 더블 체크 락킹(Double-Checked Locking)

`CodefAuthClient.getAccessToken()`은 `synchronized(tokenLock)` 블록 진입 **전과 후 모두**
`tokenCache`가 유효한지 확인한다. 첫 번째 체크는 "이미 캐시가 있으면 굳이 락을 잡지 않고 바로
반환"하는 빠른 경로(fast path) — 대부분의 호출이 여기서 끝나 동기화 비용 자체를 피한다. 두 번째
체크는 "락을 기다리는 동안 다른 스레드가 이미 캐시를 채웠을 수도 있으니" 다시 확인하는 것 — 같은
인스턴스의 여러 스레드가 토큰 만료 시점에 동시에 들어와도 실제 CODEF API 호출과 Redis 조회를 한
스레드만 하게 만든다. 이건 JVM 메모리 모델에서 흔히 쓰이는 "비싼 초기화를 한 번만"의 표준 패턴이고,
여기서는 그 대상이 "원격 API 호출 + Redis 조회"라는 더 비싼 연산이라는 점이 특징.

캐시 자체도 `record TokenCache(String token, long expiryEpoch)`를 `AtomicReference`에 담아, "토큰
값"과 "만료 시각"이 항상 같은 스냅샷으로 같이 읽히고 같이 갱신되게 한다(둘을 별도 필드로 뒀다면
한쪽만 갱신된 중간 상태를 다른 스레드가 읽는 race가 가능했을 것).

### 5.4 캐시 계층(L1/L2) — 분산 환경에서 중복 발급 방지

같은 `CodefAuthClient` 안에서 토큰 조회는 **L1(인스턴스 로컬 `AtomicReference`) → L2(Redis 공유 캐시)
→ 분산 락 + 실제 발급**의 3단계로 내려간다. 인스턴스가 여러 대인 분산 서버 환경에서 L1만 쓰면
인스턴스 수만큼 중복으로 토큰을 발급받게 되므로(각 인스턴스가 "나는 토큰이 없다"고 생각), Redis를
인스턴스 간 공유 캐시로 둬서 "다른 인스턴스가 이미 받아온 토큰"을 재사용한다. L1, L2 모두 미스일
때만(즉 정말 아무도 가진 토큰이 없을 때만) 분산 락을 잡고 실제 외부 API를 호출 — 비용이 가장 큰
연산(외부 API 호출)을 가장 마지막 수단으로 미루는 전형적인 캐시 계층 설계.

### 5.5 DB Unique Constraint — 애플리케이션 체크만으로는 부족한 이유

`UserService.signup()`은 `existsByEmail()`로 먼저 빠르게 중복을 확인하지만(빠른 실패), 진짜 보장은
`users.email` 컬럼의 **DB Unique 인덱스**(`UK6dotkott2kjsp8vw4d0m25fb7`, `V1__baseline_schema.sql`)다.
애플리케이션 레벨의 "조회 후 없으면 삽입" 체크 사이에는 항상 시간 간격이 있어, 두 요청이 동시에
같은 이메일로 가입을 시도하면 둘 다 "중복 없음"을 보고 둘 다 INSERT를 시도하는 race가 발생할 수
있다. `saveAndFlush()`를 `DataIntegrityViolationException`으로 감싸 잡는 이유가 이 race의 최종
방어선 — DB가 유니크 제약으로 둘 중 하나를 거부하면 그걸 회원가입 실패로 변환한다. **애플리케이션
체크는 사용자 경험(빠른 에러 메시지)을 위한 것이고, DB 제약이 실제 정합성을 보장하는 것** — 이
이중 구조가 이 코드베이스 전체의 반복되는 패턴이다(`Idempotency`의 `uk_user_idempotency_key`도
동일한 이유로 같은 구조를 취함 — 5.6 참고).

`V1`~`V3` 마이그레이션에 정의된 다른 비즈니스 유니크 제약들도 같은 사고방식을 보여준다: `fx_wallets`의
`(user_id, currency_code)`(유저당 통화별 지갑 1개), `external_account`의
`(user_id, organization, account_number_hash)`(같은 외부계좌 중복 연결 방지, 계좌번호는 해시로
저장되어 있어 유니크 인덱스도 해시 컬럼에 걸림), `external_asset_transactions`의
`(external_account_id, transaction_key)`(외부 거래 중복 적재 방지 — 같은 거래를 여러 번 동기화해도
멱등), `investment_orders`의 `(investment_account_id, idempotency_key)`(주문 중복 제출 방지, 컬럼이
`binary(16)`인 걸 보면 UUID를 바이너리로 저장해 인덱스 크기를 줄임), `codef_external_account_connection`의
`(user_id, organization)`(같은 기관 중복 연결 방지) 등 — "동시에 두 번 들어와도 한 번만 성립해야
하는 비즈니스 규칙"은 전부 DB 유니크 제약으로 못 박혀 있다.

### 5.6 멱등성(Idempotency) 패턴 — HTTP 멱등성을 AOP + DB로 구현

`global/idempotency` 패키지는 클라이언트가 보낸 멱등성 키를 기준으로 "같은 요청이 두 번 와도 실제
처리는 한 번만" 보장하는 범용 메커니즘이다. 동작 순서:

1. `@Idempotent(operationType=..., key="#req.idempotencyKey", userId="#userId", hashFields={...})`를
   메서드에 붙이면 `IdempotencyAspect.handle()`이 `@Around`로 감싼다.
2. SpEL(`#req.idempotencyKey` 같은 표현식)로 메서드 인자에서 키/유저ID를 꺼내고, `hashFields`로 지정한
   값들 + operationType을 합쳐 `IdempotencyRequestHasher.generate()`로 SHA-256 해시를 만든다 — 이
   해시는 "같은 멱등성 키로 들어왔지만 실제 요청 내용이 다른 경우"(키 재사용 버그 또는 악의적 시도)를
   구분하는 용도다.
3. `IdempotencyReservationFacade.reserveOrResolveDuplicate()`가 `(user_id, idempotency_key)` 유니크
   키로 "예약"을 시도한다. 여기서도 5.5와 동일한 이중 구조가 등장한다 — 먼저 `findByUser_IdAndIdempotencyKey`로
   기존 레코드를 조회하지만, 두 요청이 정말 동시에 들어오면 둘 다 "없음"을 보고 둘 다 INSERT를
   시도할 수 있다. 이때 DB의 `uk_user_idempotency_key` 제약이 하나를 거부하고, 그 거부
   (`DataIntegrityViolationException`)를 **Facade가 잡아서 "방금 누가 만든 레코드를 다시 조회"하는
   흐름으로 전환**한다(`reserveOrResolveDuplicate`의 catch 블록). 즉 DB 제약 위반이 에러가 아니라
   "다른 스레드가 이겼으니 그 결과를 따라간다"는 정상 분기로 흡수된다. 단, 예외의 원인이 정말 이
   유니크 제약 위반인지(`ConstraintViolationException`의 constraint 이름, 또는 메시지 문자열
   매칭으로 폴백) 확인한 뒤에만 재조회하고, 다른 종류의 무결성 위반이면 그대로 던진다.
4. 기존 레코드가 있으면 상태에 따라 분기: `PROCESSING`(아직 처리 중 — 동시에 같은 요청이 진행 중이니
   클라이언트는 잠시 후 재시도), `SUCCESS`(저장해 둔 JSON 응답을 그대로 역직렬화해 재생, 실제 비즈니스
   로직은 다시 실행하지 않음 — replay), `FAILED`/`EXPIRED`는 각각의 에러로 응답. 이때 저장된
   `requestHash`가 지금 들어온 요청과 다르면(`isDifferentRequest`) 같은 키를 다른 내용으로 재사용한
   것으로 보고 충돌 에러를 던진다 — 멱등성 키의 의미는 "같은 요청의 재시도"이지 "임의의 새 요청에
   재사용 가능한 토큰"이 아니기 때문.
5. 새로 예약(`reserve`)됐으면 `joinPoint.proceed()`로 실제 메서드를 실행하고, 성공하면
   `completeSuccess()`(응답을 JSON으로 직렬화해 저장), 비즈니스 예외(`BusinessException`)면
   `completeFailure()`로 마킹한다.

흥미로운 트랜잭션 디테일: `IdempotencyService.reserve()`와 `completeFailure()`는
`@Transactional(propagation = Propagation.REQUIRES_NEW)`다. `completeSuccess()`는 그냥
`@Transactional`(부모 트랜잭션에 참여). REQUIRES_NEW를 쓴 이유는, 이 멱등성 레코드의 생성/실패
기록이 **바깥의 비즈니스 트랜잭션이 롤백되더라도 별도로 남아 있어야** 하기 때문이다 — 예를 들어
비즈니스 로직이 실패해서 전체 트랜잭션이 롤백되더라도, "이 키로 한 번 시도했고 실패했다"는 사실
자체는 독립적인 트랜잭션으로 이미 커밋되어 있어야 다음 재시도가 정확한 상태를 본다. 반대로 성공
응답 저장은 비즈니스 로직과 한 트랜잭션으로 묶여 있어도 무방한데, 비즈니스 로직이 성공했다는
전제 하에 저장되는 것이라 같은 트랜잭션 경계에 있어도 문제가 없다.

`IdempotencyReservationFacade`가 별도 클래스로 분리된 이유도 트랜잭션 메커니즘과 관련 있다 — 주석에
"self-invocation 트랜잭션 문제 해결 목적"이라고 적혀 있다. Spring의 `@Transactional`은 AOP 프록시를
통해 동작하므로, 같은 클래스 안의 한 메서드가 `this.다른메서드()`로 트랜잭션 메서드를 호출하면 그
호출은 프록시를 거치지 않아 새 트랜잭션 경계가 생기지 않는다(self-invocation 문제). `reserve()`
호출과 그 예외를 잡아 재호출하는 로직을 같은 클래스에 두면 이 문제에 걸리므로, Facade를 별도
빈으로 분리해 `idempotencyService.reserve()` 호출이 항상 진짜 프록시를 통하게 만들었다.

오래된(stuck) `PROCESSING` 레코드는 `IdempotencyCleanupScheduler`가 1분마다(`fixedDelayString`)
`expireStaleProcessing(Duration.ofMinutes(10))`로 10분 이상 `PROCESSING`인 레코드를 `EXPIRED`로
정리한다 — 처리 중이던 서버가 죽어서 끝내 `complete`/`fail`이 호출되지 못한 레코드가 영원히
`PROCESSING`으로 남아 재시도를 영구히 막는 것을 방지.

### 5.7 동시 요청 방지 락 + 락 소유권의 이전

`IdentityVerificationService.startOneWonVerification()`은 1원 송금 시작 전에
`oneWonVerificationService.tryAcquireStartLock(userId)`(Redis `setIfAbsent` 기반, TTL 35초)를 잡는다.
같은 유저가 거의 동시에 두 번 호출하면 실제 은행 송금이 중복 실행될 수 있기 때문. 그런데 이 메서드는
`@Transactional(propagation = Propagation.NOT_SUPPORTED)`이고 실제 송금은 트랜잭션 커밋 이후
비동기로 처리되므로(6.2 참고), **락을 언제 풀어야 하는지가 까다롭다** — 동기 메서드가 끝나는
시점에는 아직 비동기 처리가 시작도 안 했을 수 있다. 코드는 `kickedOff` 플래그로 이걸 해결한다:
이벤트 발행까지 정상적으로 마쳤으면(`kickedOff = true`) `finally` 블록에서 락을 풀지 않고, **락
해제 책임을 비동기 처리기(`OneWonTransferProcessor`)의 `finally`로 넘긴다.** 즉 "트랜잭션이
끝났다"가 아니라 "실제 송금이 끝났다"를 락 해제 조건으로 삼아야 하므로, 락의 소유권을 동기 코드에서
비동기 코드로 명시적으로 이전한 것 — 그 사이에 예외가 나서 비동기로 못 넘어갔다면(`kickedOff`가
여전히 false) 락은 동기 코드의 `finally`가 책임지고 푼다.

---

## 6. 트랜잭션 설계

### 6.1 외부 API 호출과 DB 트랜잭션의 분리

`UserService.signup()`과 `IdentityVerificationService.startOneWonVerification()` 두 곳 모두 같은
패턴을 쓴다: 메서드 자체는 `@Transactional(propagation = Propagation.NOT_SUPPORTED)`로 **트랜잭션을
끊어버리고**, 그 안에서 외부 API(PortOne 본인인증 조회, 은행 점검시간 확인 등)를 트랜잭션 없이
호출한 뒤, DB에 실제로 쓰기가 필요한 부분만 `new TransactionTemplate(txManager).execute(...)`로
감싸 짧은 트랜잭션을 만든다. 이렇게 분리하지 않으면(즉 메서드 전체가 하나의 `@Transactional`이면)
외부 API가 응답하는 동안 DB 커넥션을 계속 붙잡고 있게 된다 — 외부 API 응답이 늦어질수록 커넥션
풀의 가용 커넥션이 줄어들고, 동시 요청이 몰리면 커넥션 풀 고갈로 이어진다. 트랜잭션(=DB 커넥션
점유 시간)을 "실제 DB에 쓰는 순간"만으로 최소화하는 게 이 패턴의 목적.

### 6.2 트랜잭션 커밋 후 비동기 처리 — 이벤트 기반과 콜백 기반의 공존(불일치)

OCR 처리(`OcrSubmittedEvent`)와 1원 송금 처리(`OneWonTransferRequestedEvent`)는 둘 다
`ApplicationEventPublisher.publishEvent()`로 이벤트를 발행하고, 실제 리스너는 `@TransactionalEventListener(phase = AFTER_COMMIT)`
형태로 트랜잭션 커밋 후에만 실행되도록 만들어져 있다(`OcrSubmittedEventListener`,
`OneWonTransferRequestedEventListener` — 클래스 이름에서 추정, 패턴은 IdentityVerificationService의
주석 "afterCommit() 콜백 직접 등록 대신 이벤트 발행"으로 명시). 굳이 "커밋 후"로 미루는 이유는,
OCR/1원송금 모두 **DB에 INSERT한 레코드(`IdentityVerification`)의 id를 가지고 별도 스레드에서 다시
조회**해야 하는데, 트랜잭션이 아직 커밋되지 않은 상태에서 다른 스레드(다른 DB 커넥션)가 조회하면
그 레코드를 아직 볼 수 없기 때문이다(트랜잭션 격리) — 반드시 커밋이 완료된 *후*에 비동기 처리가
시작돼야 한다.

반면 `ExchangeRateService`(`domain/exchange/service/ExchangeRateService.java`)는 같은 목적을
**`TransactionSynchronizationManager.registerSynchronization()`으로 직접 익명 클래스를 등록해
`afterCommit()`을 구현하는** 옛 방식을 그대로 쓰고 있다(주석: "익명클래스 & 콜백메서드 afterCommit에서
Redis 저장 실행 -> DB 커밋 성공시에만 Redis에도 저장됨" — DB 커밋과 Redis 캐시 갱신의 순서를 보장해
"DB엔 없는데 캐시엔 있는" 불일치를 막는 것이 목적이라는 점은 동일). 두 방식 모두 "커밋 후에만
실행"이라는 결과는 같지만, 구현 스타일이 코드베이스 안에서 통일되어 있지 않다 — 이벤트 기반
(`@TransactionalEventListener`)이 더 나중에 도입된 패턴으로 보이며, 리스너 클래스가 분리되어
테스트하기 쉽고 발행자와 처리자가 느슨하게 결합된다는 장점이 있는 반면, 옛 방식은 등록 코드와
서비스 로직이 한 메서드 안에 섞여 있다. (이 불일치는 인지하고 있는 미해결 항목이며 12장에도
다시 적는다.)

### 6.3 REQUIRES_NEW — 비즈니스 트랜잭션과 독립적으로 커밋되어야 하는 기록

5.6에서 다룬 `IdempotencyService.reserve()`/`completeFailure()`의 `REQUIRES_NEW`가 대표적인 예.
일반적으로 같은 요청 흐름 안의 여러 DB 작업은 하나의 트랜잭션으로 묶어 "전부 성공 또는 전부 롤백"을
보장하는 게 기본이지만, **감사/추적 목적의 기록은 그 바깥 트랜잭션의 운명과 무관하게 남아야 하는
경우**가 있다 — 멱등성 레코드가 정확히 그런 사례.

### 6.4 읽기 전용 트랜잭션 기본값

`UserService`, `IdentityVerificationService` 모두 클래스 레벨에 `@Transactional(readOnly = true)`를
선언하고, 쓰기가 필요한 개별 메서드에만 `@Transactional`(쓰기 모드)을 오버라이드한다. `readOnly=true`는
Hibernate에게 "이 트랜잭션에서는 dirty checking(변경 감지)을 위한 스냅샷 비교가 필요 없다"는 힌트를
줘서 영속성 컨텍스트의 오버헤드를 줄이고, 일부 DB 드라이버/DB 자체에서는 읽기 전용 트랜잭션에
최적화된 실행 경로를 탈 수 있다. "기본은 읽기 전용, 쓰기가 필요한 곳만 명시적으로 예외"로 잡아두면
실수로 의도치 않은 쓰기가 일어나는 것도 방지된다(읽기 전용 트랜잭션에서 엔티티를 변경해도 flush가
안 되므로 조용히 무시됨 → 버그를 일찍 드러낼 수 있음).

---

## 7. 비동기 처리와 스레드풀 설계

### 7.1 I/O-bound 워크로드에 맞춘 전용 Executor 분리

`AsyncConfig`(`global/config/AsyncConfig.java`)는 `ocrExecutor`(core 10/max 30)와
`oneWonExecutor`(core 5/max 20) 두 개의 별도 스레드풀을 만든다. 일반적으로 스레드풀 크기는 "CPU
코어 수"를 기준으로 잡지만, 이 두 풀은 **CPU 연산이 거의 없고 외부 API 응답을 기다리는 시간이
대부분(I/O-bound)**이므로 CPU 코어 수보다 훨씬 큰 풀 크기를 잡아도 의미가 있다 — I/O 대기 중인
스레드는 CPU를 점유하지 않으므로, "동시에 진행 가능한 외부 호출 수"가 실질적인 한계이고 그 한계는
CPU 코어 수와 무관하다. 두 풀을 하나로 합치지 않은 이유는, 1원 송금 한 건의 처리 시간(은행 응답
대기, 최대 30초)이 OCR 한 건(수 초)보다 훨씬 길기 때문에, 같은 풀을 쓰면 느린 1원송금 작업들이
풀의 스레드를 오래 점유해 OCR 작업이 큐에서 굶는(starvation) 상황이 생길 수 있다 — 워크로드 특성이
다른 두 작업을 격리해 한쪽의 지연이 다른 쪽 처리량에 영향을 주지 않게 한 것.

### 7.2 커스텀 멀티스레드 TaskScheduler

`SchedulingConfig`는 Spring Boot의 `@Scheduled` 기본 실행기(단일 스레드)를 풀 크기 6
(`app.scheduling.pool-size`)인 `ThreadPoolTaskScheduler`로 교체한다. 기본값을 그대로 두면 등록된
모든 `@Scheduled` 작업(`ExchangeRateSyncScheduler`의 1분 주기 환율 동기화,
`IdempotencyCleanupScheduler`의 1분 주기 정리 등)이 **하나의 스레드를 순서대로 나눠 써야 해서**,
한 작업이 오래 걸리면 그 다음 작업들의 실행 시각이 전부 밀린다 — 스케줄된 작업이 여러 개로 늘어난
이 코드베이스에서는 단일 스레드가 곧 병목이 된다. 에러 핸들러도 같이 등록해 두어, 한 스케줄
작업에서 예외가 나도 로그로 남기고 스케줄러 자체는 죽지 않게(다른 작업들의 실행을 막지 않게) 처리한다.

---

## 8. 외부 연동 HTTP 클라이언트와 커넥션 관리

### 8.1 타임아웃 — "무한 대기"라는 가장 흔한 장애 원인

`RestClientConfig`의 전역 기본 `RestClient`는 connect 5초 / read 5초로 설정되어 있다. 주석에
명시된 이유가 "무한 대기 방지" — 타임아웃을 걸지 않으면 외부 서버가 응답을 영원히 안 주는 단 한
번의 사고가 그 요청을 처리하던 스레드를 영원히 블로킹시키고, 스레드풀(또는 서블릿 컨테이너의
요청 처리 스레드)이 차례로 고갈되어 전체 서비스가 멎는 연쇄 장애(cascading failure)로 이어질 수
있다. CODEF 1원송금처럼 실제로 5초보다 오래 걸리는 호출은 전역 타임아웃을 그대로 쓰지 않고
**전용 RestClient를 따로 만들어 30초로 늘린다**(`CodefBankRestClientConfig`) — "모든 외부 호출에
같은 타임아웃"이 아니라 "그 호출의 실제 응답 특성에 맞는 타임아웃"을 호출별로 분리해서 준 것.

### 8.2 JDK HttpClient vs SimpleClientHttpRequestFactory — 커넥션 풀링 차이

`RestClientConfig`(전역)와 `CodefBankRestClientConfig`(1원송금)는 둘 다 JDK `HttpClient` +
`JdkClientHttpRequestFactory`를 쓴다. 이 선택의 이유는 JDK `HttpClient`가 **커넥션 풀링과
keep-alive를 내부적으로 관리**해서, 같은 호스트로 반복 호출할 때 TCP 연결과 TLS 핸드셰이크를
매번 새로 하지 않고 재사용하기 때문이다(`CodefBankRestClientConfig`의 주석에 이 비교가 명시되어
있음). 반면 `CodefExAccountRestClientConfig`(계좌조회 OAuth/API용 RestClient 2개)는
`SimpleClientHttpRequestFactory`(JDK `HttpURLConnection` 기반)를 쓰는데, 이 팩토리는 호출마다
새 연결을 맺는 경향이 있어 TCP/TLS 핸드셰이크 비용이 매 요청마다 발생한다. **두 설정 클래스가
같은 CODEF 연동임에도 커넥션 관리 방식이 다르다는 것은 이 코드베이스에 남아 있는 아키텍처
불일치**이며, 성능에 영향을 줄 수 있는 지점이다(12장에서 다시 언급).

### 8.3 용도별 자격증명/Bean 분리 — Redis 키 네임스페이스 충돌 방지

CODEF는 상품(계좌조회/OCR용, 1원송금용)마다 별도 자격증명을 발급하므로,
`CodefAuthClient`는 `@Component` 단일 공유 빈이 아니라 `CodefAuthClientConfig`에서 `@Qualifier`로
구분된 두 개의 별도 인스턴스(`accountInquiry`, `oneWonTransfer`)로 등록된다. 생성자 인자로 받는
`purpose` 문자열이 Redis 캐시 키(`codef:oauth:token:{purpose}`)와 락 키의 네임스페이스가 되어, 두
용도의 토큰이 같은 Redis 키를 두고 충돌하지 않는다. `CodefOcrClient`와 `CodefBankTransferService`는
각각 맞는 `@Qualifier`로 자신의 용도에 맞는 `CodefAuthClient`를 주입받는다 — 자격증명을 잘못 섞어
쓰면 한쪽 상품의 호출 한도/권한으로 다른 상품 API를 호출하는 사고가 날 수 있어, 타입(클래스) 레벨이
아니라 인스턴스(빈) 레벨에서 격리한 것.

### 8.4 선언적 HTTP 인터페이스

`CodefHttpServiceConfig`는 Spring 6의 `HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))`로
`CodefOAuthExchange`라는 인터페이스의 동적 프록시를 만든다 — 메서드 시그니처와 애너테이션만으로
HTTP 호출이 만들어지는 방식(Feign과 유사한 개념이지만 Spring 자체 기능). `CodefAuthClient`는 이
프록시를 통해 OAuth 토큰 발급 POST를 호출하고, 캐싱/락 로직만 직접 담당한다 — "HTTP 호출 방법"과
"호출 결과를 어떻게 캐싱/보호할지"라는 두 관심사가 클래스 단위로 나뉘어 있다. 반면 `PortOneClient`는
같은 코드베이스에서 `RestClient.get()...`을 직접 호출하는 명령형 스타일을 쓴다 — 두 스타일이 공존하며,
선언적 인터페이스는 호출 패턴이 단순/반복적인 OAuth 발급에, 명령형 스타일은 응답 처리가 더 커스텀한
PortOne 조회에 쓰인 것으로 보인다.

---

## 9. 캐싱 전략

### 9.1 CODEF OAuth 토큰 2단 캐시 (5.4와 동일 내용, 캐싱 관점 정리)

L1(로컬 `AtomicReference`, 만료 5분 전 갱신) → L2(Redis 공유 캐시) → 분산 락+실제 발급. 캐시 적중률
관점에서 보면, 대부분의 요청은 L1에서 끝나 Redis 네트워크 호출조차 발생시키지 않는다 — 가장 빠른
계층에서 가장 자주 끝나도록 계층화하는 것이 캐시 설계의 기본 원칙이고, 이 구조가 그 원칙을 그대로
따른다.

### 9.2 EnumMap + Copy-on-Write — 락 없는 동시 읽기/쓰기

`MarketHolidayCache`(`domain/investment/marketholiday/cache/`)는 `volatile Map<MarketType, Set<LocalDate>>`
필드 하나로 캐시를 들고 있다가, 갱신(`replace()`) 시 **기존 맵을 변경하지 않고 새 `EnumMap`을 만들어
통째로 교체**한다. 주석에 명시된 이유: "조회 스레드와 스케줄러 갱신 스레드가 동시에 접근할 수
있으므로 캐시 객체를 수정하지 않고 새 Map을 생성한 뒤 통째로 교체한다." 만약 기존 맵을 직접
`put()`으로 수정했다면, 조회 스레드가 그 수정이 끝나지 않은 중간 상태(예: 일부 엔트리만 갱신된
상태)를 볼 수 있는 race가 생긴다. 참조 교체는 원자적(`volatile` 필드에 새 참조를 대입하는 것은
JMM상 원자적 연산)이므로, 어떤 조회 스레드든 "갱신 전 통째로 일관된 맵" 또는 "갱신 후 통째로
일관된 맵" 둘 중 하나만 보게 된다 — 락을 전혀 쓰지 않고도 읽기 스레드가 항상 일관된 스냅샷을 보장받는
"불변 객체 교체"(immutable snapshot swap) 패턴. `EnumMap`을 쓴 이유는 키가 `MarketType`처럼 고정된
enum 집합일 때 일반 `HashMap`보다 메모리 효율적이고 순회가 빠르기 때문(내부적으로 enum의 ordinal을
배열 인덱스로 사용).

### 9.3 자정 리셋 카운터 — "캘린더 하루"를 TTL로 표현하기

`DailyResetClock.secondsUntilNextMidnight()`는 `ZonedDateTime.now(Asia/Seoul)` 기준 다음 자정까지
남은 초를 계산해(`Math.max(..., 1)`로 0초 이하 방지) 반환한다. 단순히 `Duration.ofDays(1)`을 TTL로
주면 "마지막 요청으로부터 24시간"이 되어 사용자마다 리셋 시각이 제각각이 되는데(슬라이딩 윈도우),
OCR 일일 한도나 1원 송금 일일 한도처럼 "오늘 자정에 다 같이 리셋"되어야 하는 한도는 이 동적 TTL과
`incrWithExpireIfNewScript`(키가 처음 생성될 때만 EXPIRE)를 조합해 구현한다 — 하루의 첫 요청이
"오늘 남은 시간"만큼의 TTL을 걸어두면, 그 카운터는 정확히 자정에 사라지고 다음 요청이 새 카운터를
만든다.

### 9.4 Enum 코드 조회 캐시 — 선형 스캔 제거

`BankCode.CODE_INDEX`는 `Arrays.stream(values()).collect(toMap(...))`로 클래스 로딩 시 한 번만
`Map<String, BankCode>`를 만들어 둔다. 주석: "호출마다 enum 전체를 선형 스캔하지 않도록 한 번만
구성해 둔다." `fromCode()`가 매 호출마다 `values()`를 순회하며 `code` 필드를 비교했다면 은행 개수에
비례하는 O(n) 비교가 매 1원송금 요청마다 일어나는데, 정적 맵으로 한 번만 인덱싱해두면 조회가
O(1)로 끝난다 — enum 항목 수가 적어 실제 성능 차이는 미미하지만, "반복 조회되는 고정 집합은 미리
인덱싱한다"는 일반적인 원칙을 보여주는 사례.

---

## 10. DB 설계와 ORM 최적화

### 10.1 `getReferenceById` — 프록시로 불필요한 SELECT 제거

`UserProfileService`와 `IdempotencyService.createProcessing()`은 FK 연결 용도로만 `User`가
필요할 때 `userRepository.findById()`(즉시 전체 로딩, SELECT 발생) 대신
`userRepository.getReferenceById(userId)`(Hibernate 프록시, **지연 로딩 — 실제로 그 엔티티의 필드를
읽기 전까지 SELECT가 나가지 않음**)를 쓴다. 주석: "FK 연결용으로만 쓰이므로 findById(풀 로딩) 대신
존재 확인 + getReferenceById(프록시)로 SELECT 한 번 절약." `Idempotency`나 새로 만드는 엔티티의
`@ManyToOne` 연관관계를 세팅하는 데는 그 User 엔티티의 id 값만 있으면 충분하고(JPA가 INSERT 시
FK 컬럼에 그 id만 쓰면 됨), User의 다른 필드(email, name 등)는 전혀 필요 없으므로, 프록시만으로
충분한 경우 실제 SELECT를 발생시키지 않는다 — 단, 존재하지 않는 id로 프록시를 만들면 그 프록시의
필드를 처음 접근하는 시점에야 `EntityNotFoundException`이 나므로, 존재가 보장된 컨텍스트(예: 이미
인증된 userId)에서만 안전하게 쓸 수 있는 최적화라는 전제가 깔려 있다.

### 10.2 LAZY 로딩 기본값

`IdentityVerification.user`, `Idempotency.user` 등 `@ManyToOne` 연관관계는 전부
`fetch = FetchType.LAZY`로 명시되어 있다(JPA의 `@ManyToOne` 기본값은 EAGER이므로 이건 의도적인
오버라이드). 연관 엔티티를 즉시 함께 로딩하면 그 엔티티가 필요 없는 조회에서도 매번 JOIN이나 추가
SELECT가 발생하는데, LAZY로 두면 실제로 `getUser()`를 호출해 필드에 접근하는 시점에만 로딩된다 —
N+1 문제는 LAZY 자체가 아니라 "여러 건을 조회한 뒤 각각 연관 엔티티에 접근"할 때 발생하므로, 그런
경우엔 별도로 fetch join이나 batch fetching이 필요하지만, 이 코드베이스의 단건 조회 위주 흐름에서는
LAZY 기본값이 불필요한 로딩을 막는 합리적인 기본 선택이다.

### 10.3 DB 유니크 제약 전수 정리

`V1__baseline_schema.sql` ~ `V3__add_codef_external_account_connection.sql`에 정의된 유니크
제약들을 다시 정리하면 — `users.email`, `accounts.account_number`, `currencies.currency_code`,
`exchange_orders.(exchange_quote_id)`/`(transaction_history_id)`(1:1 관계 보장),
`external_account.(user_id, organization, account_number_hash)`,
`external_asset_transactions.(external_account_id, transaction_key)`,
`fx_wallets.(user_id, currency_code)`, `idempotency_keys.(user_id, idempotency_key)`,
`investment_accounts.account_number`, `investment_holdings.(investment_account_id, stock_id)`(종목당
보유 1행), `investment_orders.(investment_account_id, idempotency_key)`,
`market_holidays.(market_type, holiday_date)`, `stocks.stock_code`,
`stock_watchlists.(user_id, stock_id)`, `user_consents.(user_id, terms_type)`,
`user_profiles.user_id`, `young_policy.policy_id`,
`codef_external_account_connection.(user_id, organization)`. 패턴이 분명하다 — **단일 컬럼
유니크는 "전역적으로 하나뿐인 식별자"(이메일, 계좌번호, 종목코드)를, 복합 컬럼 유니크는 "특정
사용자/엔티티 범위 안에서 하나뿐이어야 하는 조합"(유저당 통화별 지갑, 유저당 약관유형별 동의,
계좌당 종목 보유, 계좌당 거래키)을 표현한다.** 이런 제약은 동시성 버그를 미리 막아주는 효과 외에도,
"이 조합은 중복될 수 없다"는 비즈니스 규칙을 코드 리뷰 없이도 DB 스키마만 보고 알 수 있게 해주는
문서화 효과도 있다.

### 10.4 Flyway 기반 스키마 버전 관리

`application.yml`은 `spring.flyway.enabled: true`, `spring.jpa.hibernate.ddl-auto: validate`로
dev/prod 모두 설정되어 있다 — Hibernate가 스키마를 직접 만들거나 고치지 않고(`validate`는 엔티티와
실제 스키마가 일치하는지 검사만 함), 스키마 변경은 오직 `V{n}__설명.sql` 마이그레이션 파일로만
이루어진다. 이 방식이 `ddl-auto: update` 같은 자동 동기화보다 나은 이유는, 마이그레이션 파일이
**스키마 변경의 순서가 보장된 이력**(버전 번호)이 되어 어떤 환경(로컬/CI/운영)에 적용해도 항상
같은 순서로 같은 변경이 적용된다는 점이다 — `update` 모드는 Hibernate가 추론한 DDL을 그때그때
실행하므로 환경마다 실제 적용된 스키마가 미묘하게 달라질 위험이 있고, 운영 DB에 의도치 않은
DDL이 자동으로 나가는 사고도 방지된다. 테스트 환경(`application-test.yml`)만 예외적으로
`flyway.enabled: false` + `ddl-auto: create-drop`(H2 인메모리 DB를 매 테스트 컨텍스트마다 엔티티
기준으로 새로 생성/삭제)을 쓰는데, 이는 테스트 속도와 격리성을 우선한 선택 — 테스트는 매번 깨끗한
스키마에서 시작해야 하고 마이그레이션 이력을 쌓을 필요가 없다.

---

## 11. 예외 처리 아키텍처 (1.7과 함께 보는 전체 그림)

- `GlobalExceptionHandler` — 도메인 비의존. `BusinessException`, Bean Validation 계열
  (`MethodArgumentNotValidException`, `ConstraintViolationException`), 파라미터/헤더 누락,
  타입 불일치, catch-all `Exception`(500, 내부 정보 비노출)을 처리.
- `UserExceptionHandler` — `domain.user` 패키지 스코프, `JwtException`(refresh 흐름 전용 의미)을
  `INVALID_REFRESH_TOKEN`으로 번역.
- 도메인별 `ErrorCode` enum(`UserErrorCode`, `GlobalErrorCode`)이 HTTP 상태 코드와 클라이언트
  메시지를 한곳에 모아 관리 — 예외 처리 코드 안에 상태 코드/메시지 문자열이 흩어지지 않게 함.

이 구조의 의의는 "전역 핸들러가 모든 도메인의 예외 타입을 알아야 한다"는 결합을 피한 것이다.
도메인이 늘어날수록 전역 핸들러에 `if (e instanceof XxxException)`이 쌓이는 대신, 각 도메인이
자신의 advice를 갖는 수평 확장 구조를 취하고 있다.

---

## 12. 알려진 불일치/미해결 사항 (정직하게 기록)

이 문서는 "코드에 들어간 지식"을 정리하는 것이 목적이라, 발견한 불일치도 숨기지 않고 적어둔다 —
고치는 건 별도 작업이고 여기서는 사실만 기록.

1. **`ExchangeRateService`의 옛 콜백 방식** — `TransactionSynchronizationManager.registerSynchronization().afterCommit()`을
   직접 등록하는 구식 패턴이 `OcrSubmittedEvent`/`OneWonTransferRequestedEvent`가 쓰는
   `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` 패턴으로 아직 전환되지
   않았다(6.2).
2. **CODEF RestClient 커넥션 관리 불일치** — `RestClientConfig`/`CodefBankRestClientConfig`는 JDK
   `HttpClient`(풀링/keep-alive 내장)를 쓰는데 `CodefExAccountRestClientConfig`는
   `SimpleClientHttpRequestFactory`(요청마다 새 연결 경향)를 쓴다(8.2).
3. **`AuthUser.java`** (`global/security/AuthUser.java`) — 파일이 거의 빈 상태(1줄)이고, `grep`으로
   전체 메인 소스를 검색해도 참조하는 코드가 없다. `auth_principal_userdetails_review` 메모에 남긴
   "Long userId를 그대로 principal로 유지하고 커스텀 `UserDetails`는 보류"라는 과거 결정과 맞물려
   보면, 그 방향으로 가다 만든 흔적(스텁)으로 보인다 — 실제 인증 principal은
   `JwtAuthenticationFilter`가 `Long userId`를 그대로 `SecurityContext`에 심는 방식이고, 이 파일은
   현재 어디서도 쓰이지 않는다.
4. **Hikari 커넥션 풀 크기 미설정(prod)** — `application-test.yml`에는
   `spring.datasource.hikari.maximum-pool-size: 50`이 명시되어 있지만, `application.yml`/`application-prod.yml`/`application-dev.yml`에는
   해당 설정이 없어 운영에서는 HikariCP 기본값(10)을 그대로 쓰는 것으로 보인다 — 트래픽 규모에 맞는
   값인지는 별도로 검토가 필요한 지점.

---

## 13. 전체 그림 — 이 모든 장치가 풀고 있는 공통 문제

이 문서에서 다룬 장치들을 다시 묶어 보면, 결국 몇 가지 반복되는 CS 주제로 환원된다.

- **원자성(Atomicity)**: Redis Lua 스크립트, DB 트랜잭션, AES-GCM 인증 태그 — "여러 단계가 중간에
  끼어들 여지 없이 한 단위로 끝나야 한다"는 같은 요구를 레이어마다 다른 도구로 만족시킨다.
- **경쟁 상태(Race Condition) 방지**: 더블 체크 락킹, compare-and-delete, DB 유니크 제약 + 재조회,
  분산 락 — "두 행위자가 동시에 같은 자원을 건드릴 때 정확히 하나만 이겨야 한다"는 문제를 애플리케이션
  레벨 락, DB 제약, Redis 원자적 명령 세 가지 도구로 상황에 맞게 풀고 있다.
  
- **멱등성(Idempotency)**: HTTP 멱등성 키 패턴, RT rotation의 1회성 소비, jti 기반 1회성 무효화 —
  "같은 요청/토큰이 여러 번 와도 효과는 한 번만"이라는 성질을 각 영역에서 반복 구현.
- **신뢰 경계에 맞는 암호 프리미티브 선택**: 비밀번호(BCrypt) vs 블라인드 인덱스(HMAC) vs 가역
  암호화(AES-GCM, RSA) — "복호화가 필요한가/동등비교가 필요한가/브루트포스 저항이 필요한가"라는
  질문에 따라 도구를 갈라 쓴다.
- **자원 격리와 타임박싱**: 전용 스레드풀(OCR vs 1원송금), 전용 RestClient(전역 5s vs CODEF 30s),
  전용 Redis 키 네임스페이스(purpose별 CODEF 토큰) — 한 영역의 느림/실패가 다른 영역으로 전파되지
  않도록 자원을 나누고 시간 제한을 둔다.

---

## 부록: 핵심 파일 위치 인덱스

- 인증/토큰: `global/jwt/JwtProvider.java`, `RefreshTokenService.java`, `TokenBlocklistService.java`
- 시큐리티 필터/설정: `global/security/SecurityConfig.java`, `JwtAuthenticationFilter.java`,
  `AuthUser.java`(미사용), `HmacSha256Hasher.java`
- 암호화: `global/crypto/HmacHasher.java`, `HmacConfig.java`,
  `domain/codef/exAccount/crypto/CodefConnectedIdEncryptor.java`, `CodefExAccountPasswordEncryptor.java`
- 로그인/계정 보호: `domain/user/service/LoginAttemptService.java`, `UserService.java`
- 본인인증 3단계: `domain/user/service/IdentityVerificationService.java`,
  `domain/user/verification/OneWonVerificationService.java`, `BankCode.java`, `DailyResetClock.java`,
  `domain/user/ocr/OcrService.java`, `OcrPersistenceService.java`
- CODEF 연동: `domain/codef/auth/client/CodefAuthClient.java`, `CodefBankTransferService.java`,
  `domain/codef/auth/ocr/CodefOcrClient.java`, `domain/codef/auth/config/*`
- 멱등성: `global/idempotency/**`
- Redis 원자성: `global/config/RedisScriptConfig.java`, `RedisScriptInitializer.java`,
  `RedisConfig.java`
- 비동기/스케줄링: `global/config/AsyncConfig.java`, `SchedulingConfig.java`, `RetryConfig.java`
- HTTP 클라이언트: `global/config/RestClientConfig.java`,
  `domain/codef/auth/config/CodefBankRestClientConfig.java`,
  `domain/codef/exAccount/config/CodefExAccountRestClientConfig.java`,
  `domain/codef/auth/config/CodefHttpServiceConfig.java`
- 캐싱: `domain/investment/marketholiday/cache/MarketHolidayCache.java`
- 예외 처리: `global/exception/GlobalExceptionHandler.java`,
  `domain/user/exception/UserExceptionHandler.java`
- DB 마이그레이션: `backend/src/main/resources/db/migration/V1~V3__*.sql`
- 쿠키/세션 전달: `domain/user/controller/AuthController.java`
