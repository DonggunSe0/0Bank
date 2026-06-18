package com.team10.backend.domain.exchange.dto.res;

import com.team10.backend.domain.exchange.entity.FxWallet;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import com.team10.backend.domain.exchange.type.FxWalletStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "외화 지갑 응답")
public record FxWalletRes(
        @Schema(description = "외화 지갑 ID", example = "1")
        Long walletId,

        @Schema(description = "외화 지갑 통화 코드", example = "USD")
        CurrencyCode currencyCode,

        @Schema(description = "외화 지갑 잔액", example = "72.50")
        BigDecimal balance,

        @Schema(description = "외화 지갑 상태", example = "ACTIVE")
        FxWalletStatus status,

        @Schema(description = "외화 지갑 생성 시각", example = "2026-06-17T10:00:00")
        LocalDateTime createdAt,

        @Schema(description = "외화 지갑 수정 시각", example = "2026-06-17T10:00:00")
        LocalDateTime updatedAt
) {

    public static FxWalletRes from(FxWallet fxWallet) {
        return new FxWalletRes(
                fxWallet.getId(),
                fxWallet.getCurrency().getCurrencyCode(),
                fxWallet.getBalance(),
                fxWallet.getStatus(),
                fxWallet.getCreatedAt(),
                fxWallet.getUpdatedAt()
        );
    }
}
