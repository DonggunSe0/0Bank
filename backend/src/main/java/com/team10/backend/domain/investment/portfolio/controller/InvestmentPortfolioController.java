package com.team10.backend.domain.investment.portfolio.controller;

import com.team10.backend.domain.investment.portfolio.dto.res.InvestmentHoldingRes;
import com.team10.backend.domain.investment.portfolio.service.InvestmentPortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/investment/accounts/{accountId}/holdings")
@Tag(name = "Investment Portfolio", description = "투자 포트폴리오 API")
public class InvestmentPortfolioController {

    private final InvestmentPortfolioService investmentPortfolioService;

    @Operation(summary = "투자 계좌 보유 종목 조회", description = "인증 사용자의 특정 투자 계좌가 보유 중인 종목 목록을 페이지 조회합니다.")
    @GetMapping
    public ResponseEntity<Page<InvestmentHoldingRes>> getHoldings(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long userId,

            @PathVariable Long accountId,

            @Parameter(description = "페이지 번호(0부터 시작)")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(investmentPortfolioService.getHoldings(userId, accountId, page, size));
    }
}
