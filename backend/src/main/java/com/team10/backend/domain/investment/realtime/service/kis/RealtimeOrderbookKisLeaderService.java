package com.team10.backend.domain.investment.realtime.service.kis;

import com.team10.backend.domain.investment.client.realtime.KisOrderbookWebSocketClient;
import com.team10.backend.domain.investment.realtime.repository.RealtimeOrderbookLeaderLockRepository;
import com.team10.backend.domain.investment.realtime.service.RealtimeOrderbookInstanceIdProvider;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeOrderbookKisLeaderService {

    private final RealtimeOrderbookLeaderLockRepository leaderLockRepository;
    private final RealtimeOrderbookInstanceIdProvider instanceIdProvider;
    private final RealtimeOrderbookKisSubscriptionCoordinator kisSubscriptionCoordinator;
    private final KisOrderbookWebSocketClient kisOrderbookWebSocketClient;
    private final AtomicBoolean leadershipHeld = new AtomicBoolean(false);

    public void reconcileStockIfLeader(String stockCode) {
        LeadershipStatus leadershipStatus = ensureLeadership();

        if (leadershipStatus == LeadershipStatus.NOT_LEADER) {
            return;
        }

        if (leadershipStatus == LeadershipStatus.ACQUIRED) {
            kisSubscriptionCoordinator.reconcileAll();
            return;
        }

        // ALREADY_LEADER
        kisSubscriptionCoordinator.reconcileStock(stockCode);
    }

    public void reconcileAllIfLeader() {
        if (ensureLeadership() == LeadershipStatus.NOT_LEADER) {
            return;
        }

        kisSubscriptionCoordinator.reconcileAll();
    }

    private LeadershipStatus ensureLeadership() {
        String instanceId = instanceIdProvider.getInstanceId();

        try {
            if (leaderLockRepository.renew(instanceId)) {
                leadershipHeld.set(true);
                return LeadershipStatus.ALREADY_LEADER;
            }

            if (leaderLockRepository.tryAcquire(instanceId)) {
                leadershipHeld.set(true);
                log.info("Realtime orderbook KIS WebSocket leader acquired. instanceId={}", instanceId);
                return LeadershipStatus.ACQUIRED;
            }
        } catch (RuntimeException e) {
            /** leader 여부를 확신할 수 없는 문제 상황 시 오류 발생 방지를 위해 연결 해제 시도 */
            disconnectLocalKisClientIfNecessary();
            throw e;
        }

        leadershipHeld.set(false);
        disconnectLocalKisClientIfNecessary();
        return LeadershipStatus.NOT_LEADER;
    }

    public void releaseLeadership() {
        String instanceId = instanceIdProvider.getInstanceId();
        if (!leadershipHeld.get()) {
            disconnectLocalKisClientIfNecessary();
            return;
        }

        try {
            if (leaderLockRepository.release(instanceId)) {
                log.info("Realtime orderbook KIS WebSocket leader released. instanceId={}", instanceId);
            }
        } catch (RuntimeException e) {
            log.warn("Failed to release realtime orderbook KIS WebSocket leader. instanceId={}", instanceId, e);
        } finally {
            leadershipHeld.set(false);
            disconnectLocalKisClientIfNecessary();
        }
    }

    private void disconnectLocalKisClientIfNecessary() {
        try {
            boolean connected = kisOrderbookWebSocketClient.isConnected();
            Set<String> subscribedStockCodes = connected ? Set.of() : kisOrderbookWebSocketClient.subscribedStockCodes();

            if (connected || (subscribedStockCodes != null && !subscribedStockCodes.isEmpty())) {
                kisOrderbookWebSocketClient.disconnect();
            }
        } catch (RuntimeException e) {
            log.warn("Failed to disconnect local KIS orderbook WebSocket client.", e);
        }
    }

    private enum LeadershipStatus {
        ALREADY_LEADER,
        ACQUIRED,
        NOT_LEADER
    }
}
