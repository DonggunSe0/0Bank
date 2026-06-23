package com.team10.backend.domain.investment.realtime.dto;

import java.time.Instant;

public record RealtimeOrderbookPriceSnapshot(
        String stockCode,
        Long asks,
        Long bids,
        Instant receivedAt
) {
    public Long bestAskPrice() {
        return asks;
    }

    public Long bestBidPrice() {
        return bids;
    }
}
