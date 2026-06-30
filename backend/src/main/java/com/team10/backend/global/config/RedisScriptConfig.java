package com.team10.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.RedisScript;


@Configuration
public class RedisScriptConfig {

    @Bean
    public RedisScript<Long> incrWithExpireIfNewScript() {
        return RedisScript.of(
                "local v = redis.call('INCR', KEYS[1])\n" +
                "if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
                "return v",
                Long.class
        );
    }

    @Bean
    public RedisScript<Long> incrAndExpireScript() {
        return RedisScript.of(
                "local v = redis.call('INCR', KEYS[1])\n" +
                "redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
                "return v",
                Long.class
        );
    }

    // 임계값 미만일 때만 원자적으로 INCR+EXPIRE, 이미 임계값 이상이면 증가시키지 않고 -1 반환.
    // "확인(GET)"과 "기록(INCR)"이 분리되어 있던 race(예: LoginAttemptService)를 막기 위해
    // 확인+예약을 하나의 Redis 스크립트 호출로 묶는다.
    @Bean
    public RedisScript<Long> checkAndIncrIfBelowLimitScript() {
        return RedisScript.of(
                "local count = tonumber(redis.call('GET', KEYS[1]) or '0')\n" +
                "if count >= tonumber(ARGV[1]) then\n" +
                "  return -1\n" +
                "end\n" +
                "local v = redis.call('INCR', KEYS[1])\n" +
                "redis.call('EXPIRE', KEYS[1], ARGV[2])\n" +
                "return v",
                Long.class
        );
    }

    @Bean
    public RedisScript<Long> getAndDeleteIfMatchScript() {
        return RedisScript.of(
                "local stored = redis.call('GET', KEYS[1])\n" +
                "if stored == ARGV[1] then\n" +
                "  redis.call('DEL', KEYS[1])\n" +
                "  return 1\n" +
                "else\n" +
                "  return 0\n" +
                "end",
                Long.class
        );
    }
}
