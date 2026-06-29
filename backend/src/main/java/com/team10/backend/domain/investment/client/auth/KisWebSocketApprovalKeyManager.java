package com.team10.backend.domain.investment.client.auth;

import com.team10.backend.domain.investment.client.auth.dto.KisWebSocketApprovalKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class KisWebSocketApprovalKeyManager {

    private final KisAuthClient kisAuthClient;

    /**
     * 멀티 스레드 상황에서 메모리 가시성을 위해 volatile 사용
     */
    private volatile String approvalKey;

    /**
     * WebSocket 연결 직전에 사용할 접속키를 lazy 방식으로 발급한다.
     */
    public String getApprovalKey() {
        if (approvalKey == null) {
            synchronized (this) {
                if (approvalKey == null) {
                    /**
                     * 재시도를 적용하면 최초 연결 스레드가 오래 대기하고 이로 인해 동시 요청 타 스레드들까지 락에 걸려 대기하게된다.
                     * 발급 실패는 현재 연결만 실패시켜 타 스레드가 즉시 락을 획득하고 시도할 수 있도록한다.
                     */
                    issueApprovalKey();
                }
            }
        }

        return approvalKey;
    }

    private void issueApprovalKey() {
        KisWebSocketApprovalKey issuedKey = kisAuthClient.issueWebSocketApprovalKey();
        this.approvalKey = issuedKey.approvalKey();
    }
}
