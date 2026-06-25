package com.team10.backend.domain.exAccount.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "외부 계좌 거래내역 새로고침 요청")
public record ExAccountTransactionRefreshReq(
        @Schema(
                description = "외부기관에서 새로 조회된 거래내역 목록. transactionKey 기준으로 신규 저장 또는 기존 거래 갱신을 수행합니다.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @Valid
        @NotEmpty(message = "새로고침할 거래내역 목록은 필수입니다.")
        List<ExAccountTransactionSyncReq> transactions
) {
}
