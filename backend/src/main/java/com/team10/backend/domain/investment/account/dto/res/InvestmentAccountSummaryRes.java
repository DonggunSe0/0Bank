package com.team10.backend.domain.investment.account.dto.res;

import com.team10.backend.domain.investment.account.entity.InvestmentAccount;
import com.team10.backend.domain.investment.account.type.InvestmentAccountStatus;
import com.team10.backend.domain.investment.type.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

@Schema(description = "투자 계좌 요약 응답")
public record InvestmentAccountSummaryRes(

        @Schema(description = "투자 계좌번호")
        String accountNumber,

        @Schema(description = "투자 계좌 별칭")
        String nickname,

        @Schema(description = "예수금")
        Long cashBalance,

        @Schema(description = "통화")
        CurrencyCode currencyCode,

        @Schema(description = "투자 계좌 상태")
        InvestmentAccountStatus status
) {

    public static InvestmentAccountSummaryRes from(InvestmentAccount account) {
        Objects.requireNonNull(account, "account는 null일 수 없습니다.");

        return new InvestmentAccountSummaryRes(
                account.getAccountNumber(),
                account.getNickname(),
                account.getCashBalance(),
                account.getCurrencyCode(),
                account.getStatus()
        );
    }
}
