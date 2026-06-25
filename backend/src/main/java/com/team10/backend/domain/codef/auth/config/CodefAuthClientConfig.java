package com.team10.backend.domain.codef.auth.config;

import com.team10.backend.domain.codef.auth.client.CodefAuthClient;
import com.team10.backend.domain.codef.auth.client.CodefOAuthExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * {@link CodefAuthClient}를 {@code oneWonTransfer} 자격증명으로 등록한다.
 * OCR/1원송금이 모두 이 자격증명을 공유해서 쓴다({@link CodefHttpServiceConfig} 참고).
 */
@Configuration
public class CodefAuthClientConfig {

    @Bean
    @Qualifier("oneWonTransfer")
    public CodefAuthClient oneWonTransferAuthClient(
            @Value("${codef.one-won-transfer.client-id}") String clientId,
            @Value("${codef.one-won-transfer.client-secret}") String clientSecret,
            CodefOAuthExchange codefOAuthExchange,
            StringRedisTemplate redisTemplate,
            RedisScript<Long> getAndDeleteIfMatchScript
    ) {
        return new CodefAuthClient(
                "one-won-transfer", clientId, clientSecret,
                codefOAuthExchange, redisTemplate, getAndDeleteIfMatchScript);
    }
}
