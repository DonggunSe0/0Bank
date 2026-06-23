package com.team10.backend.domain.exAccount.service;

import com.team10.backend.domain.exAccount.exception.ExAccountConnectionErrorCode;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExAccountCodefRateLimitServiceTest {

    private StringRedisTemplate redisTemplate;
    private RedisScript<Long> incrWithExpireIfNewScript;
    private ExAccountCodefRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        incrWithExpireIfNewScript = mock(RedisScript.class);
        rateLimitService = new ExAccountCodefRateLimitService(
                redisTemplate,
                incrWithExpireIfNewScript
        );
    }

    @Test
    void allowsRegisterWithinUserOrganizationLimit() {
        when(redisTemplate.execute(
                eq(incrWithExpireIfNewScript),
                eq(List.of("codef:ex-account:rate:register:1:0004")),
                eq("60")
        )).thenReturn(3L);

        rateLimitService.checkRegister(1L, "0004");

        verify(redisTemplate).execute(
                eq(incrWithExpireIfNewScript),
                eq(List.of("codef:ex-account:rate:register:1:0004")),
                eq("60")
        );
    }

    @Test
    void rejectsRegisterOverUserOrganizationLimit() {
        when(redisTemplate.execute(
                eq(incrWithExpireIfNewScript),
                eq(List.of("codef:ex-account:rate:register:1:0004")),
                eq("60")
        )).thenReturn(4L);

        assertThatThrownBy(() -> rateLimitService.checkRegister(1L, "0004"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_REGISTER_RATE_LIMIT_EXCEEDED));
    }

    @Test
    void rejectsAccountListOverUserOrganizationLimit() {
        when(redisTemplate.execute(
                eq(incrWithExpireIfNewScript),
                eq(List.of("codef:ex-account:rate:account-list:1:0004")),
                eq("60")
        )).thenReturn(11L);

        assertThatThrownBy(() -> rateLimitService.checkAccountList(1L, "0004"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                ExAccountConnectionErrorCode.EX_ACCOUNT_CONNECTION_ACCOUNT_LIST_RATE_LIMIT_EXCEEDED));
    }
}
