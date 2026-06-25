package com.team10.backend.domain.investment.realtime.repository;

import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.ORDERBOOK_SNAPSHOT_KEY_PREFIX;
import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.ORDERBOOK_SNAPSHOT_TTL;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookLevel;
import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookPriceSnapshot;
import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RealtimeOrderbookSnapshotStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void save(RealtimeOrderbookSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        validateStockCode(snapshot.stockCode());

        RealtimeOrderbookPriceSnapshot priceSnapshot = new RealtimeOrderbookPriceSnapshot(
                snapshot.stockCode(),
                bestPrice(snapshot.asks()),
                bestPrice(snapshot.bids()),
                Instant.now()
        );

        try {
            redisTemplate.opsForValue().set(
                    key(snapshot.stockCode()),
                    objectMapper.writeValueAsString(priceSnapshot),
                    ORDERBOOK_SNAPSHOT_TTL
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("실시간 호가 스냅샷 직렬화에 실패했습니다.", e);
        }
    }

    public Optional<RealtimeOrderbookPriceSnapshot> findByStockCode(String stockCode) {
        validateStockCode(stockCode);

        String payload;
        try {
            payload = redisTemplate.opsForValue().get(key(stockCode));
        } catch (RuntimeException e) {
            log.warn("Failed to read realtime orderbook snapshot. stockCode={}", stockCode, e);
            return Optional.empty();
        }

        if (!StringUtils.hasText(payload)) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(payload, RealtimeOrderbookPriceSnapshot.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse realtime orderbook snapshot. stockCode={}, payload={}", stockCode, payload, e);
            return Optional.empty();
        }
    }

    private Long bestPrice(List<RealtimeOrderbookLevel> levels) {
        if (levels == null || levels.isEmpty()) {
            return null;
        }
        return levels.get(0).price();
    }

    private String key(String stockCode) {
        return ORDERBOOK_SNAPSHOT_KEY_PREFIX + stockCode;
    }

    private void validateStockCode(String stockCode) {
        if (!StringUtils.hasText(stockCode)) {
            throw new IllegalArgumentException("stockCode must not be blank");
        }
    }
}
