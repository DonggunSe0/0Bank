package com.team10.backend.domain.transaction.dto.req;

import com.team10.backend.domain.transaction.type.TransactionDirection;
import com.team10.backend.global.validation.annotation.ValidDateRange;
import com.team10.backend.global.validation.annotation.ValidNumberRange;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "거래내역 검색 요청")
@ValidDateRange(start = "startDate", end = "endDate")
@ValidNumberRange(min = "minAmount", max = "maxAmount")
public record TransactionHistorySearchReq(
        // 기간 범위
        @Schema(description = "검색 시작일", nullable = true)
        LocalDate startDate,

        @Schema(description = "검색 종료일", nullable = true)
        LocalDate endDate,

        // 입 , 출금
        @Schema(description = "입출금 방향", allowableValues = {"IN", "OUT"}, nullable = true)
        TransactionDirection direction,

        // 거래액 범위
        @Schema(description = "최소 거래 금액", nullable = true)
        @PositiveOrZero Long minAmount,

        @Schema(description = "최대 거래 금액", nullable = true)
        @PositiveOrZero Long maxAmount,

        // 거래 상대
        @Schema(description = "거래 상대명", nullable = true)
        @Size(max = 30) String counterpartyName
) {
}
