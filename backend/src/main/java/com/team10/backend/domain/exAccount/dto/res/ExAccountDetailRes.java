package com.team10.backend.domain.exAccount.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "외부 계좌 상세 및 거래내역 응답")
public record ExAccountDetailRes(
        @Schema(description = "선택한 외부 계좌 상세 정보")
        ExAccountRes account,

        @Schema(description = "선택한 외부 계좌의 거래내역 목록. 최신 거래일시 순으로 정렬됩니다.")
        List<ExAccountTransactionRes> transactions
) {
    public static ExAccountDetailRes of(ExAccountRes account, List<ExAccountTransactionRes> transactions) {
        return new ExAccountDetailRes(account, transactions);
    }
}
