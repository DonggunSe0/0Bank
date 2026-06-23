package com.team10.backend.domain.investment.realtime.scheduler;

import com.team10.backend.domain.investment.realtime.service.kis.RealtimeOrderbookKisLeaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "investment.realtime.kis-leader-reconcile",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RealtimeOrderbookKisLeaderReconcileScheduler {

    private final RealtimeOrderbookKisLeaderService leaderService;

    /**
     * KIS WebSocket leader를 획득/갱신한 인스턴스만 Redis 구독 상태와 KIS 실제 구독 상태를 전체 대조한다.
     */
    @Scheduled(fixedDelayString = "${investment.realtime.kis-leader-reconcile.fixed-delay-ms:10000}")
    public void reconcileKisSubscriptions() {
        leaderService.reconcileAllIfLeader();
    }
}
