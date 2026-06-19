package com.team10.backend.domain.exAccount.dto.req;

import com.team10.backend.domain.exAccount.Type.ExAccountType;
import com.team10.backend.domain.exAccount.entity.ExAccount;
import com.team10.backend.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "외부 계좌 연동 요청. 후보 조회와 연동 저장 요청에서 공통으로 사용하는 외부 계좌 스냅샷입니다.")
public record ExAccountLinkReq(
        @Schema(description = "외부 금융기관명 또는 기관 코드", example = "국민은행", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "외부 금융기관은 필수입니다.")
        @Size(max = 20, message = "외부 금융기관은 20자 이하여야 합니다.")
        String organization,

        @Schema(description = "외부 계좌번호. 같은 사용자, 기관, 계좌번호 조합으로 중복 연동 여부를 판단합니다.", example = "12345678901234", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "외부 계좌번호는 필수입니다.")
        @Size(max = 80, message = "외부 계좌번호는 80자 이하여야 합니다.")
        String accountNumber,

        @Schema(description = "외부 계좌명", example = "KB Star 입출금통장", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "외부 계좌명은 필수입니다.")
        @Size(max = 100, message = "외부 계좌명은 100자 이하여야 합니다.")
        String accountName,

        @Schema(description = "외부 계좌 별칭", example = "생활비 통장", nullable = true)
        @Size(max = 100, message = "외부 계좌 별칭은 100자 이하여야 합니다.")
        String accountAlias,

        @Schema(description = "외부 계좌 유형", example = "DEMAND", allowableValues = {
                "DEMAND", "SAVING", "LOAN", "FUND", "FX", "INSURANCE", "UNKNOWN"
        }, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "외부 계좌 유형은 필수입니다.")
        ExAccountType assetType,

        @Schema(description = "외부 계좌 잔액. null이면 저장 시 0원으로 처리합니다.", example = "1500000.00")
        BigDecimal balance,

        @Schema(description = "출금 가능 금액", example = "1200000.00", nullable = true)
        BigDecimal withdrawableAmount,

        @Schema(description = "외부 계좌 개설일", example = "2024-01-15", nullable = true)
        LocalDate openedAt,

        @Schema(description = "외부 계좌 만기일", example = "2027-01-15", nullable = true)
        LocalDate maturityAt,

        @Schema(description = "마지막 거래일", example = "2026-06-18", nullable = true)
        LocalDate lastTransactionAt
) {
    public ExAccount toEntity(User user) {
        return ExAccount.create(
                user,
                organization,
                accountNumber,
                accountName,
                accountAlias,
                assetType,
                balance,
                withdrawableAmount,
                openedAt,
                maturityAt,
                lastTransactionAt
        );
    }

    public void applyTo(ExAccount account) {
        account.updateSnapshot(
                accountName,
                accountAlias,
                balance,
                withdrawableAmount,
                maturityAt,
                lastTransactionAt
        );
    }
}
