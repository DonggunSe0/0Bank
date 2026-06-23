package com.team10.backend.domain.transaction.controller;

import com.team10.backend.domain.transaction.dto.req.TransactionHistorySearchReq;
import com.team10.backend.domain.transaction.dto.res.TransactionHistoryDetailRes;
import com.team10.backend.domain.transaction.dto.res.TransactionHistorySearchRes;
import com.team10.backend.domain.transaction.service.TransactionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/transactions")
@RequiredArgsConstructor
@Validated
@Tag(name = "TransactionHistory", description = "거래내역 API")
public class TransactionHistoryController {

    private final TransactionHistoryService transactionHistoryService;

    // 거래 내역 다건 조회 [ 필터링 & 페이징 ]
    @GetMapping
    @Operation(
            summary = "거래내역 목록 조회",
            description = "인증 사용자의 특정 계좌 거래내역을 기간, 입출금 방향, 거래 금액, 거래 상대 조건으로 조회합니다."
    )
    public ResponseEntity<Page<TransactionHistorySearchRes>> getTransactionHistories(
            @Parameter(description = "계좌 ID")
            @PathVariable @Positive Long accountId,

            @Parameter(hidden = true)
            @AuthenticationPrincipal Long userId,

            @Valid @ModelAttribute TransactionHistorySearchReq filter,

            @Parameter(description = "페이지 번호(0부터 시작)", required = false)
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "거래일시 정렬 방향", required = false)
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection
    ) {
        return ResponseEntity.ok(
                transactionHistoryService.getTransactionHistories(accountId, userId, filter, page, sortDirection)
        );
    }

    // 거래 내역 단건 조회 [ 상세 정보 ]
    @GetMapping("/{transactionId}")
    @Operation(
            summary = "거래내역 상세 조회",
            description = "인증 사용자의 특정 계좌에 속한 단건 거래내역 상세 정보를 조회합니다."
    )
    public ResponseEntity<TransactionHistoryDetailRes> getTransactionHistoryDetail(
            @Parameter(description = "계좌 ID")
            @PathVariable @Positive Long accountId,

            @Parameter(description = "거래내역 ID")
            @PathVariable @Positive Long transactionId,

            @Parameter(hidden = true) @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(
                transactionHistoryService.getTransactionHistoryDetail(accountId, transactionId, userId)
        );
    }

}
