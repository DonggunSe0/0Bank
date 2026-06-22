package com.team10.backend.domain.codef.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CODEF OAuth Access Token 발급/캐싱. purpose별로 인스턴스를 분리해 Redis 캐시/락 키 네임스페이스를 나눈다.
 * L1(로컬 {@link AtomicReference}) → L2(Redis 공유 캐시) → 분산 락+발급 순으로 확인해 인스턴스 간 중복 발급을 막는다.
 * 락 해제는 내가 쓴 UUID 값과 일치할 때만 원자적으로 삭제해, TTL 만료 후 다른 인스턴스가 새로 잡은 락을 잘못 지우지 않게 한다.
 */
@Slf4j
public class CodefAuthClient {

    private static final Duration EXPIRY_BUFFER = Duration.ofMinutes(5);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final Duration LOCK_WAIT_BASE_INTERVAL = Duration.ofMillis(50);
    private static final Duration LOCK_WAIT_MAX_INTERVAL = Duration.ofMillis(250);
    private static final int LOCK_WAIT_RETRIES = 6; // 지수 백오프 + jitter로 최대 약 1.1초 대기

    // purpose로 네임스페이스를 나눠 용도별 인스턴스가 같은 Redis 키를 두고 충돌하지 않게 한다.
    private final String redisKey;
    private final String lockKey;

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
            String purpose,
            String clientId,
            String clientSecret,
            CodefOAuthExchange codefOAuthExchange,
            StringRedisTemplate redisTemplate,
            RedisScript<Long> getAndDeleteIfMatchScript
    ) {
        this.redisKey = "codef:oauth:token:" + purpose;
        this.lockKey = "codef:oauth:token:lock:" + purpose;
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
        String token = redisTemplate.opsForValue().get(redisKey);
        if (token == null) {
            return null;
        }
        Long remainingTtl = redisTemplate.getExpire(redisKey);
        if (remainingTtl == null || remainingTtl <= 0) {
            return null;
        }
        tokenCache.set(new TokenCache(token, Instant.now().getEpochSecond() + remainingTtl));
        return token;
    }

    /** 분산 락을 잡고 토큰을 발급한다. 락을 못 잡으면 잠깐 대기 후 재확인, 끝까지 안 되면 직접 발급한다. */
    private String fetchWithDistributedLock() {
        String lockValue = UUID.randomUUID().toString();
        boolean lockAcquired = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TTL));

        if (lockAcquired) {
            try {
                return fetchAndCacheToken();
            } finally {
                releaseLock(lockValue);
            }
        }

        log.info("[CODEF] 다른 인스턴스가 토큰 발급 중 — 대기 후 재확인");
        for (int attempt = 0; attempt < LOCK_WAIT_RETRIES; attempt++) {
            sleep(nextBackoff(attempt));
            String shared = readSharedToken();
            if (shared != null) {
                log.info("[CODEF] 토큰 재사용 (대기 중 다른 인스턴스 발급 완료)");
                return shared;
            }
        }

        log.warn("[CODEF] 분산 락 대기 시간 초과 — 직접 토큰 발급 시도");
        return fetchAndCacheToken();
    }

    /** 내가 쓴 락 값과 일치할 때만 원자적으로 삭제 — TTL 만료로 다른 인스턴스 소유가 된 락은 지우지 않는다. */
    private void releaseLock(String lockValue) {
        redisTemplate.execute(getAndDeleteIfMatchScript, List.of(lockKey), lockValue);
    }

    /** 락 재확인 대기 시간을 지수 백오프+equal jitter로 계산 — 여러 인스턴스의 폴링이 한 박자로 몰리는 thundering herd를 방지한다. */
    private Duration nextBackoff(int attempt) {
        long capMillis = LOCK_WAIT_MAX_INTERVAL.toMillis();
        long baseMillis = LOCK_WAIT_BASE_INTERVAL.toMillis();
        long exp = Math.min(capMillis, baseMillis << attempt);
        long half = exp / 2;
        long jittered = half + ThreadLocalRandom.current().nextLong(half + 1);
        return Duration.ofMillis(jittered);
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
                redisTemplate.opsForValue().set(redisKey, response.accessToken(), Duration.ofSeconds(cacheableSeconds));
            }

            log.info("[CODEF] 토큰 발급 완료");
            return response.accessToken();
        } catch (Exception e) {
            // 호출부에서 각자의 도메인 예외로 변환하도록 그대로 전파 (BusinessException이면 호출부에서 가로채지 못함)
            throw new CodefAuthException("CODEF 토큰 응답 파싱 실패", e);
        }
    }
}
