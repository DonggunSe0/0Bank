package com.team10.backend.domain.exAccount.service;

import com.team10.backend.domain.exAccount.exception.ExAccountConnectionErrorCode;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExAccountCodefRateLimitService {

    private static final String KEY_PREFIX = "codef:ex-account:rate:";
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int REGISTER_LIMIT = 3;
    private static final int ACCOUNT_LIST_LIMIT = 10;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> incrWithExpireIfNewScript;

    public void checkRegister(Long userId, String organization) {
        check(
                "register",
                userId,
                organization,
                REGISTER_LIMIT,
                ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_REGISTER_RATE_LIMIT_EXCEEDED
        );
    }

    public void checkAccountList(Long userId, String organization) {
        check(
                "account-list",
                userId,
                organization,
                ACCOUNT_LIST_LIMIT,
                ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_ACCOUNT_LIST_RATE_LIMIT_EXCEEDED
        );
    }

    private void check(
            String operation,
            Long userId,
            String organization,
            int limit,
            ExAccountConnectionErrorCode errorCode
    ) {
        Long count;
        try {
            count = redisTemplate.execute(
                    incrWithExpireIfNewScript,
                    List.of(key(operation, userId, organization)),
                    String.valueOf(WINDOW.toSeconds())
            );
        } catch (RuntimeException exception) {
            log.warn("Redis error occurred during rate limiting. Falling open. User: {}, Org: {}", userId, organization, exception);
            return;
        }
        if (count == null || count > limit) {
            throw new BusinessException(errorCode);
        }
    }

    private String key(String operation, Long userId, String organization) {
        return KEY_PREFIX + operation + ":" + userId + ":" + organization;
    }
}

