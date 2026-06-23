package com.team10.backend.domain.investment.realtime.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.team10.backend.domain.investment.realtime.service.kis.RealtimeOrderbookKisLeaderService;
import com.team10.backend.domain.investment.realtime.service.stream.RealtimeOrderbookStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealtimeOrderbookGracefulShutdownHandlerTest {

    @Mock
    private RealtimeOrderbookStreamService streamService;

    @Mock
    private RealtimeOrderbookKisLeaderService kisLeaderService;

    private RealtimeOrderbookGracefulShutdownHandler shutdownHandler;

    @BeforeEach
    void setUp() {
        shutdownHandler = new RealtimeOrderbookGracefulShutdownHandler(streamService, kisLeaderService);
    }

    @Test
    @DisplayName("종료 시 로컬 SSE stream을 먼저 닫고 KIS leader 상태를 정리한다")
    void shutdown() {
        shutdownHandler.shutdown();

        InOrder inOrder = inOrder(streamService, kisLeaderService);
        inOrder.verify(streamService).closeLocalStreamsOnShutdown();
        inOrder.verify(kisLeaderService).releaseLeadership();
    }

    @Test
    @DisplayName("로컬 SSE stream 정리에 실패해도 KIS leader 상태 정리를 시도한다")
    void shutdownContinuesWhenClosingLocalStreamsFails() {
        doThrow(new IllegalStateException("sse error"))
                .when(streamService)
                .closeLocalStreamsOnShutdown();

        shutdownHandler.shutdown();

        verify(kisLeaderService).releaseLeadership();
    }
}
