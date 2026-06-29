package com.team10.backend.global.lock;

import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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

    public boolean tryExecuteWithLock(
            String key,
            Duration leaseTime,
            Runnable task
    ) {
        // 스케줄러처럼 락 획득 실패가 정상적인 skip 흐름인 작업에 사용한다.
        // 락을 즉시 한 번만 시도하고, 다른 인스턴스가 이미 실행 중이면 false를 반환한다.
        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, lockValue, leaseTime);

        if (!Boolean.TRUE.equals(acquired)) {
            return false;
        }

        try {
            task.run();
            return true;
        } finally {
            releaseLock(key, lockValue);
        }
    }

    private void releaseLock(String key, String value) {
        try {
            redisTemplate.execute(getAndDeleteIfMatchScript, List.of(key), value);
        } catch (Exception e) {
            log.warn("분산락 해제 실패. key={}", key, e);
        }
    }
}
