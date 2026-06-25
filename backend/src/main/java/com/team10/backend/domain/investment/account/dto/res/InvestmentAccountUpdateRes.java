package com.team10.backend.domain.investment.account.dto.res;

import com.team10.backend.domain.investment.account.entity.InvestmentAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Objects;

@Schema(description = "투자 계좌 정보 수정 응답")
public record InvestmentAccountUpdateRes(
        @Schema(description = "투자 계좌 별칭", example = "장기투자 계좌", nullable = true)
        String nickname,

        @Schema(description = "투자 계좌 수정 일시", example = "2026-06-17T11:00:00")
        LocalDateTime updatedAt
) {

    public static InvestmentAccountUpdateRes from(InvestmentAccount account) {
        Objects.requireNonNull(account, "account는 null일 수 없습니다.");

        return new InvestmentAccountUpdateRes(
                account.getNickname(),
                account.getUpdatedAt()
        );
    }
}
