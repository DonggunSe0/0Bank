package com.team10.backend.domain.investment.realtime.service;

import com.team10.backend.domain.investment.realtime.service.kis.RealtimeOrderbookKisLeaderService;
import com.team10.backend.domain.investment.realtime.service.stream.RealtimeOrderbookStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeOrderbookGracefulShutdownHandler {

    private final RealtimeOrderbookStreamService streamService;
    private final RealtimeOrderbookKisLeaderService kisLeaderService;

    @EventListener(ContextClosedEvent.class)
    public void shutdown() {
        closeLocalSseStreams();
        releaseKisLeadership();
    }

    private void closeLocalSseStreams() {
        try {
            streamService.closeLocalStreamsOnShutdown();
        } catch (RuntimeException e) {
            log.warn("Failed to close realtime orderbook local SSE streams on shutdown.", e);
        }
    }

    private void releaseKisLeadership() {
        try {
            kisLeaderService.releaseLeadership();
        } catch (RuntimeException e) {
            log.warn("Failed to release realtime orderbook KIS leadership on shutdown.", e);
        }
    }
}
