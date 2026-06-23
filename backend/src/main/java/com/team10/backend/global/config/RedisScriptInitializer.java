package com.team10.backend.global.config;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 서버 기동 완료 시 {@link RedisScriptConfig}에 등록된 모든 Lua 스크립트를 Redis에 SCRIPT LOAD한다.
 * 이후 각 서비스의 {@code redisTemplate.execute(script, ...)} 호출은 EVALSHA로 곧바로 처리된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisScriptInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final StringRedisTemplate redisTemplate;
    private final ApplicationContext applicationContext;

    @Override
    @SuppressWarnings("rawtypes")
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Map<String, RedisScript> scripts = applicationContext.getBeansOfType(RedisScript.class);

        redisTemplate.execute((RedisConnection connection) -> {
            scripts.forEach((beanName, script) -> {
                String sha = connection.scriptingCommands()
                        .scriptLoad(script.getScriptAsString().getBytes(StandardCharsets.UTF_8));
                log.info("[Redis] Lua 스크립트 로드 완료 - bean={}, sha={}", beanName, sha);
            });
            return null;
        });
    }
}
