package com.team10.backend.domain.codef.exAccount.client;

import com.team10.backend.domain.codef.exAccount.config.CodefExAccountProperties;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountAuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CodefExAccountAuthClientTest {

    private static final String OAUTH_TOKEN_URL = "https://oauth.codef.io/oauth/token";
    private static final String REDIS_TOKEN_KEY = "codef:oauth:token:account-inquiry";
    private static final String REDIS_LOCK_KEY = "codef:oauth:token:lock:account-inquiry";

    private MockRestServiceServer server;
    private CodefExAccountAuthClient authClient;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisScript<Long> getAndDeleteIfMatchScript;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        getAndDeleteIfMatchScript = mock(RedisScript.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(REDIS_LOCK_KEY), anyString(), eq(Duration.ofSeconds(10))))
                .thenReturn(true);
        authClient = new CodefExAccountAuthClient(
                properties(),
                builder.build(),
                redisTemplate,
                getAndDeleteIfMatchScript
        );
    }

    @Test
    void issuesAccessTokenWithAccountInquiryCredentials() {
        String credentials = Base64.getEncoder().encodeToString(
                "account-client-id:account-client-secret".getBytes(StandardCharsets.UTF_8)
        );

        server.expect(requestTo(OAUTH_TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic " + credentials))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string("grant_type=client_credentials&scope=read"))
                .andRespond(withSuccess("""
                        {
                          "access_token": "account-access-token",
                          "expires_in": 3600
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(authClient.getAccessToken()).isEqualTo("account-access-token");
        verify(valueOperations).set(
                eq(REDIS_TOKEN_KEY),
                eq("account-access-token"),
                eq(Duration.ofSeconds(3300))
        );
        verify(redisTemplate).execute(
                eq(getAndDeleteIfMatchScript),
                eq(List.of(REDIS_LOCK_KEY)),
                anyString()
        );
        server.verify();
    }

    @Test
    void reusesCachedTokenForConcurrentRequests() {
        server.expect(requestTo(OAUTH_TOKEN_URL))
                .andRespond(withSuccess("""
                        {
                          "access_token": "cached-access-token",
                          "expires_in": 3600
                        }
                        """, MediaType.APPLICATION_JSON));

        List<CompletableFuture<String>> requests = IntStream.range(0, 10)
                .mapToObj(ignored -> CompletableFuture.supplyAsync(authClient::getAccessToken))
                .toList();

        assertThat(requests)
                .allSatisfy(request -> assertThat(request.join()).isEqualTo("cached-access-token"));
        server.verify();
    }

    @Test
    void reusesSharedRedisTokenWhenLocalCacheIsEmpty() {
        when(valueOperations.get(REDIS_TOKEN_KEY)).thenReturn("shared-access-token");
        when(redisTemplate.getExpire(REDIS_TOKEN_KEY)).thenReturn(1200L);

        assertThat(authClient.getAccessToken()).isEqualTo("shared-access-token");

        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
        server.verify();
    }

    @Test
    void waitsForSharedRedisTokenWhenAnotherInstanceOwnsLock() {
        when(valueOperations.setIfAbsent(eq(REDIS_LOCK_KEY), anyString(), eq(Duration.ofSeconds(10))))
                .thenReturn(false);
        when(valueOperations.get(REDIS_TOKEN_KEY))
                .thenReturn(null)
                .thenReturn("shared-access-token");
        when(redisTemplate.getExpire(REDIS_TOKEN_KEY)).thenReturn(1200L);

        assertThat(authClient.getAccessToken()).isEqualTo("shared-access-token");

        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
        server.verify();
    }

    @Test
    void issuesTokenWhenRedisReadFails() {
        when(valueOperations.get(REDIS_TOKEN_KEY)).thenThrow(new RuntimeException("redis unavailable"));
        server.expect(requestTo(OAUTH_TOKEN_URL))
                .andRespond(withSuccess("""
                        {
                          "access_token": "fallback-access-token",
                          "expires_in": 3600
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(authClient.getAccessToken()).isEqualTo("fallback-access-token");
        server.verify();
    }

    @Test
    void returnsIssuedTokenWhenRedisWriteFails() {
        doThrow(new RuntimeException("redis unavailable"))
                .when(valueOperations)
                .set(eq(REDIS_TOKEN_KEY), eq("issued-access-token"), eq(Duration.ofSeconds(3300)));
        server.expect(requestTo(OAUTH_TOKEN_URL))
                .andRespond(withSuccess("""
                        {
                          "access_token": "issued-access-token",
                          "expires_in": 3600
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(authClient.getAccessToken()).isEqualTo("issued-access-token");
        server.verify();
    }

    @Test
    void issuesTokenWhenRedisLockFails() {
        when(valueOperations.setIfAbsent(eq(REDIS_LOCK_KEY), anyString(), eq(Duration.ofSeconds(10))))
                .thenThrow(new RuntimeException("redis unavailable"));
        server.expect(requestTo(OAUTH_TOKEN_URL))
                .andRespond(withSuccess("""
                        {
                          "access_token": "lock-fallback-access-token",
                          "expires_in": 3600
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(authClient.getAccessToken()).isEqualTo("lock-fallback-access-token");
        server.verify();
    }

    @Test
    void rejectsInvalidOAuthResponse() {
        server.expect(requestTo(OAUTH_TOKEN_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(authClient::getAccessToken)
                .isInstanceOf(CodefExAccountAuthException.class)
                .hasMessage("CODEF 외부계좌 OAuth 응답이 올바르지 않습니다.");
        server.verify();
    }

    private CodefExAccountProperties properties() {
        return new CodefExAccountProperties(
                "DEMO",
                "account-client-id",
                "account-client-secret",
                "account-public-key",
                "https://development.codef.io",
                "/v1/account/create",
                "/account-list",
                "/transaction-list"
        );
    }
}
