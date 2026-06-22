package com.team10.backend.domain.exchange.controller;

import com.team10.backend.domain.exchange.dto.req.ExchangeOrderCreateReq;
import com.team10.backend.domain.exchange.dto.req.ExchangeQuoteCreateReq;
import com.team10.backend.domain.exchange.dto.res.CurrencyRes;
import com.team10.backend.domain.exchange.dto.res.ExchangeOrderRes;
import com.team10.backend.domain.exchange.dto.res.ExchangeQuoteRes;
import com.team10.backend.domain.exchange.dto.res.ExchangeRateRes;
import com.team10.backend.domain.exchange.service.ExchangeRateService;
import com.team10.backend.domain.exchange.service.ExchangeService;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exchanges")
@Tag(name = "ExchangeController", description = "환전 API")
@RequiredArgsConstructor
public class ExchangeController {

    private final ExchangeRateService exchangeRateService;
    private final ExchangeService exchangeService;

    @GetMapping("/currencies")
    @Operation(description = "지원 통화 목록 조회")
    public ResponseEntity<List<CurrencyRes>> getCurrencies() {
        List<CurrencyRes> response = exchangeRateService.getCurrencies();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rates")
    @Operation(description = "현재 환율 목록 조회")
    public ResponseEntity<List<ExchangeRateRes>> getExchangeRates() {
        return ResponseEntity.ok(exchangeRateService.getLatestRates());
    }

    @GetMapping("/currencies/{currencyCode}")
    @Operation(description = "특정 통화 환율 조회")
    public ResponseEntity<ExchangeRateRes> getExchangeRate(
            @PathVariable CurrencyCode currencyCode
            ) {
        return ResponseEntity.ok(exchangeRateService.getLatestRate(currencyCode));
    }

    @PostMapping("/currencies/quotes")
    @Operation(description = "환전 견적 생성")
    public ResponseEntity<ExchangeQuoteRes> createExchangeQuote(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ExchangeQuoteCreateReq request
    ) {
        ExchangeQuoteRes response = exchangeService.createQuote(
                userId,
                request.fromCurrencyCode(),
                request.toCurrencyCode(),
                request.fromAmount());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/currencies/orders")
    @Operation(description = "환전 주문 실행")
    public ResponseEntity<ExchangeOrderRes> createExchangeOrder(
            @AuthenticationPrincipal Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ExchangeOrderCreateReq request
    ) {
        ExchangeOrderRes response = exchangeService.createExchangeOrder(
                userId,
                idempotencyKey,
                request.exchangeQuoteId(),
                request.krwAccountId(),
                request.fxWalletId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/currencies/orders")
    @Operation(description = "내 환전 주문 목록 조회")
    public ResponseEntity<List<ExchangeOrderRes>> getExchangeOrders(
            @AuthenticationPrincipal Long userId
    ) {
        List<ExchangeOrderRes> response = exchangeService.getExchangeOrders(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/currencies/orders/{exchangeOrderId}")
    @Operation(description = "환전 주문 상세 조회")
    public ResponseEntity<ExchangeOrderRes> getExchangeOrder(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long exchangeOrderId
    ) {
        ExchangeOrderRes response = exchangeService.getExchangeOrder(userId, exchangeOrderId);
        return ResponseEntity.ok(response);
    }

}
