package com.team10.backend.domain.exAccount.dto.res;

import com.team10.backend.domain.exAccount.type.ExAccountStatus;
import com.team10.backend.domain.exAccount.type.ExAccountType;
import com.team10.backend.domain.exAccount.entity.ExAccount;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "외부 계좌 응답")
public record ExAccountRes(
        @Schema(description = "외부 계좌 ID", example = "1")
        Long id,
        @Schema(description = "외부 금융기관명", example = "국민은행")
        String organization,
        @Schema(description = "마스킹된 외부 계좌번호", example = "123456******7890")
        String accountNoMasked,
        @Schema(description = "외부 계좌명", example = "KB Star 입출금통장")
        String accountName,
        @Schema(description = "외부 계좌 별칭", example = "생활비 통장", nullable = true)
        String accountAlias,
        @Schema(description = "외부 계좌 유형", example = "DEMAND", allowableValues = {
                "DEMAND", "SAVING", "LOAN", "FUND", "FX", "INSURANCE", "UNKNOWN"
        })
        ExAccountType assetType,
        @Schema(description = "외부 계좌 잔액", example = "1500000.00")
        BigDecimal balance,
        @Schema(description = "출금 가능 금액", example = "1200000.00", nullable = true)
        BigDecimal withdrawableAmount,
        @Schema(description = "외부 계좌 개설일", example = "2024-01-15", nullable = true)
        LocalDate openedAt,
        @Schema(description = "외부 계좌 만기일", example = "2027-01-15", nullable = true)
        LocalDate maturityAt,
        @Schema(description = "마지막 거래일", example = "2026-06-18", nullable = true)
        LocalDate lastTransactionAt,
        @Schema(description = "외부 계좌 상태", example = "ACTIVE", allowableValues = {"ACTIVE", "CLOSED", "UNKNOWN"})
        ExAccountStatus status
) {
    public static ExAccountRes from(ExAccount account) {
        return new ExAccountRes(
                account.getId(),
                account.getOrganization(),
                account.getAccountNoMasked(),
                account.getAccountName(),
                account.getAccountAlias(),
                account.getAssetType(),
                account.getBalance(),
                account.getWithdrawableAmount(),
                account.getOpenedAt(),
                account.getMaturityAt(),
                account.getLastTransactionAt(),
                account.getStatus()
        );
    }
}
