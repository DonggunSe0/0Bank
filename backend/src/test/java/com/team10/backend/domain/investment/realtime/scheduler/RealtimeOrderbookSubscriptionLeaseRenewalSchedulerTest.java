package com.team10.backend.domain.investment.realtime.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.investment.realtime.repository.RealtimeOrderbookSubscriptionStore;
import com.team10.backend.domain.investment.realtime.service.stream.RealtimeOrderbookSseConnection;
import com.team10.backend.domain.investment.realtime.service.stream.RealtimeOrderbookSseEmitterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealtimeOrderbookSubscriptionLeaseRenewalSchedulerTest {

    @Mock
    private RealtimeOrderbookSubscriptionStore subscriptionStore;

    private RealtimeOrderbookSseEmitterRegistry emitterRegistry;
    private RealtimeOrderbookSubscriptionLeaseRenewalScheduler scheduler;

    @BeforeEach
    void setUp() {
        emitterRegistry = new RealtimeOrderbookSseEmitterRegistry();
        scheduler = new RealtimeOrderbookSubscriptionLeaseRenewalScheduler(emitterRegistry, subscriptionStore);
    }

    @Test
    @DisplayName("로컬 SSE stream이 없으면 Redis lease 갱신을 수행하지 않는다")
    void renewLocalStreamLeasesWithoutStreams() {
        scheduler.renewLocalStreamLeases();

        verify(subscriptionStore, never()).renewLease(anyString());
    }

    @Test
    @DisplayName("heartbeat 후 현재 인스턴스에 연결된 모든 SSE stream의 Redis lease를 갱신한다")
    void renewLocalStreamLeases() {
        RealtimeOrderbookSseConnection first = emitterRegistry.register(1L, "005930", null);
        RealtimeOrderbookSseConnection second = emitterRegistry.register(2L, "000660", null);
        when(subscriptionStore.renewLease(first.streamId())).thenReturn(true);
        when(subscriptionStore.renewLease(second.streamId())).thenReturn(true);

        scheduler.renewLocalStreamLeases();

        verify(subscriptionStore).renewLease(first.streamId());
        verify(subscriptionStore).renewLease(second.streamId());
        assertThat(emitterRegistry.streamCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Redis lease 상태가 없으면 로컬 SSE stream을 종료한다")
    void closeLocalStreamWhenLeaseStateIsMissing() {
        RealtimeOrderbookSseConnection connection = emitterRegistry.register(1L, "005930", null);
        when(subscriptionStore.renewLease(connection.streamId())).thenReturn(false);

        scheduler.renewLocalStreamLeases();

        assertThat(emitterRegistry.find(connection.streamId())).isEmpty();
        assertThat(emitterRegistry.streamCount()).isZero();
    }

    @Test
    @DisplayName("Redis lease 갱신 중 예외가 발생하면 로컬 SSE stream을 유지하고 다음 주기에 재시도할 수 있게 한다")
    void keepLocalStreamWhenLeaseRenewalFails() {
        RealtimeOrderbookSseConnection connection = emitterRegistry.register(1L, "005930", null);
        when(subscriptionStore.renewLease(connection.streamId())).thenThrow(new IllegalStateException("redis error"));

        scheduler.renewLocalStreamLeases();

        assertThat(emitterRegistry.find(connection.streamId())).contains(connection);
        assertThat(emitterRegistry.streamCount()).isEqualTo(1);
    }
}
