package com.team10.backend.domain.exAccount.controller;

import com.team10.backend.domain.exAccount.dto.res.ExAccountTransactionRes;
import com.team10.backend.domain.exAccount.service.ExAccountTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "External Account Transaction", description = "외부 계좌 거래내역 API")
public class ExAccountTransactionController {
    private final ExAccountTransactionService exAccountTransactionService;

    @Operation(
            summary = "외부 계좌 전체 거래내역 조회",
            description = "인증 사용자가 연동한 모든 외부 계좌의 거래내역을 최신순으로 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "외부 계좌 전체 거래내역 조회 성공")
    @GetMapping("/transactions")
    public ResponseEntity<List<ExAccountTransactionRes>> getTransactions(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(exAccountTransactionService.getTransactions(userId));
    }
}
