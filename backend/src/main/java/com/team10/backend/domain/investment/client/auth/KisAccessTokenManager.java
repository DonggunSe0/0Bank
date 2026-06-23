package com.team10.backend.domain.investment.client.auth;

import static com.team10.backend.domain.investment.config.KisConstants.SEOUL_ZONE;

import com.team10.backend.domain.investment.client.auth.dto.KisAccessToken;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisAccessTokenManager {

    /**
     * 엑세스 토큰의 만료 시간보다 조금 일찍 재발급 처리할 수 있도록 버퍼 타임 정의
     */
    private static final long REFRESH_BUFFER_MINUTES = 15L;

    private final KisAuthClient kisAuthClient;

    /**
     * 멀티 스레드 상황에서 메모리 가시성을 위해 volatile 사용
     */
    private volatile String accessToken;
    private volatile LocalDateTime expiresAt;

    /**
     * API 호출을 위한 AccessToken을 획득하는 클래스. 애플리케이션 레벨 동시성 제어를 위해 synchronized 사용
     */
    public String getAccessToken() {
        if (needRefresh()) {
            synchronized (this) {
                if (needRefresh()) {
                    /**
                     * 재시도를 적용하면 synchronized 블록 안에서 다른 요청 스레드까지 대기하게 된다.
                     * 발급 실패는 현재 요청만 실패시키고, 다음 요청에서 다시 lazy 발급을 시도한다.
                     */
                    refreshToken();
                }
            }
        }

        return accessToken;
    }

    /**
     * AccessToken이 필요한 API를 최초 호출 시 or 발급받은 토큰의 만료 시간이 임박했을 때
     */
    private boolean needRefresh() {
        return (accessToken == null || expiresAt == null)
                || LocalDateTime.now(SEOUL_ZONE).plusMinutes(REFRESH_BUFFER_MINUTES).isAfter(expiresAt);
    }

    /**
     * accessToken 획득하는 KIS API 호출
     */
    private void refreshToken() {
        KisAccessToken issuedToken = kisAuthClient.issueAccessToken();
        this.accessToken = issuedToken.accessToken();
        this.expiresAt = issuedToken.expiresAt();

        log.info("Refreshing KIS access token");
    }

    /**
     * 애플리케이션 종료 시점에 엑세스토큰 폐기 요청을 수행한다. 애플리케이션의 종료에 중대한 영향을 주는 로직은 아니기에 실패하더라도 로그만 남긴 후 애플리케이션 종료한다.
     */
    @PreDestroy
    public void revokeOnShutdown() {
        String token = this.accessToken;
        if (token == null) {
            return;
        }

        try {
            kisAuthClient.revokeAccessToken(token);
        } catch (RuntimeException e) {
            log.warn("Failed to revoke KIS access token on shutdown", e);
        } finally {
            clear();
        }
    }

    public synchronized void clear() {
        this.accessToken = null;
        this.expiresAt = null;
    }
}
