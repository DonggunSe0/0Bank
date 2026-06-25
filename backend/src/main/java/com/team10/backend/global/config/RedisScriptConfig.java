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
