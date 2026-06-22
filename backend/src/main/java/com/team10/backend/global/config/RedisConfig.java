package com.team10.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis 연결 설정. commandTimeout을 3초로 줄여 장애 시 요청 스레드가 오래 묶이는 걸 방지한다.
 * 운영 배포 전 SPRING_DATA_REDIS_HOST/PORT/PASSWORD 환경변수 주입 필요.
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration(host, port);
        // 빈 문자열이면 RedisPassword.none()으로 처리되어 무인증 연결과 동일하게 동작한다.
        standaloneConfig.setPassword(password);

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                // Redis 장애 시 요청 스레드가 오래 묶이는 걸 막기 위해 짧게 설정 (기본 60초 → 3초)
                .commandTimeout(Duration.ofSeconds(3))
                .build();

        return new LettuceConnectionFactory(standaloneConfig, clientConfig);
    }

    @Bean
    public StringRedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        // 문자열 키/값만 다루므로 StringRedisTemplate로 충분
        return new StringRedisTemplate(redisConnectionFactory);
    }
}
