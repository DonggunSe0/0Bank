package com.team10.backend.domain.investment.realtime.dto;

import java.util.List;

/**
 * KIS에서 수신한 한 종목의 실시간 호가 스냅샷.
 */
public record RealtimeOrderbookSnapshot(
        String stockCode,
        String businessTime,
        String timeType,
        List<RealtimeOrderbookLevel> asks, // 매도 호가
        List<RealtimeOrderbookLevel> bids, // 매수 호가
        Long totalAskQuantity,
        Long totalBidQuantity
) {
}
