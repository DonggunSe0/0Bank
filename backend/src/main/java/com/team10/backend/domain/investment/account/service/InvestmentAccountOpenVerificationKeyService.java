package com.team10.backend.domain.investment.account.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * 사용자 인증 키를 Redis로 관리한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentAccountOpenVerificationKeyService {

    private static final String KEY_PREFIX = "investment:account-open:";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final RedisScript<Long> COMPARE_AND_DELETE = RedisScript.of(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then\n" +
                    "  redis.call('DEL', KEYS[1])\n" +
                    "  return 1\n" +
                    "end\n" +
                    "return 0",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public String generateAndStore(Long userId) {
        String verificationKey = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(redisKey(userId), verificationKey, TTL);

        log.info("[투자 계좌 개설] 인증키 발급 - userId={}, ttlSeconds={}", userId, TTL.toSeconds());
        return verificationKey;
    }

    public boolean verifyAndDelete(Long userId, String inputKey) {
        String key = redisKey(userId);
        Long matched = redisTemplate.execute(COMPARE_AND_DELETE, List.of(key), inputKey);

        if (matched == null || matched != 1L) {
            log.warn("[투자 계좌 개설] 인증키 검증 실패 - userId={}", userId);
            return false;
        }

        log.info("[투자 계좌 개설] 인증키 검증 성공 - userId={}", userId);
        return true;
    }

    public long ttlSeconds() {
        return TTL.toSeconds();
    }

    private String redisKey(Long userId) {
        return KEY_PREFIX + userId;
    }
}
