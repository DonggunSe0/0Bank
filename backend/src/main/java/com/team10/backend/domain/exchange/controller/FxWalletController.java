package com.team10.backend.domain.exchange.controller;

import com.team10.backend.domain.exchange.dto.req.FxWalletCreateReq;
import com.team10.backend.domain.exchange.dto.res.FxWalletRes;
import com.team10.backend.domain.exchange.service.FxWalletService;
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
@RequestMapping("/api/v1/fx-wallets")
@Tag(name = "FxWalletController", description = "외화 지갑 API")
@RequiredArgsConstructor
public class FxWalletController {

    private final FxWalletService fxWalletService;


    @PostMapping
    @Operation(description = "외화 지갑 생성")
    public ResponseEntity<FxWalletRes> createFxWallet(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody FxWalletCreateReq request
            ) {
        CurrencyCode currencyCode = request.currencyCode();
        FxWalletRes response = fxWalletService.createFxWallet(currencyCode, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(description = "내 외화 지갑 목록 조회")
    public ResponseEntity<List<FxWalletRes>> getFxWallets(
            @AuthenticationPrincipal Long userId
    ) {
        List<FxWalletRes> response = fxWalletService.getFxWallets(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{fxWalletId}")
    @Operation(description = "외화 지갑 상세 조회")
    public ResponseEntity<FxWalletRes> getFxWallet(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long fxWalletId
    ) {
        FxWalletRes response = fxWalletService.getFxWallet(fxWalletId, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{fxWalletId}/close")
    @Operation(description = "외화 지갑 해지/비활성화")
    public ResponseEntity<FxWalletRes> closeFxWallet(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long fxWalletId
    ) {
        FxWalletRes response = fxWalletService.closeFxWallet(fxWalletId, userId);
        return ResponseEntity.ok(response);
    }
}
