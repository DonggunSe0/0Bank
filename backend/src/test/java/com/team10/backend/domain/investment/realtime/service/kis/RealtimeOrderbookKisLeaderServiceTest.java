package com.team10.backend.domain.investment.realtime.service.kis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.investment.client.realtime.KisOrderbookWebSocketClient;
import com.team10.backend.domain.investment.realtime.repository.RealtimeOrderbookLeaderLockRepository;
import com.team10.backend.domain.investment.realtime.service.RealtimeOrderbookInstanceIdProvider;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealtimeOrderbookKisLeaderServiceTest {

    @Mock
    private RealtimeOrderbookLeaderLockRepository leaderLockRepository;

    @Mock
    private RealtimeOrderbookInstanceIdProvider instanceIdProvider;

    @Mock
    private RealtimeOrderbookKisSubscriptionCoordinator kisSubscriptionCoordinator;

    @Mock
    private KisOrderbookWebSocketClient kisOrderbookWebSocketClient;

    private RealtimeOrderbookKisLeaderService leaderService;

    @BeforeEach
    void setUp() {
        when(instanceIdProvider.getInstanceId()).thenReturn("instance-a");
        leaderService = new RealtimeOrderbookKisLeaderService(
                leaderLockRepository,
                instanceIdProvider,
                kisSubscriptionCoordinator,
                kisOrderbookWebSocketClient
        );
    }

    @Test
    @DisplayName("이미 leader lock을 보유 중이면 lease를 갱신하고 종목 단위 reconcile을 수행한다")
    void reconcileStockWhenAlreadyLeader() {
        when(leaderLockRepository.renew("instance-a")).thenReturn(true);

        leaderService.reconcileStockIfLeader("005930");

        verify(kisSubscriptionCoordinator).reconcileStock("005930");
        verify(leaderLockRepository, never()).tryAcquire("instance-a");
    }

    @Test
    @DisplayName("leader lock을 새로 획득하면 전체 reconcile을 수행한다")
    void reconcileAllWhenLeaderAcquired() {
        when(leaderLockRepository.renew("instance-a")).thenReturn(false);
        when(leaderLockRepository.tryAcquire("instance-a")).thenReturn(true);

        leaderService.reconcileAllIfLeader();

        verify(kisSubscriptionCoordinator).reconcileAll();
    }

    @Test
    @DisplayName("종목 이벤트 처리 중 leader lock을 새로 획득하면 단일 종목이 아니라 전체 reconcile을 수행한다")
    void reconcileAllWhenLeaderAcquiredByStockEvent() {
        when(leaderLockRepository.renew("instance-a")).thenReturn(false);
        when(leaderLockRepository.tryAcquire("instance-a")).thenReturn(true);

        leaderService.reconcileStockIfLeader("005930");

        verify(kisSubscriptionCoordinator).reconcileAll();
        verify(kisSubscriptionCoordinator, never()).reconcileStock("005930");
    }

    @Test
    @DisplayName("leader lock 획득에 실패하면 reconcile을 수행하지 않는다")
    void skipReconcileWhenNotLeader() {
        when(leaderLockRepository.renew("instance-a")).thenReturn(false);
        when(leaderLockRepository.tryAcquire("instance-a")).thenReturn(false);
        when(kisOrderbookWebSocketClient.isConnected()).thenReturn(false);
        when(kisOrderbookWebSocketClient.subscribedStockCodes()).thenReturn(Set.of());

        leaderService.reconcileStockIfLeader("005930");

        verify(kisSubscriptionCoordinator, never()).reconcileStock("005930");
    }

    @Test
    @DisplayName("leader가 아닌 인스턴스에 KIS WebSocket 상태가 남아 있으면 연결을 종료한다")
    void disconnectLocalKisClientWhenLeadershipLost() {
        when(leaderLockRepository.renew("instance-a")).thenReturn(false);
        when(leaderLockRepository.tryAcquire("instance-a")).thenReturn(false);
        when(kisOrderbookWebSocketClient.isConnected()).thenReturn(true);

        leaderService.reconcileStockIfLeader("005930");

        verify(kisSubscriptionCoordinator, never()).reconcileStock("005930");
        verify(kisOrderbookWebSocketClient).disconnect();
    }

    @Test
    @DisplayName("leader lock 확인 중 Redis 예외가 발생하면 로컬 KIS WebSocket 상태를 정리하고 예외를 전파한다")
    void disconnectLocalKisClientAndRethrowWhenLockCheckFails() {
        when(leaderLockRepository.renew("instance-a")).thenThrow(new IllegalStateException("redis error"));
        when(kisOrderbookWebSocketClient.isConnected()).thenReturn(false);
        when(kisOrderbookWebSocketClient.subscribedStockCodes()).thenReturn(Set.of("005930"));

        assertThatThrownBy(() -> leaderService.reconcileAllIfLeader())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis error");

        verify(kisOrderbookWebSocketClient).disconnect();
        verify(kisSubscriptionCoordinator, never()).reconcileAll();
    }

    @Test
    @DisplayName("leader lock을 정상 반납하면 KIS WebSocket 연결도 종료한다")
    void releaseLeadershipDisconnectsKisClient() {
        when(leaderLockRepository.renew("instance-a")).thenReturn(true);
        when(leaderLockRepository.release("instance-a")).thenReturn(true);
        when(kisOrderbookWebSocketClient.isConnected()).thenReturn(true);

        leaderService.reconcileAllIfLeader();
        leaderService.releaseLeadership();

        verify(kisOrderbookWebSocketClient).disconnect();
    }

    @Test
    @DisplayName("leader lock을 보유한 적이 없으면 반납을 시도하지 않는다")
    void releaseLeadershipSkipsRedisReleaseWhenLeadershipWasNeverHeld() {
        when(kisOrderbookWebSocketClient.isConnected()).thenReturn(false);
        when(kisOrderbookWebSocketClient.subscribedStockCodes()).thenReturn(Set.of());

        leaderService.releaseLeadership();

        verify(leaderLockRepository, never()).release("instance-a");
        verify(kisOrderbookWebSocketClient, never()).disconnect();
    }

    @Test
    @DisplayName("leader lock 반납에 실패해도 로컬 KIS WebSocket 상태가 남아 있으면 연결을 종료한다")
    void releaseLeadershipDisconnectsKisClientWhenReleaseFails() {
        when(leaderLockRepository.renew("instance-a")).thenReturn(true);
        when(leaderLockRepository.release("instance-a")).thenReturn(false);
        when(kisOrderbookWebSocketClient.isConnected()).thenReturn(false);
        when(kisOrderbookWebSocketClient.subscribedStockCodes()).thenReturn(Set.of("005930"));

        leaderService.reconcileAllIfLeader();
        leaderService.releaseLeadership();

        verify(kisOrderbookWebSocketClient).disconnect();
    }

    @Test
    @DisplayName("leader lock 반납 중 예외가 발생해도 예외를 전파하지 않고 로컬 KIS WebSocket 연결을 종료한다")
    void releaseLeadershipDisconnectsKisClientAndDoesNotThrowWhenReleaseThrows() {
        when(leaderLockRepository.renew("instance-a")).thenReturn(true);
        when(leaderLockRepository.release("instance-a")).thenThrow(new IllegalStateException("redis error"));
        when(kisOrderbookWebSocketClient.isConnected()).thenReturn(true);

        leaderService.reconcileAllIfLeader();
        assertThatCode(() -> leaderService.releaseLeadership())
                .doesNotThrowAnyException();

        verify(kisOrderbookWebSocketClient).disconnect();
    }
}
