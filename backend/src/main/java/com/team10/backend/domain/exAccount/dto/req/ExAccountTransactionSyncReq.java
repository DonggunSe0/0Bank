package com.team10.backend.domain.exAccount.dto.req;

import com.team10.backend.domain.exAccount.Type.ExAccountTransactionDirection;
import com.team10.backend.domain.exAccount.entity.ExAccount;
import com.team10.backend.domain.exAccount.entity.ExAccountTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "외부 계좌 거래내역 동기화 항목. 거래내역 새로고침 요청에서 사용합니다.")
public record ExAccountTransactionSyncReq(
        @Schema(description = "외부기관 거래 고유키. 같은 외부 계좌 안에서 중복 여부를 판단하는 기준입니다.", example = "KB-20260618143000-0001", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "거래 고유키는 필수입니다.")
        @Size(max = 160, message = "거래 고유키는 160자 이하여야 합니다.")
        String transactionKey,

        @Schema(description = "거래 일시", example = "2026-06-18T14:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "거래 일시는 필수입니다.")
        LocalDateTime transactedAt,

        @Schema(description = "입출금 방향", example = "OUT", allowableValues = {"IN", "OUT"}, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "입출금 방향은 필수입니다.")
        ExAccountTransactionDirection direction,

        @Schema(description = "거래 금액", example = "45000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "거래 금액은 필수입니다.")
        BigDecimal amount,

        @Schema(description = "거래 후 잔액", example = "1455000.00", nullable = true)
        BigDecimal balanceAfter,

        @Schema(description = "거래 상대명", example = "스타벅스", nullable = true)
        @Size(max = 100, message = "거래 상대명은 100자 이하여야 합니다.")
        String counterpartyName,

        @Schema(description = "거래 메모", example = "카드 결제", nullable = true)
        @Size(max = 255, message = "거래 메모는 255자 이하여야 합니다.")
        String memo,

        @Schema(description = "외부기관 원본 거래 카테고리", example = "식비", nullable = true)
        @Size(max = 80, message = "원본 거래 카테고리는 80자 이하여야 합니다.")
        String rawCategory
) {
    public ExAccountTransaction toEntity(ExAccount account) {
        return ExAccountTransaction.create(
                account,
                transactionKey,
                transactedAt,
                direction,
                amount,
                balanceAfter,
                counterpartyName,
                memo,
                rawCategory
        );
    }

    public void applyTo(ExAccountTransaction transaction) {
        transaction.updateSnapshot(
                transactedAt,
                direction,
                amount,
                balanceAfter,
                counterpartyName,
                memo,
                rawCategory
        );
    }
}
