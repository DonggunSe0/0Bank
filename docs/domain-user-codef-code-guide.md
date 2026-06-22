# domain.user / domain.codef 코드 가이드

작성일: 2026-06-18

`backend/src/main/java/com/team10/backend/domain/user`와 `domain/codef` 두 패키지에 속한 모든 메인 소스 파일(총 56개, QueryDSL 생성 `Q*.java`와 테스트 코드는 제외)을 대상으로, 파일마다 어떤 기술/패턴을 사용했고 왜 그렇게 작성했는지 정리한 문서입니다. 패키지 단위로 묶어서 설명합니다.

---

## 1. user 도메인

### 1.1 entity (4개)

**User.java**
회원 엔티티. `@NoArgsConstructor(PROTECTED)`로 JPA용 기본 생성자를 외부에 노출하지 않고, `@Builder`를 `private` 생성자에 걸어 `User.create(...)` 정적 팩토리로만 객체를 만들도록 제한했습니다. `status` 필드는 `@Enumerated(EnumType.STRING)`으로 저장하는데, `ORDINAL`(순서 기반 정수 저장) 대신 문자열로 저장해 enum에 값을 추가/재배열해도 기존 데이터가 깨지지 않게 했습니다. 비밀번호 변경·탈퇴·인증완료는 setter가 아니라 `changePassword()`, `withdraw()`, `completeIdentityVerification()`처럼 의도가 드러나는 도메인 메서드로 캡슐화했습니다(Rich Domain Model).

**UserProfile.java**
`@OneToOne(LAZY)`로 User와 연관되고, `Set<FinancialInterest>`는 `@ElementCollection(EAGER)` + `@CollectionTable`로 별도 테이블(`user_profile_interests`)에 매핑했습니다. 관심사 enum 하나하나를 별도 엔티티로 만들지 않고 값 컬렉션으로 처리해 테이블 수를 줄였습니다. `update()`는 `clear()` 후 `addAll()`로 컬렉션을 통째로 교체하는 방식인데, 이 때문에 테스트에서 불변 `Set.of(...)`를 직접 주입하면 `clear()`에서 `UnsupportedOperationException`이 나는 문제가 있었습니다(실제 운영에서는 JPA가 관리하는 가변 컬렉션이라 문제 없음 — 이번 세션에서 테스트 픽스처만 수정).

**UserConsent.java**
`@ManyToOne(LAZY)` + 복합 unique 제약(`user_id`, `terms_type`)으로 같은 약관에 중복 동의 레코드가 쌓이지 않도록 DB 레벨에서 막았습니다. `agreedAt`은 `LocalDateTime.now(ZoneId.of("Asia/Seoul"))`로 서버의 기본 타임존 설정과 무관하게 항상 한국 시간으로 기록되도록 했습니다.

**IdentityVerification.java**
OCR → 행안부 진위확인 → 1원 송금의 3단계 인증을 `VerificationStatus` enum 기반 상태 머신 하나로 표현합니다. `completeGovernmentVerification()`과 `fail()` 양쪽 모두 내부에서 `maskResidentNumber()`를 호출하도록 만들어, 성공/실패 어느 경로로 가더라도 주민등록번호 뒷자리가 평문으로 남지 않게 엔티티 책임으로 마스킹을 내재화했습니다.

### 1.2 dto.req (8개)

**OneWonVerifyReq, LoginReq, ChangePasswordReq, OneWonStartReq, UserCreateReq, ConsentUpdateReq, UserProfileReq, TokenRefreshReq** — 모두 Java `record` + Jakarta Bean Validation 애너테이션(`@NotBlank`, `@Email`, `@Pattern`, `@Size`, `@AssertTrue`, `@Past` 등) 조합입니다. 검증을 DTO 선언부에 선언적으로 박아 두면 컨트롤러의 `@Valid`만으로 형식 검증이 서비스 진입 전에 끝나, 서비스 코드에는 비즈니스 검증만 남습니다.

특히 눈에 띄는 것들:
- `ChangePasswordReq`/`UserCreateReq`의 비밀번호 `@Pattern`은 "영문+숫자 각 1자 이상, 8자 이상"이라는 정책을 정규식으로 강제합니다.
- `UserCreateReq`의 서비스 이용약관·개인정보·금융정보 동의 3종은 `@AssertTrue`로 선언되어 있어, 값이 `false`이거나 `null`이면 그 자체로 검증 실패가 되어 필수 동의 없이는 회원가입 요청이 통과하지 못합니다.
- `TokenRefreshReq`는 `accessToken`만 받고 `refreshToken`은 일부러 필드로 두지 않았습니다. RT는 HttpOnly 쿠키로만 전달하기로 설계했기 때문입니다.

### 1.3 dto.res (8개)

**OcrAcceptedRes, OneWonStartRes, OneWonVerifyRes** — 인증 세션 ID·현재 상태·안내 메시지 3종 필드의 반복되는 형태로, 비동기/단계형 API의 "접수했다"는 응답을 표현합니다.

**UserRes** — 비밀번호 등 민감 필드를 제외하고 화면에 노출해도 되는 필드만 선별한 응답 DTO입니다.

**ConsentRes, UserProfileRes** — `from(entity)` 정적 팩토리 메서드를 둬서 엔티티→DTO 변환 로직을 DTO 자신이 갖도록 했습니다. 서비스 코드에서는 `XxxRes.from(entity)` 한 줄로 끝나 변환 로직이 여기저기 흩어지지 않습니다.

**LoginRes, TokenRefreshRes** — `record`의 컴팩트 생성자/필드는 유지하면서, `refreshToken()` 접근자만 `@Override` + `@JsonIgnore`로 재정의했습니다. 덕분에 객체 내부적으로는 `refreshToken` 값을 그대로 갖고 있지만, Jackson이 JSON으로 직렬화할 때는 이 필드가 응답 바디에서 빠집니다. RT는 오직 HttpOnly 쿠키로만 내려가게 해서, XSS로 JS가 응답 바디를 읽어도 RT를 탈취할 수 없게 만든 설계입니다.

### 1.4 type (6개 enum)

**TermsType, AgeGroup, OccupationStatus, FinancialInterest, UserStatus, VerificationStatus** — 전부 순수 enum입니다. 매직스트링/매직넘버 대신 타입-세이프한 분류값으로 도메인 개념(약관 종류, 연령대, 직업상태, 관심분야, 계정상태, 인증단계)을 표현하고, 엔티티에는 `@Enumerated(STRING)`으로 저장해 DB만 봐도 값을 바로 읽을 수 있게 했습니다.

### 1.5 repository (4개)

**UserRepository, UserProfileRepository, UserConsentRepository, IdentityVerificationRepository** — 모두 Spring Data JPA `JpaRepository` 인터페이스입니다. `existsByEmail`, `findByUserId`, `findTopByUserIdOrderByCreatedAtDesc`처럼 메서드 이름만으로 쿼리를 자동 생성하는 "쿼리 메서드" 기능을 적극 활용해 별도 JPQL/SQL 없이 의도가 드러나는 조회 메서드를 만들었습니다. 특히 `findTopBy...OrderByCreatedAtDesc`는 "해당 사용자의 가장 최근 인증 세션"처럼 다단계 인증에서 자주 필요한 조회를 한 줄로 표현합니다.

### 1.6 service (5개)

**UserService** — 회원가입/로그인/탈퇴/토큰 재발급/비밀번호 변경/로그아웃을 담당합니다. 회원가입에서는 PortOne 본인인증 조회(외부 API)를 `@Transactional(propagation = NOT_SUPPORTED)`로 트랜잭션 밖에서 먼저 호출하고, DB 쓰기만 `TransactionTemplate`으로 별도 트랜잭션으로 묶었습니다. 외부 API 응답을 기다리는 동안 DB 커넥션을 점유하지 않기 위한 설계입니다. `existsByEmail` 체크 후 저장 시 `DataIntegrityViolationException`을 잡아 `DUPLICATE_EMAIL`로 변환하는 부분은, 두 요청이 동시에 들어와 체크를 모두 통과한 뒤 DB unique 제약에서 한쪽이 걸리는 race condition까지 고려한 것입니다. 로그인은 "비밀번호 검증 → 계정 상태 체크" 순서를 지켜서, 탈퇴/휴면 계정인지 여부가 비밀번호 검증 전에 노출되지 않게 했습니다.

**UserProfileService** — 생성 시 FK 연결 대상인 `User`를 `findById`(풀 로딩) 대신 `existsById` + `getReferenceById`(프록시) 조합으로 처리해, 실제로는 쓰지 않을 User 전체 컬럼을 SELECT하지 않도록 했습니다(이전 세션 성능 개선 #11).

**UserConsentService** — 회원가입 시 4종 약관 동의를 `saveAll`로 한 번에 저장하고, 마케팅 수신 동의만 선택적으로 변경 가능하게 분리했습니다.

**LoginAttemptService** — Redis Lua 스크립트(`INCR` + `EXPIRE`를 원자적으로 묶은 스크립트)로 로그인 실패 횟수를 관리합니다. INCR과 EXPIRE를 애플리케이션에서 따로 호출하면 그 사이 서버가 죽었을 때 TTL이 설정되지 않을 위험이 있는데, Lua 스크립트로 묶어 원자성을 보장합니다. 5회 실패 시 30분 잠금.

**IdentityVerificationService** — 3단계 본인인증의 진입점입니다. 업로드된 이미지에 대해 Content-Type 헤더 검증과 매직바이트(파일 시그니처) 검증을 이중으로 수행하고(보안 강화 항목, `docs/domain-user-security-summary.md` 8.2절 참조), Redis 기반 일일 OCR 요청 한도(5회/일)를 체크합니다. 1원 송금 시작은 동시 요청으로 같은 사용자에게 중복 송금이 나가지 않도록 Redis `SET NX` 기반 분산락을 사용하고, 외부 송금 API 호출은 트랜잭션 밖에서, DB 갱신은 `TransactionTemplate`으로 분리했습니다.

### 1.7 client (2개)

**PortOneClient** — 포트원(PortOne) V2 본인인증 조회 API를 호출하는 클라이언트입니다. `RestClient`로 `Authorization: PortOne {API_SECRET}` 헤더를 구성해 GET 요청을 보내고, `RestClientException`을 `BusinessException(IDENTITY_VERIFICATION_FAILED)`로 변환해 도메인 예외 체계에 맞춥니다.

**PortOneIdentityVerification** — 포트원 응답을 매핑하는 record입니다. `@JsonIgnoreProperties(ignoreUnknown = true)`를 둬서 외부 API가 새 필드를 추가해도 역직렬화가 깨지지 않게 했습니다.

### 1.8 verification (8개)

**GovernmentVerifyResult** — 행안부(또는 Mock) 진위확인 결과를 표현하는 enum(`VERIFIED`/`ISSUE_DATE_MISMATCH`/`IDENTITY_NOT_FOUND`)입니다. 타임아웃은 결과값이 아니라 별도 예외로 표현해 "정상적으로 끝난 결과"와 "통신 실패"를 명확히 구분했습니다.

**GovernmentVerifyTimeoutException** — `RuntimeException`을 상속해 `@Transactional` 경계에서 자동 롤백되도록 했습니다. OCR 1단계 결과가 이미 커밋된 뒤 행안부 타임아웃이 나도, FAILED 기록은 `VerificationSessionRecorder`가 별도 트랜잭션으로 안전하게 남깁니다.

**VerificationSessionRecorder** — `@Transactional(propagation = REQUIRES_NEW)`로 "실패 상태 기록"만을 독립된 새 트랜잭션으로 커밋하는 헬퍼입니다. 메인 트랜잭션이 이미 끝났거나 롤백 대상이어도 실패 기록만큼은 별도로 살아남게 하려는 목적입니다.

**MockGovernmentVerifyService** — 행안부 실제 연동 전 사용하는 개발용 Mock입니다. 특정 주민등록번호 패턴으로 발급일자 불일치/존재하지 않는 명의/타임아웃 시나리오를 트리거할 수 있게 만들어, 실제 외부 기관 없이도 각 분기를 테스트할 수 있습니다.

**BankTransferService** — 1원 송금 기능을 추상화한 인터페이스입니다. `MockBankTransferService`(개발용)와 `CodefBankTransferService`(`@Primary`, 실제 CODEF 연동) 두 구현체가 있어, 운영 전환 시 설정 변경 없이 `@Primary` 구현체가 자동으로 선택되는 전략 패턴 구조입니다.

**MockBankTransferService** — 실제 은행 API 없이 로그만 남기는 Mock 구현.

**OneWonVerificationService** — Redis로 1원 인증코드 발급(`SecureRandom` 4자리), 시도 횟수 제한(5회 잠금), 일일 한도(10회), 동시 시작 요청 방지 락을 모두 관리합니다. INCR+EXPIRE 패턴을 두 가지 변형(매번 TTL 갱신 vs 최초 1회만 TTL 설정)으로 나눠 "시도 횟수"와 "일일 카운터"의 서로 다른 만료 정책을 구현했습니다.

**BankCode** — CODEF 기관코드, 표시명, 점검시간을 가진 enum입니다. `fromCode()` 조회를 위해 `Arrays.stream(values())`로 만든 `Map<String, BankCode>`를 static 필드로 캐싱해, 매 호출마다 enum 전체를 선형 스캔하지 않도록 했습니다(이전 세션 성능 개선 #12). `isMaintenance()`는 자정을 넘는 점검 구간(23:30~00:30)도 올바르게 판정합니다.

### 1.9 ocr (2개)

**OcrService** — `@Async("ocrExecutor")`로 별도 스레드풀에서 OCR 처리를 비동기 실행합니다. OCR 성공 시 바로 행안부 검증으로 체이닝해서, 컨트롤러가 202 Accepted로 즉시 응답한 뒤 백그라운드에서 1~2단계를 순서대로 처리합니다.

**OcrPersistenceService** — DB 저장만 전담하는 별도 서비스로 분리되어 있습니다. `OcrService` 안에 `@Transactional` 메서드를 같은 클래스에서 self-invocation(자기 자신 호출)하면 Spring AOP 프록시가 트랜잭션을 가로채지 못하는 한계가 있어, 이를 피하려고 저장 로직만 별도 빈으로 뽑아낸 구조입니다.

### 1.10 controller (2개)

**AuthController** — 회원가입/로그인/토큰갱신/로그아웃 API. `ResponseCookie`로 RT를 `httpOnly(true)`, `sameSite("Strict")`, `secure` 옵션을 적용해 발급하고, 로그아웃 시 `maxAge(0)`으로 즉시 만료시키는 쿠키를 내려보냅니다. XSS(HttpOnly)와 CSRF(SameSite=Strict) 양쪽을 의식한 설계입니다.

**UserController** — 내 정보/비밀번호/탈퇴/약관동의/프로필/본인인증 API. OCR 업로드 엔드포인트는 `consumes = MULTIPART_FORM_DATA_VALUE`로 멀티파트 파일을 받고, 모든 "내 정보" 관련 엔드포인트는 `@AuthenticationPrincipal Long userId`로 인증 필터가 채워준 사용자 ID를 그대로 받아 별도 인증 로직 없이 사용합니다.

### 1.11 exception (1개)

**UserErrorCode** — 공통 `ErrorCode` 인터페이스를 구현하는 enum으로, 각 에러 케이스마다 `HttpStatus`와 사용자 노출 메시지를 한 쌍으로 정의합니다. 예외 발생 지점과 HTTP 응답 코드/메시지를 분리해, 서비스 코드는 `throw new BusinessException(UserErrorCode.XXX)`만 하면 전역 예외 핸들러가 나머지를 처리하는 구조를 따릅니다.

---

## 2. codef 도메인

CODEF(금융 데이터 거래소) Open API 연동을 담당하는 패키지입니다.

### 2.1 client (3개)

**CodefAuthClient** — CODEF OAuth 액세스 토큰을 발급받고 캐싱합니다. `AtomicReference<TokenCache>` + `synchronized` 블록의 Double-Checked Locking으로 토큰 캐시를 동시성 안전하게 관리합니다(이전 세션 성능 개선 #10 — 매 요청마다 OAuth 서버를 호출하지 않고, 만료 5분 전까지는 캐시된 토큰을 재사용하며, 여러 스레드가 동시에 만료를 감지해도 OAuth 호출은 한 번만 일어나도록 보호).

**CodefAuthException** — 의도적으로 `BusinessException`이 아닌 평범한 `RuntimeException`으로 만들었습니다. 호출부(`CodefOcrClient`, `CodefBankTransferService`)가 `catch (BusinessException e) { throw e; }`로 비즈니스 예외는 그대로 던지고 나머지는 자신의 도메인 에러코드로 변환하는 구조이기 때문에, 이 예외가 `BusinessException`이면 의도한 변환 로직을 타지 않고 그대로 새어나가 버립니다.

**CodefBankTransferService** — `BankTransferService` 인터페이스의 실제 구현체로 `@Primary`가 붙어 있어 `MockBankTransferService` 대신 기본으로 주입됩니다. CODEF의 1원 계좌인증 API를 호출하는데, EasyCodef 공식 SDK가 URL 인코딩과 Content-Type 처리에서 `CF-00003` 오류를 일으키는 문제가 있어 SDK를 우회하고 Spring `RestClient`로 `application/json` 요청을 직접 구성했습니다. 응답이 URL 인코딩되어 오기 때문에 `URLDecoder.decode` 후 JSON 파싱합니다.

### 2.2 ocr (2개)

**CodefOcrClient** — CODEF 신분증 OCR API를 호출합니다. 이미지를 Base64로 인코딩해 전송하고, 응답에서 이름·주민등록번호·발급일자를 추출해 하이픈을 넣는 등 정규화합니다. `CodefBankTransferService`와 마찬가지로 EasyCodef SDK 대신 `RestClient` 직접 호출 방식을 씁니다. 필수 필드가 비어 있거나 길이가 부족하면 `OCR_FAILED`로 처리하고, 로그에는 주민등록번호를 앞 6자리만 남기고 마스킹해서 남깁니다.

**IdCardOcrResult** — OCR 결과(이름/주민등록번호/발급일자)를 담는 단순 record입니다.

### 2.3 config (1개)

**CodefBankRestClientConfig** — 1원 송금 전용 `RestClient` 빈을 별도로 정의합니다. CODEF가 실제 은행에 동기로 요청을 보내고 응답을 기다리는 구조라 전역 `RestClientConfig`의 5초 read timeout으로는 부족해서, 이 엔드포인트에만 30초 read timeout을 적용한 별도 빈으로 분리했습니다. `JdkClientHttpRequestFactory` + `HttpClient`를 사용해 커넥션 풀링/keep-alive를 활용하므로(이전 세션 성능 개선 #9), 호출마다 새 TCP/TLS 핸드셰이크를 맺지 않습니다.

---

## 3. 요약

| 영역 | 핵심 기술/패턴 |
|---|---|
| 엔티티 | JPA, Rich Domain Model(도메인 메서드로 상태 변경 캡슐화), Enumerated(STRING), 정적 팩토리 |
| DTO | record, Bean Validation, `@JsonIgnore`로 RT 응답 바디 노출 차단 |
| 트랜잭션 | `@Transactional(NOT_SUPPORTED)` + `TransactionTemplate`(외부 API와 DB 쓰기 분리), `REQUIRES_NEW`(독립 실패 기록) |
| 동시성 제어 | Redis Lua 스크립트(INCR+EXPIRE 원자 실행), Redis `SET NX` 분산락, `AtomicReference`+`synchronized`(토큰 캐시) |
| 외부 연동 | Spring `RestClient`(EasyCodef SDK 우회), 전용 타임아웃 Bean 분리, OAuth 토큰 캐싱 |
| 보안 | Content-Type + 매직바이트 이중 검증, PII 마스킹을 엔티티 책임으로 내재화, HttpOnly/SameSite=Strict 쿠키, 로그인 실패 잠금 |
| 비동기 처리 | `@Async`(OCR), self-invocation 회피를 위한 서비스 분리 |
