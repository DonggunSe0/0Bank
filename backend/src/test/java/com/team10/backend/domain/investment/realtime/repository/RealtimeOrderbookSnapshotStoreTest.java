package com.team10.backend.domain.investment.realtime.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants;
import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookLevel;
import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookPriceSnapshot;
import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RealtimeOrderbookSnapshotStoreTest {

    private final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final RealtimeOrderbookSnapshotStore store = new RealtimeOrderbookSnapshotStore(redisTemplate,
            objectMapper);

    @Test
    @DisplayName("최우선 매도/매수 호가와 서버 수신 시각을 Redis에 저장한다")
    void save() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        store.save(snapshot());

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                Mockito.eq("investment:orderbook:snapshot:005930"),
                payloadCaptor.capture(),
                Mockito.eq(RealtimeOrderbookRedisConstants.ORDERBOOK_SNAPSHOT_TTL)
        );

        RealtimeOrderbookPriceSnapshot saved = objectMapper.readValue(
                payloadCaptor.getValue(),
                RealtimeOrderbookPriceSnapshot.class
        );
        assertThat(saved.stockCode()).isEqualTo("005930");
        assertThat(saved.bestAskPrice()).isEqualTo(70_100L);
        assertThat(saved.bestBidPrice()).isEqualTo(70_000L);
        assertThat(saved.receivedAt()).isNotNull();
    }

    @Test
    @DisplayName("저장된 Redis 스냅샷을 종목코드로 조회한다")
    void findByStockCode() throws Exception {
        RealtimeOrderbookPriceSnapshot snapshot = new RealtimeOrderbookPriceSnapshot(
                "005930",
                70_100L,
                70_000L,
                Instant.now()
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("investment:orderbook:snapshot:005930"))
                .thenReturn(objectMapper.writeValueAsString(snapshot));

        Optional<RealtimeOrderbookPriceSnapshot> found = store.findByStockCode("005930");

        assertThat(found).contains(snapshot);
    }

    private RealtimeOrderbookSnapshot snapshot() {
        return new RealtimeOrderbookSnapshot(
                "005930",
                "145856",
                "0",
                List.of(new RealtimeOrderbookLevel(1, 70_100L, 100L)),
                List.of(new RealtimeOrderbookLevel(1, 70_000L, 200L)),
                100L,
                200L
        );
    }
}
