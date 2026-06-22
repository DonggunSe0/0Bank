package com.team10.backend.domain.investment.realtime.scheduler;

import static org.mockito.Mockito.verify;

import com.team10.backend.domain.investment.realtime.service.kis.RealtimeOrderbookKisLeaderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RealtimeOrderbookKisLeaderReconcileSchedulerTest {

    private final RealtimeOrderbookKisLeaderService leaderService =
            Mockito.mock(RealtimeOrderbookKisLeaderService.class);
    private final RealtimeOrderbookKisLeaderReconcileScheduler scheduler =
            new RealtimeOrderbookKisLeaderReconcileScheduler(leaderService);

    @Test
    @DisplayName("스케줄러는 leader 인스턴스에서 KIS WebSocket 구독 상태 전체 대조를 요청한다")
    void reconcileKisSubscriptions() {
        scheduler.reconcileKisSubscriptions();

        verify(leaderService).reconcileAllIfLeader();
    }
}
