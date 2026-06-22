package com.team10.backend.domain.investment.realtime.config;

import java.time.Duration;

public final class RealtimeOrderbookRedisConstants {

    public static final String KEY_PREFIX = "kis:orderbook:";
    public static final String ALL_KEYS_PATTERN = KEY_PREFIX + "*";

    public static final String STREAM_KEY_PREFIX = KEY_PREFIX + "stream:";
    public static final String STREAM_USER_KEY_SUFFIX = ":user";
    public static final String STREAM_STOCK_KEY_SUFFIX = ":stock";
    public static final String STREAM_OWNER_KEY_SUFFIX = ":owner";

    public static final String LEASE_KEY_PREFIX = KEY_PREFIX + "lease:";
    public static final Duration STREAM_LEASE_TTL = Duration.ofSeconds(90);

    public static final String LEADER_KEY = KEY_PREFIX + "leader";
    public static final Duration LEADER_LEASE_TTL = Duration.ofSeconds(30);

    public static final String STOCK_STREAMS_KEY_PREFIX = KEY_PREFIX + "stock:";
    public static final String USER_STREAMS_KEY_PREFIX = KEY_PREFIX + "user:";
    public static final String STREAMS_KEY_SUFFIX = ":streams";
    public static final String ACTIVE_STOCKS_KEY = KEY_PREFIX + "active-stocks";

    public static final String SUBSCRIPTION_CHANGED_CHANNEL = KEY_PREFIX + "subscription-changed";
    public static final String ORDERBOOK_UPDATED_CHANNEL = KEY_PREFIX + "orderbook-updated";

    private RealtimeOrderbookRedisConstants() {
    }
}
