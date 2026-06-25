package com.team10.backend.domain.exAccount.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "외부 계좌 거래내역 새로고침 응답")
public record ExAccountTransactionRefreshRes(
        @Schema(description = "요청 거래내역 수", example = "20")
        int requestedCount,

        @Schema(description = "신규 저장된 거래내역 수", example = "5")
        int createdCount,

        @Schema(description = "갱신된 거래내역 수", example = "15")
        int updatedCount,

        @Schema(description = "새로고침 후 외부 계좌 상세 정보와 거래내역")
        ExAccountDetailRes detail
) {
    public static ExAccountTransactionRefreshRes of(
            int requestedCount,
            int createdCount,
            int updatedCount,
            ExAccountDetailRes detail
    ) {
        return new ExAccountTransactionRefreshRes(requestedCount, createdCount, updatedCount, detail);
    }
}
