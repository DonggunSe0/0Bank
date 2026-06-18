package com.team10.backend.domain.investment.account.dto.res;

import com.team10.backend.domain.investment.account.entity.InvestmentAccount;
import com.team10.backend.domain.investment.account.type.InvestmentAccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Objects;

@Schema(description = "투자 계좌 해지 응답")
public record InvestmentAccountCloseRes(
        @Schema(description = "투자 계좌 상태", example = "CLOSED")
        InvestmentAccountStatus status,

        @Schema(description = "투자 계좌 수정 일시", example = "2026-06-17T11:00:00")
        LocalDateTime updatedAt
) {

    public static InvestmentAccountCloseRes from(InvestmentAccount account) {
        Objects.requireNonNull(account, "account는 null일 수 없습니다.");

        return new InvestmentAccountCloseRes(
                account.getStatus(),
                account.getUpdatedAt()
        );
    }
}
