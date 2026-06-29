package com.team10.backend.domain.investment.realtime.scheduler;

import com.team10.backend.domain.investment.realtime.repository.RealtimeOrderbookSubscriptionStore;
import com.team10.backend.domain.investment.realtime.service.stream.RealtimeOrderbookSseEmitterRegistry;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "investment.realtime.stream-lease-renewal",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RealtimeOrderbookSubscriptionLeaseRenewalScheduler {

    private final RealtimeOrderbookSseEmitterRegistry emitterRegistry;
    private final RealtimeOrderbookSubscriptionStore subscriptionStore;

    /**
     * 현재 인스턴스에 연결된 SSE stream의 Redis lease를 주기적으로 연장한다.
     *
     * <p>Redis의 구독 상태는 멀티 인스턴스 환경에서 KIS WebSocket 구독 유지 여부를 판단하는 기준이다.
     * SSE 연결은 살아있지만 Redis lease가 만료되면 leader가 해당 종목의 구독자가 없다고 판단할 수 있으므로, 로컬에 살아있는 stream의 lease를 TTL보다 짧은 주기로 갱신한다.
     */
    @Scheduled(fixedDelayString = "${investment.realtime.stream-lease-renewal.fixed-delay-ms:30000}")
    public void renewLocalStreamLeases() {
        Set<String> streamIds = emitterRegistry.findAllStreamIds();
        if (streamIds.isEmpty()) {
            return;
        }

        int heartbeatSentCount = emitterRegistry.sendHeartbeatToAll();
        Set<String> aliveStreamIds = emitterRegistry.findAllStreamIds();
        if (aliveStreamIds.isEmpty()) {
            log.info("Realtime orderbook stream lease renewal skipped. total={}, heartbeatSent={}, alive=0",
                    streamIds.size(),
                    heartbeatSentCount);
            return;
        }

        int renewedCount = 0;
        int closedCount = 0;
        int failedCount = 0;

        for (String streamId : aliveStreamIds) {
            try {
                if (subscriptionStore.renewLease(streamId)) {
                    renewedCount++;
                    continue;
                }

                if (emitterRegistry.complete(streamId)) {
                    closedCount++;
                }
                log.warn("Closed realtime orderbook SSE stream because Redis lease state is missing. streamId={}",
                        streamId);
            } catch (RuntimeException e) {
                failedCount++;
                log.warn("Failed to renew realtime orderbook SSE stream lease. streamId={}", streamId, e);
            }
        }

        log.info(
                "Realtime orderbook stream lease renewal completed. total={}, heartbeatSent={}, alive={}, renewed={}, closed={}, failed={}",
                streamIds.size(),
                heartbeatSentCount,
                aliveStreamIds.size(),
                renewedCount,
                closedCount,
                failedCount);
    }
}
