package com.team10.backend.domain.investment.realtime.repository;

import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.ACTIVE_STOCKS_KEY;
import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.LEASE_KEY_PREFIX;
import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.STOCK_STREAMS_KEY_PREFIX;
import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.STREAMS_KEY_SUFFIX;
import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.STREAM_KEY_PREFIX;
import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.STREAM_LEASE_TTL;
import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.STREAM_OWNER_KEY_SUFFIX;
import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.STREAM_STOCK_KEY_SUFFIX;
import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.STREAM_USER_KEY_SUFFIX;
import static com.team10.backend.domain.investment.realtime.config.RealtimeOrderbookRedisConstants.USER_STREAMS_KEY_PREFIX;

import com.team10.backend.domain.investment.realtime.dto.RealtimeOrderbookSubscription;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;


/**
 * Redis에 접근하여 구독 상태를 조회 및 관리하는 Repository
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RealtimeOrderbookSubscriptionStore {

    private static final RedisScript<Long> SAVE_SCRIPT = RedisScript.of(
            "local function pruneStock(stockCode)\n" +
                    "  local stockStreamsKey = ARGV[8] .. stockCode .. ARGV[9]\n" +
                    "  local streamIds = redis.call('SMEMBERS', stockStreamsKey)\n" +
                    "  local activeCount = 0\n" +
                    "  for _, streamId in ipairs(streamIds) do\n" +
                    "    if redis.call('GET', ARGV[7] .. streamId) == stockCode then\n" +
                    "      activeCount = activeCount + 1\n" +
                    "    else\n" +
                    "      redis.call('SREM', stockStreamsKey, streamId)\n" +
                    "    end\n" +
                    "  end\n" +
                    "  if activeCount == 0 then\n" +
                    "    redis.call('SREM', KEYS[7], stockCode)\n" +
                    "  end\n" +
                    "end\n" +
                    "local activeStockCodes = redis.call('SMEMBERS', KEYS[7])\n" +
                    "for _, activeStockCode in ipairs(activeStockCodes) do\n" +
                    "  pruneStock(activeStockCode)\n" +
                    "end\n" +
                    "if redis.call('SISMEMBER', KEYS[7], ARGV[2]) == 0\n" +
                    "    and redis.call('SCARD', KEYS[7]) >= tonumber(ARGV[6]) then\n" +
                    "  return 0\n" +
                    "end\n" +
                    "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[5])\n" +
                    "redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[5])\n" +
                    "redis.call('SET', KEYS[3], ARGV[3], 'EX', ARGV[5])\n" +
                    "redis.call('SET', KEYS[4], ARGV[2], 'EX', ARGV[5])\n" +
                    "redis.call('SADD', KEYS[5], ARGV[4])\n" +
                    "redis.call('SADD', KEYS[6], ARGV[4])\n" +
                    "redis.call('SADD', KEYS[7], ARGV[2])\n" +
                    "return 1",
            Long.class
    );

    private static final RedisScript<List> DELETE_SCRIPT = RedisScript.of(
            "local userId = redis.call('GET', KEYS[1])\n" +
                    "local stockCode = redis.call('GET', KEYS[2])\n" +
                    "local ownerInstanceId = redis.call('GET', KEYS[3])\n" +
                    "if not userId or not stockCode then\n" +
                    "  return {}\n" +
                    "end\n" +
                    "redis.call('DEL', KEYS[1], KEYS[2], KEYS[3], KEYS[4])\n" +
                    "redis.call('SREM', ARGV[1] .. userId .. ARGV[3], ARGV[4])\n" +
                    "local stockStreamsKey = ARGV[2] .. stockCode .. ARGV[3]\n" +
                    "redis.call('SREM', stockStreamsKey, ARGV[4])\n" +
                    "local remainingStreamIds = redis.call('SMEMBERS', stockStreamsKey)\n" +
                    "local hasActiveStream = false\n" +
                    "for _, remainingStreamId in ipairs(remainingStreamIds) do\n" +
                    "  if redis.call('GET', ARGV[5] .. remainingStreamId) == stockCode then\n" +
                    "    hasActiveStream = true\n" +
                    "  else\n" +
                    "    redis.call('SREM', stockStreamsKey, remainingStreamId)\n" +
                    "  end\n" +
                    "end\n" +
                    "if not hasActiveStream then\n" +
                    "  redis.call('SREM', KEYS[5], stockCode)\n" +
                    "end\n" +
                    "return {userId, stockCode, ownerInstanceId or ''}",
            List.class
    );

    private static final RedisScript<List> DELETE_IF_OWNER_SCRIPT = RedisScript.of(
            "local userId = redis.call('GET', KEYS[1])\n" +
                    "local stockCode = redis.call('GET', KEYS[2])\n" +
                    "local ownerInstanceId = redis.call('GET', KEYS[3])\n" +
                    "if not userId or not stockCode or userId ~= ARGV[1] then\n" +
                    "  return {}\n" +
                    "end\n" +
                    "redis.call('DEL', KEYS[1], KEYS[2], KEYS[3], KEYS[4])\n" +
                    "redis.call('SREM', KEYS[5], ARGV[3])\n" +
                    "local stockStreamsKey = ARGV[2] .. stockCode .. ARGV[4]\n" +
                    "redis.call('SREM', stockStreamsKey, ARGV[3])\n" +
                    "local remainingStreamIds = redis.call('SMEMBERS', stockStreamsKey)\n" +
                    "local hasActiveStream = false\n" +
                    "for _, remainingStreamId in ipairs(remainingStreamIds) do\n" +
                    "  if redis.call('GET', ARGV[5] .. remainingStreamId) == stockCode then\n" +
                    "    hasActiveStream = true\n" +
                    "  else\n" +
                    "    redis.call('SREM', stockStreamsKey, remainingStreamId)\n" +
                    "  end\n" +
                    "end\n" +
                    "if not hasActiveStream then\n" +
                    "  redis.call('SREM', KEYS[6], stockCode)\n" +
                    "end\n" +
                    "return {userId, stockCode, ownerInstanceId or ''}",
            List.class
    );

    private static final RedisScript<Long> RENEW_LEASE_SCRIPT = RedisScript.of(
            "local userId = redis.call('GET', KEYS[1])\n" +
                    "local stockCode = redis.call('GET', KEYS[2])\n" +
                    "local ownerInstanceId = redis.call('GET', KEYS[3])\n" +
                    "if not userId or not stockCode or not ownerInstanceId then\n" +
                    "  return 0\n" +
                    "end\n" +
                    "redis.call('EXPIRE', KEYS[1], ARGV[1])\n" +
                    "redis.call('EXPIRE', KEYS[2], ARGV[1])\n" +
                    "redis.call('EXPIRE', KEYS[3], ARGV[1])\n" +
                    "redis.call('SET', KEYS[4], stockCode, 'EX', ARGV[1])\n" +
                    "return 1",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public boolean saveIfWithinActiveStockLimit(
            RealtimeOrderbookSubscription subscription,
            int maxActiveStockCount
    ) {
        validateSubscription(subscription);
        if (maxActiveStockCount < 1) {
            throw new IllegalArgumentException("maxActiveStockCount must be greater than 0");
        }

        Long result = redisTemplate.execute(
                SAVE_SCRIPT,
                List.of(
                        streamUserKey(subscription.streamId()),
                        streamStockKey(subscription.streamId()),
                        streamOwnerKey(subscription.streamId()),
                        leaseKey(subscription.streamId()),
                        userStreamsKey(subscription.userId()),
                        stockStreamsKey(subscription.stockCode()),
                        ACTIVE_STOCKS_KEY
                ),
                String.valueOf(subscription.userId()),
                subscription.stockCode(),
                subscription.ownerInstanceId(),
                subscription.streamId(),
                String.valueOf(STREAM_LEASE_TTL.toSeconds()),
                String.valueOf(maxActiveStockCount),
                LEASE_KEY_PREFIX,
                STOCK_STREAMS_KEY_PREFIX,
                STREAMS_KEY_SUFFIX
        );

        if (Long.valueOf(0L).equals(result)) {
            return false;
        }

        if (Long.valueOf(1L).equals(result)) {
            return true;
        }

        throw new IllegalStateException("실시간 호가 구독 상태 저장에 실패했습니다.");
    }

    public Optional<RealtimeOrderbookSubscription> findByStreamId(String streamId) {
        validateStreamId(streamId);

        String leasedStockCode = redisTemplate.opsForValue().get(leaseKey(streamId));
        if (!StringUtils.hasText(leasedStockCode)) {
            return Optional.empty();
        }

        String userId = redisTemplate.opsForValue().get(streamUserKey(streamId));
        String stockCode = redisTemplate.opsForValue().get(streamStockKey(streamId));
        String ownerInstanceId = redisTemplate.opsForValue().get(streamOwnerKey(streamId));

        if (!StringUtils.hasText(userId)
                || !leasedStockCode.equals(stockCode)
                || !StringUtils.hasText(ownerInstanceId)) {
            return Optional.empty();
        }

        return Optional.of(new RealtimeOrderbookSubscription(
                streamId,
                Long.valueOf(userId),
                stockCode,
                ownerInstanceId
        ));
    }

    public Optional<RealtimeOrderbookSubscription> deleteByStreamId(String streamId) {
        validateStreamId(streamId);

        List<?> result = redisTemplate.execute(
                DELETE_SCRIPT,
                List.of(
                        streamUserKey(streamId),
                        streamStockKey(streamId),
                        streamOwnerKey(streamId),
                        leaseKey(streamId),
                        ACTIVE_STOCKS_KEY
                ),
                USER_STREAMS_KEY_PREFIX,
                STOCK_STREAMS_KEY_PREFIX,
                STREAMS_KEY_SUFFIX,
                streamId,
                LEASE_KEY_PREFIX
        );

        return toSubscription(streamId, result);
    }

    public Optional<RealtimeOrderbookSubscription> deleteByStreamIdAndUserId(String streamId, Long userId) {
        validateStreamId(streamId);
        Objects.requireNonNull(userId, "userId must not be null");

        List<?> result = redisTemplate.execute(
                DELETE_IF_OWNER_SCRIPT,
                List.of(
                        streamUserKey(streamId),
                        streamStockKey(streamId),
                        streamOwnerKey(streamId),
                        leaseKey(streamId),
                        userStreamsKey(userId),
                        ACTIVE_STOCKS_KEY
                ),
                String.valueOf(userId),
                STOCK_STREAMS_KEY_PREFIX,
                streamId,
                STREAMS_KEY_SUFFIX,
                LEASE_KEY_PREFIX
        );

        return toSubscription(streamId, result);
    }

    public boolean renewLease(String streamId) {
        validateStreamId(streamId);

        Long result = redisTemplate.execute(
                RENEW_LEASE_SCRIPT,
                streamKeys(streamId),
                String.valueOf(STREAM_LEASE_TTL.toSeconds())
        );

        return Long.valueOf(1L).equals(result);
    }

    public Set<String> findActiveStreamIdsByUserId(Long userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        Set<String> streamIds = redisTemplate.opsForSet().members(userStreamsKey(userId));
        if (streamIds == null || streamIds.isEmpty()) {
            return Set.of();
        }

        Set<String> activeStreamIds = new HashSet<>();
        for (String streamId : streamIds) {
            Optional<RealtimeOrderbookSubscription> subscription = findByStreamId(streamId);
            if (subscription.isPresent() && subscription.get().userId().equals(userId)) {
                activeStreamIds.add(streamId);
            } else {
                redisTemplate.opsForSet().remove(userStreamsKey(userId), streamId);
            }
        }

        return Set.copyOf(activeStreamIds);
    }

    public long countActiveStreamsByStockCode(String stockCode) {
        return refreshActiveStreamIdsByStockCode(stockCode).size();
    }

    public Set<String> findActiveStockCodes() {
        Set<String> stockCodesToRefresh = new HashSet<>();
        Set<String> indexedActiveStockCodes = redisTemplate.opsForSet().members(ACTIVE_STOCKS_KEY);
        if (indexedActiveStockCodes != null) {
            stockCodesToRefresh.addAll(indexedActiveStockCodes);
        }

        ScanOptions options = ScanOptions.scanOptions()
                .match(STOCK_STREAMS_KEY_PREFIX + "*" + STREAMS_KEY_SUFFIX)
                .count(1000)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                parseStockCodeFromStockStreamsKey(key)
                        .ifPresent(stockCodesToRefresh::add);
            }
        }

        stockCodesToRefresh.forEach(this::refreshActiveStreamIdsByStockCode);

        Set<String> activeStockCodes = redisTemplate.opsForSet().members(ACTIVE_STOCKS_KEY);
        if (activeStockCodes == null || activeStockCodes.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(activeStockCodes);
    }

    public long leaseTtlSeconds() {
        return STREAM_LEASE_TTL.toSeconds();
    }

    private Optional<RealtimeOrderbookSubscription> toSubscription(String streamId, List<?> result) {
        if (result == null || result.size() < 2) {
            return Optional.empty();
        }

        String userId = String.valueOf(result.get(0));
        String stockCode = String.valueOf(result.get(1));
        String ownerInstanceId = result.size() >= 3 ? String.valueOf(result.get(2)) : "";

        if (!StringUtils.hasText(userId) || !StringUtils.hasText(stockCode)) {
            return Optional.empty();
        }

        return Optional.of(new RealtimeOrderbookSubscription(
                streamId,
                Long.valueOf(userId),
                stockCode,
                ownerInstanceId
        ));
    }

    private Set<String> refreshActiveStreamIdsByStockCode(String stockCode) {
        validateStockCode(stockCode);

        Set<String> streamIds = redisTemplate.opsForSet().members(stockStreamsKey(stockCode));
        if (streamIds == null || streamIds.isEmpty()) {
            redisTemplate.opsForSet().remove(ACTIVE_STOCKS_KEY, stockCode);
            return Set.of();
        }

        Set<String> activeStreamIds = new HashSet<>();
        for (String streamId : streamIds) {
            String leasedStockCode = redisTemplate.opsForValue().get(leaseKey(streamId));
            if (stockCode.equals(leasedStockCode)) {
                activeStreamIds.add(streamId);
            } else {
                redisTemplate.opsForSet().remove(stockStreamsKey(stockCode), streamId);
            }
        }

        if (activeStreamIds.isEmpty()) {
            redisTemplate.opsForSet().remove(ACTIVE_STOCKS_KEY, stockCode);
        } else {
            redisTemplate.opsForSet().add(ACTIVE_STOCKS_KEY, stockCode);
        }

        return Set.copyOf(activeStreamIds);
    }

    private Optional<String> parseStockCodeFromStockStreamsKey(String key) {
        if (!key.startsWith(STOCK_STREAMS_KEY_PREFIX) || !key.endsWith(STREAMS_KEY_SUFFIX)) {
            return Optional.empty();
        }

        String stockCode = key.substring(
                STOCK_STREAMS_KEY_PREFIX.length(),
                key.length() - STREAMS_KEY_SUFFIX.length()
        );
        return StringUtils.hasText(stockCode) ? Optional.of(stockCode) : Optional.empty();
    }

    private List<String> streamKeys(String streamId) {
        return List.of(
                streamUserKey(streamId),
                streamStockKey(streamId),
                streamOwnerKey(streamId),
                leaseKey(streamId)
        );
    }

    private void validateSubscription(RealtimeOrderbookSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription must not be null");
        validateStreamId(subscription.streamId());
        Objects.requireNonNull(subscription.userId(), "userId must not be null");
        validateStockCode(subscription.stockCode());
        if (!StringUtils.hasText(subscription.ownerInstanceId())) {
            throw new IllegalArgumentException("ownerInstanceId must not be blank");
        }
    }

    private void validateStreamId(String streamId) {
        if (!StringUtils.hasText(streamId)) {
            throw new IllegalArgumentException("streamId must not be blank");
        }
    }

    private void validateStockCode(String stockCode) {
        if (!StringUtils.hasText(stockCode)) {
            throw new IllegalArgumentException("stockCode must not be blank");
        }
    }

    private String streamUserKey(String streamId) {
        return STREAM_KEY_PREFIX + streamId + STREAM_USER_KEY_SUFFIX;
    }

    private String streamStockKey(String streamId) {
        return STREAM_KEY_PREFIX + streamId + STREAM_STOCK_KEY_SUFFIX;
    }

    private String streamOwnerKey(String streamId) {
        return STREAM_KEY_PREFIX + streamId + STREAM_OWNER_KEY_SUFFIX;
    }

    private String leaseKey(String streamId) {
        return LEASE_KEY_PREFIX + streamId;
    }

    private String userStreamsKey(Long userId) {
        return USER_STREAMS_KEY_PREFIX + userId + STREAMS_KEY_SUFFIX;
    }

    private String stockStreamsKey(String stockCode) {
        return STOCK_STREAMS_KEY_PREFIX + stockCode + STREAMS_KEY_SUFFIX;
    }
}
