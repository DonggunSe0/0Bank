package com.team10.backend.domain.codef.exAccount.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.team10.backend.domain.codef.exAccount.config.CodefExAccountProperties;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountAuthException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.team10.backend.domain.codef.exAccount.config.CodefExAccountRestClientConfig.OAUTH_REST_CLIENT;

@Component
public class CodefExAccountAuthClient {

    private static final String OAUTH_TOKEN_URL = "https://oauth.codef.io/oauth/token";
    private static final long TOKEN_REFRESH_SKEW_SECONDS = 300;
    private static final String REDIS_TOKEN_KEY = "codef:oauth:token:account-inquiry";
    private static final String REDIS_LOCK_KEY = "codef:oauth:token:lock:account-inquiry";
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final Duration LOCK_WAIT_INTERVAL = Duration.ofMillis(100);
    private static final int LOCK_WAIT_RETRIES = 10;

    private final CodefExAccountProperties properties;
    private final RestClient restClient;
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> getAndDeleteIfMatchScript;
    private final AtomicReference<TokenCache> tokenCache = new AtomicReference<>();
    private final Object tokenLock = new Object();

    public CodefExAccountAuthClient(
            CodefExAccountProperties properties,
            @Qualifier(OAUTH_REST_CLIENT) RestClient restClient,
            StringRedisTemplate redisTemplate,
            @Qualifier("getAndDeleteIfMatchScript") RedisScript<Long> getAndDeleteIfMatchScript
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.redisTemplate = redisTemplate;
        this.getAndDeleteIfMatchScript = getAndDeleteIfMatchScript;
    }

    public String getAccessToken() {
        TokenCache cache = tokenCache.get();
        if (isValid(cache)) {
            return cache.accessToken();
        }

        synchronized (tokenLock) {
            cache = tokenCache.get();
            if (isValid(cache)) {
                return cache.accessToken();
            }
            String sharedToken = readSharedToken();
            if (sharedToken != null) {
                return sharedToken;
            }
            return issueAndCacheAccessTokenWithDistributedLock();
        }
    }

    private boolean isValid(TokenCache cache) {
        return cache != null
                && Instant.now().getEpochSecond() < cache.validUntilEpochSecond();
    }

    private String readSharedToken() {
        try {
            String value = redisTemplate.opsForValue().get(REDIS_TOKEN_KEY);
            if (value == null || value.isBlank()) {
                return null;
            }

            int lastColonIndex = value.lastIndexOf(':');
            if (lastColonIndex == -1) {
                return null;
            }

            String token = value.substring(0, lastColonIndex);
            String validUntilStr = value.substring(lastColonIndex + 1);
            long validUntilEpochSecond = Long.parseLong(validUntilStr);

            if (Instant.now().getEpochSecond() >= validUntilEpochSecond) {
                return null;
            }

            tokenCache.set(new TokenCache(
                    token,
                    validUntilEpochSecond
            ));
            return token;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String issueAndCacheAccessTokenWithDistributedLock() {
        String lockValue = UUID.randomUUID().toString();
        boolean lockAcquired = tryAcquireLock(lockValue);

        if (lockAcquired) {
            try {
                return issueAndCacheAccessToken();
            } finally {
                releaseLock(lockValue);
            }
        }

        for (int i = 0; i < LOCK_WAIT_RETRIES; i++) {
            sleep(LOCK_WAIT_INTERVAL);
            String sharedToken = readSharedToken();
            if (sharedToken != null) {
                return sharedToken;
            }
        }

        return issueAndCacheAccessToken();
    }

    private boolean tryAcquireLock(String lockValue) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.opsForValue().setIfAbsent(REDIS_LOCK_KEY, lockValue, LOCK_TTL)
            );
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private void releaseLock(String lockValue) {
        try {
            redisTemplate.execute(getAndDeleteIfMatchScript, List.of(REDIS_LOCK_KEY), lockValue);
        } catch (RuntimeException ignored) {
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CodefExAccountAuthException("CODEF 외부계좌 OAuth 토큰 발급 대기 중 인터럽트가 발생했습니다.", exception);
        }
    }

    private String issueAndCacheAccessToken() {
        try {
            OAuthTokenRes response = restClient.post()
                    .uri(OAUTH_TOKEN_URL)
                    .header(HttpHeaders.AUTHORIZATION, createBasicAuthorization())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials&scope=read")
                    .retrieve()
                    .body(OAuthTokenRes.class);

            validateResponse(response);

            long cacheableSeconds = response.expiresIn() - TOKEN_REFRESH_SKEW_SECONDS;
            tokenCache.set(new TokenCache(
                    response.accessToken(),
                    Instant.now().getEpochSecond() + cacheableSeconds
            ));
            writeSharedToken(response.accessToken(), cacheableSeconds);
            return response.accessToken();
        } catch (CodefExAccountAuthException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new CodefExAccountAuthException("CODEF 외부계좌 OAuth 요청에 실패했습니다.", exception);
        }
    }

    private void writeSharedToken(String accessToken, long cacheableSeconds) {
        try {
            long validUntilEpochSecond = Instant.now().getEpochSecond() + cacheableSeconds;
            String value = accessToken + ":" + validUntilEpochSecond;
            redisTemplate.opsForValue().set(
                    REDIS_TOKEN_KEY,
                    value,
                    Duration.ofSeconds(cacheableSeconds)
            );
        } catch (RuntimeException ignored) {
        }
    }

    private String createBasicAuthorization() {
        String credentials = properties.clientId() + ":" + properties.clientSecret();
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private void validateResponse(OAuthTokenRes response) {
        if (response == null
                || response.accessToken() == null
                || response.accessToken().isBlank()
                || response.expiresIn() == null
                || response.expiresIn() <= TOKEN_REFRESH_SKEW_SECONDS) {
            throw new CodefExAccountAuthException("CODEF 외부계좌 OAuth 응답이 올바르지 않습니다.");
        }
    }

    private record OAuthTokenRes(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn
    ) {
    }

    private record TokenCache(String accessToken, long validUntilEpochSecond) {
    }
}
