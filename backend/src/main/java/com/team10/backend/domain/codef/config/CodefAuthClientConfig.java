package com.team10.backend.domain.codef.config;

import com.team10.backend.domain.codef.client.CodefAuthClient;
import com.team10.backend.domain.codef.client.CodefOAuthExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * CODEF는 상품(계좌조회/OCR, 1원송금)마다 별도로 자격증명을 발급하므로 {@link CodefAuthClient}를
 * 단일 공유 빈으로 두지 않고 용도별로 분리한 빈 두 개로 등록한다.
 *
 * <ul>
 *   <li>{@code accountInquiry} — 신분증 OCR 등에서 사용 ({@code codef.account-inquiry.*})</li>
 *   <li>{@code oneWonTransfer} — 1원 송금 계좌인증에서 사용 ({@code codef.one-won-transfer.*})</li>
 * </ul>
 *
 * 각 빈은 {@link CodefAuthClient} 생성 시 purpose를 넘겨받아 Redis 토큰 캐시/락 키를 분리하므로,
 * 두 용도의 토큰이 같은 Redis 키를 두고 서로 덮어쓰는 일은 없다.
 */
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
