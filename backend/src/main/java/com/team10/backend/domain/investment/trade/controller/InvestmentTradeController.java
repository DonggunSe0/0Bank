package com.team10.backend.domain.investment.trade.controller;

import com.team10.backend.domain.investment.trade.dto.req.MarketOrderCreateReq;
import com.team10.backend.domain.investment.trade.dto.res.InvestmentTradeRes;
import com.team10.backend.domain.investment.trade.service.InvestmentTradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/investment/trades")
@Tag(name = "Investment Trade", description = "주식 거래 API")
public class InvestmentTradeController {

    private final InvestmentTradeService investmentTradeService;

    @Operation(summary = "주식 시장가 즉시 체결 주문", description = "실시간 호가 스냅샷 기준으로 시장가 주문을 전량 체결합니다.")
    @PostMapping("/market-orders")
    public ResponseEntity<InvestmentTradeRes> createMarketOrder(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long userId,

            @RequestHeader("Idempotency-Key") String idempotencyKey,

            @Valid @RequestBody MarketOrderCreateReq request
    ) {
        InvestmentTradeRes response = investmentTradeService.createMarketOrder(userId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
