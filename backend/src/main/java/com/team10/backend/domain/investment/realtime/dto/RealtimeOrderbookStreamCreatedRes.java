package com.team10.backend.domain.investment.realtime.dto;

public record RealtimeOrderbookStreamCreatedRes(
        String streamId,
        String stockCode
) {
}
