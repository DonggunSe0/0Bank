package com.team10.backend.domain.investment.realtime.config;

public final class RealtimeOrderbookSseConstants {

    public static final String STREAM_CREATED_EVENT_NAME = "stream-created";
    public static final String ORDERBOOK_UPDATED_EVENT_NAME = "orderbook-updated";
    public static final String HEARTBEAT_COMMENT = "heartbeat";

    private RealtimeOrderbookSseConstants() {
    }
}
