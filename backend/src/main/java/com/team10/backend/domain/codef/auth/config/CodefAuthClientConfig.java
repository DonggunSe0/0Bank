package com.team10.backend.domain.codef.auth.config;

import com.team10.backend.domain.codef.auth.client.CodefAuthClient;
import com.team10.backend.domain.codef.auth.client.CodefOAuthExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** {@link CodefAuthClient}를 용도별({@code accountInquiry}/{@code oneWonTransfer}) 빈 두 개로 분리 등록한다. */
@Configuration
public class CodefAuthClientConfig {

    @Bean
    @Qualifier("accountInquiry")
    public CodefAuthClient accountInquiryAuthClient(
            @Value("${codef.account-inquiry.client-id}") String clientId,
            @Value("${codef.account-inquiry.client-secret}") String clientSecret,
            CodefOAuthExchange codefOAuthExchange,
            StringRedisTemplate redisTemplate,
            RedisScript<Long> getAndDeleteIfMatchScript
    ) {
        return new CodefAuthClient(
                "account-inquiry", clientId, clientSecret,
                codefOAuthExchange, redisTemplate, getAndDeleteIfMatchScript);
    }

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
