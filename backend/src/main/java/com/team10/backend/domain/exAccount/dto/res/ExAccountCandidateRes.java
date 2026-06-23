package com.team10.backend.domain.exAccount.dto.res;

import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountSnapshot;
import com.team10.backend.domain.exAccount.dto.req.ExAccountLinkReq;
import com.team10.backend.domain.exAccount.type.ExAccountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "외부 계좌 조회 후보 응답")
public record ExAccountCandidateRes(
        @Schema(description = "계좌 후보 인덱스 번호 (0부터 시작)", example = "0")
        int index,
        @Schema(description = "외부 금융기관명 또는 기관 코드", example = "국민은행")
        String organization,
        @Schema(description = "마스킹된 외부 계좌번호", example = "123456******7890")
        String accountNoMasked,
        @Schema(description = "외부 계좌명", example = "KB Star 입출금통장")
        String accountName,
        @Schema(description = "외부 계좌 별칭", example = "생활비 통장", nullable = true)
        String accountAlias,
        @Schema(description = "외부 계좌 유형", example = "DEMAND")
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
        @Schema(description = "이미 연동된 외부 계좌 여부", example = "false")
        boolean linked
) {
    public static ExAccountCandidateRes from(
            int index,
            CodefExAccountSnapshot snapshot,
            String accountNumberMasked,
            boolean linked
    ) {
        return new ExAccountCandidateRes(
                index,
                snapshot.organization(),
                accountNumberMasked,
                snapshot.accountName(),
                snapshot.accountAlias(),
                snapshot.assetType(),
                snapshot.balance(),
                snapshot.withdrawableAmount(),
                snapshot.openedAt(),
                snapshot.maturityAt(),
                snapshot.lastTransactionAt(),
                linked
        );
    }

}
