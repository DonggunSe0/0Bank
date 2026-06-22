package com.team10.backend.domain.codef.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CODEF OAuth Access Token 발급/캐싱 (OCR, 1원 송금 등 CODEF API 호출에서 공용으로 사용).
 *
 * <h2>분산서버 대응 — 2단계 캐시 + 분산 락</h2>
 * <p>인스턴스 로컬 {@link AtomicReference}만 쓰면 인스턴스마다 캐시가 따로 생겨, 인스턴스 수만큼
 * 중복으로 토큰을 발급받게 된다. 이를 막기 위해 Redis를 인스턴스 간 공유 캐시(L2)로 두고,
 * 캐시가 비어 있을 때만 Redis 분산 락(SET NX)을 잡아 실제 발급 API 호출을 1번으로 합친다.
 * <ol>
 *   <li>L1 — 인스턴스 로컬 {@code AtomicReference}: 대부분의 호출은 여기서 끝나 Redis도 거치지 않는다.</li>
 *   <li>L2 — Redis 공유 캐시: 다른 인스턴스가 이미 받아온 토큰이 있으면 그대로 재사용한다.</li>
 *   <li>L1, L2 모두 미스일 때만 분산 락을 잡고 CODEF OAuth API를 호출한다.
 *       락을 못 잡은 인스턴스는 잠시 대기 후 Redis를 재확인하고, 끝까지 못 받으면
 *       (락 보유 인스턴스 지연/실패 대비) 가용성을 위해 직접 발급을 시도한다.</li>
 * </ol>
 *
 * <h2>락 해제 안전성</h2>
 * <p>락 값에 매 시도마다 새로 생성한 UUID를 넣고, 해제 시 {@code getAndDeleteIfMatchScript}로
 * "내가 쓴 값일 때만" 원자적으로 지운다. 단순히 키를 무조건 DEL하면, 발급 호출이
 * {@link #LOCK_TTL}보다 길어져 락이 자동 만료된 사이 다른 인스턴스가 새로 잡은 락을
 * 실수로 지워버릴 수 있다 — 그 경우 세 번째 인스턴스까지 동시에 락을 잡게 된다.
 */
@Slf4j
@Component
public class CodefAuthClient {

    private static final String REDIS_KEY = "codef:oauth:token";
    private static final String LOCK_KEY = "codef:oauth:token:lock";
    private static final Duration EXPIRY_BUFFER = Duration.ofMinutes(5);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final Duration LOCK_WAIT_INTERVAL = Duration.ofMillis(100);
    private static final int LOCK_WAIT_RETRIES = 10; // 최대 1초 대기

    private final String clientId;
    private final String clientSecret;
    // 실제 OAuth POST 호출은 선언적 HTTP 인터페이스(CodefOAuthExchange)로 위임 — 이 클래스는
    // 토큰 캐싱/락 로직만 담당한다 (CodefHttpServiceConfig에서 빈으로 등록).
    private final CodefOAuthExchange codefOAuthExchange;
    private final StringRedisTemplate redisTemplate;
    // RedisScriptConfig에서 정의 — 값 일치 시에만 원자적으로 삭제(1=일치 후 삭제, 0=불일치/키 없음).
    // 락 해제에 사용해 TTL 만료로 넘어간 다른 인스턴스의 락을 잘못 지우지 않게 한다.
    private final RedisScript<Long> getAndDeleteIfMatchScript;

    // AT 캐시 — 만료 5분 전 갱신, token+만료시간 원자적 관리
    private record TokenCache(String token, long expiryEpoch) {}
    private final AtomicReference<TokenCache> tokenCache = new AtomicReference<>();
    private final Object tokenLock = new Object();

    public CodefAuthClient(
            @Value("${codef.client-id}") String clientId,
            @Value("${codef.client-secret}") String clientSecret,
            CodefOAuthExchange codefOAuthExchange,
            StringRedisTemplate redisTemplate,
            RedisScript<Long> getAndDeleteIfMatchScript
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.codefOAuthExchange = codefOAuthExchange;
        this.redisTemplate = redisTemplate;
        this.getAndDeleteIfMatchScript = getAndDeleteIfMatchScript;
    }

    /** L1(로컬) → L2(Redis) → 분산 락+발급 순으로 확인해 유효한 토큰을 반환한다 */
    public String getAccessToken() {
        TokenCache cache = tokenCache.get();
        if (isValid(cache)) {
            return cache.token();
        }

        // Double-Checked Locking — 동시 만료 시 같은 인스턴스의 여러 스레드가
        // 중복으로 Redis/CODEF API를 호출하는 것을 방지
        synchronized (tokenLock) {
            cache = tokenCache.get();
            if (isValid(cache)) {
                return cache.token();
            }

            String shared = readSharedToken();
            if (shared != null) {
                log.info("[CODEF] 토큰 재사용 (Redis 공유 캐시 히트)");
                return shared;
            }

            return fetchWithDistributedLock();
        }
    }

    /** Redis에 다른 인스턴스가 올려둔 유효한 토큰이 있으면 로컬에도 채워두고 반환한다 */
    private String readSharedToken() {
        String token = redisTemplate.opsForValue().get(REDIS_KEY);
        if (token == null) {
            return null;
        }
        Long remainingTtl = redisTemplate.getExpire(REDIS_KEY);
        if (remainingTtl == null || remainingTtl <= 0) {
            return null;
        }
        tokenCache.set(new TokenCache(token, Instant.now().getEpochSecond() + remainingTtl));
        return token;
    }

    /**
     * 분산 락을 잡고 CODEF OAuth API를 호출한다. 락을 못 잡으면 다른 인스턴스가 발급 중인 것으로
     * 보고 Redis에 새 토큰이 올라오는지 잠깐 기다린다. 끝까지 안 올라오면(락 보유 인스턴스 지연/실패)
     * 가용성을 위해 직접 발급한다 — 이 경우 드물게 중복 발급이 발생할 수 있다.
     */
    private String fetchWithDistributedLock() {
        String lockValue = UUID.randomUUID().toString();
        boolean lockAcquired = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, lockValue, LOCK_TTL));

        if (lockAcquired) {
            try {
                return fetchAndCacheToken();
            } finally {
                releaseLock(lockValue);
            }
        }

        log.info("[CODEF] 다른 인스턴스가 토큰 발급 중 — 대기 후 재확인");
        for (int i = 0; i < LOCK_WAIT_RETRIES; i++) {
            sleep(LOCK_WAIT_INTERVAL);
            String shared = readSharedToken();
            if (shared != null) {
                log.info("[CODEF] 토큰 재사용 (대기 중 다른 인스턴스 발급 완료)");
                return shared;
            }
        }

        log.warn("[CODEF] 분산 락 대기 시간 초과 — 직접 토큰 발급 시도");
        return fetchAndCacheToken();
    }

    /**
     * 내가 setIfAbsent로 써넣은 값과 Redis에 남아있는 값이 같을 때만 원자적으로 락을 지운다.
     * TTL 만료로 락이 이미 다른 인스턴스 소유로 넘어간 경우엔 아무것도 지우지 않는다.
     */
    private void releaseLock(String lockValue) {
        redisTemplate.execute(getAndDeleteIfMatchScript, List.of(LOCK_KEY), lockValue);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CodefAuthException("토큰 발급 대기 중 인터럽트 발생", e);
        }
    }

    private boolean isValid(TokenCache cache) {
        return cache != null && Instant.now().getEpochSecond() < cache.expiryEpoch();
    }

    private String fetchAndCacheToken() {
        try {
            String credentials = Base64.getEncoder().encodeToString(
                    (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

            CodefOAuthExchange.CodefTokenResponse response = codefOAuthExchange.issueToken(
                    "Basic " + credentials,
                    "grant_type=client_credentials&scope=read"
            );

            if (response == null || response.accessToken() == null || response.expiresIn() == null) {
                throw new IllegalStateException("access_token 또는 expires_in 누락");
            }

            long cacheableSeconds = response.expiresIn() - EXPIRY_BUFFER.toSeconds();
            tokenCache.set(new TokenCache(response.accessToken(), Instant.now().getEpochSecond() + cacheableSeconds));

            // 버퍼를 빼고도 양수로 남는 만큼만 Redis에 공유 — 너무 짧으면 다른 인스턴스와
            // 공유할 가치가 없으니 로컬 캐시에만 두고 다음 호출에서 바로 재발급되게 둔다.
            if (cacheableSeconds > 0) {
                redisTemplate.opsForValue().set(REDIS_KEY, response.accessToken(), Duration.ofSeconds(cacheableSeconds));
            }

            log.info("[CODEF] 토큰 발급 완료");
            return response.accessToken();
        } catch (Exception e) {
            // 호출부에서 각자의 도메인 예외로 변환하도록 그대로 전파 (BusinessException이면 호출부에서 가로채지 못함)
            throw new CodefAuthException("CODEF 토큰 응답 파싱 실패", e);
        }
    }
}
