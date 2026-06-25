package com.team10.backend.global.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisScriptingCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisScriptInitializerTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ApplicationContext applicationContext;
    @Mock RedisConnection redisConnection;
    @Mock RedisScriptingCommands scriptingCommands;

    @InjectMocks
    RedisScriptInitializer initializer;

    @Test
    @DisplayName("기동 시 등록된 모든 RedisScript 빈을 Redis에 SCRIPT LOAD한다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void onApplicationEvent_loadsAllRegisteredScripts() {
        RedisScript<Long> scriptA = RedisScript.of("return 1", Long.class);
        RedisScript<Long> scriptB = RedisScript.of("return 2", Long.class);
        Map<String, RedisScript> scripts = Map.of("scriptA", scriptA, "scriptB", scriptB);

        when(applicationContext.getBeansOfType(RedisScript.class)).thenReturn(scripts);
        when(redisConnection.scriptingCommands()).thenReturn(scriptingCommands);
        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<?> callback = invocation.getArgument(0);
            return callback.doInRedis(redisConnection);
        });

        initializer.onApplicationEvent(mock(ApplicationReadyEvent.class));

        verify(scriptingCommands).scriptLoad("return 1".getBytes(StandardCharsets.UTF_8));
        verify(scriptingCommands).scriptLoad("return 2".getBytes(StandardCharsets.UTF_8));
    }
}
