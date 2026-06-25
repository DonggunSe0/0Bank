package com.team10.backend.global.lock;

import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class DistributedLockTemplate {

    private final StringRedisTemplate redisTemplate;
    @Qualifier("getAndDeleteIfMatchScript")
    private final RedisScript<Long> getAndDeleteIfMatchScript;

    public <T> T executeWithLock(
            String key,
            Duration waitTime,
            Duration leaseTime,
            ErrorCode errorCode,
            Supplier<T> task
    ) {
        String lockValue = UUID.randomUUID().toString();
        long waitLimit = System.currentTimeMillis() + waitTime.toMillis();

        while (System.currentTimeMillis() < waitLimit) {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, lockValue, leaseTime);
            if (Boolean.TRUE.equals(acquired)) {
                try {
                    return task.get();
                } finally {
                    releaseLock(key, lockValue);
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(errorCode);
            }
        }
        throw new BusinessException(errorCode);
    }

    private void releaseLock(String key, String value) {
        try {
            redisTemplate.execute(getAndDeleteIfMatchScript, List.of(key), value);
        } catch (Exception ignored) {
        }
    }
}
