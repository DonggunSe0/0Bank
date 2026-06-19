package com.team10.backend.domain.investment.account.dto.res;

import com.team10.backend.domain.investment.account.entity.InvestmentAccount;
import com.team10.backend.domain.investment.account.type.InvestmentAccountStatus;
import com.team10.backend.domain.investment.type.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Objects;

@Schema(description = "투자 계좌 개설 성공에 대한 응답.")
public record InvestmentAccountCreateRes(

        @Schema(description = "투자 계좌 ID")
        Long id,

        @Schema(description = "투자 계좌번호")
        String accountNumber,

        @Schema(description = "투자 계좌 별칭")
        String nickname,

        @Schema(description = "예수금")
        Long cashBalance,

        @Schema(description = "통화")
        CurrencyCode currencyCode,

        @Schema(description = "투자 계좌 상태", example = "ACTIVE")
        InvestmentAccountStatus status,

        @Schema(description = "투자 계좌 생성 일시", example = "2026-06-17T10:30:00")
        LocalDateTime createdAt
) {

    public static InvestmentAccountCreateRes from(InvestmentAccount account) {
        Objects.requireNonNull(account, "account는 null일 수 없습니다.");

        return new InvestmentAccountCreateRes(
                account.getId(),
                account.getAccountNumber(),
                account.getNickname(),
                account.getCashBalance(),
                account.getCurrencyCode(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}
