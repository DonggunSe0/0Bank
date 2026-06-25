package com.team10.backend.domain.saving.controller;

import com.team10.backend.domain.saving.dto.res.SavingProductRes;
import com.team10.backend.domain.saving.dto.res.SavingProductSummaryRes;
import com.team10.backend.domain.saving.service.SavingProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/savings")
@Tag(name = "Saving", description = "예금/적금 상품 API")
public class SavingProductController {

    private final SavingProductService savingProductService;

    @Operation(summary = "예금 상품 목록 조회", description = "활성 상태의 예금 상품 목록을 조회합니다.")
    @GetMapping("/deposit-products")
    public ResponseEntity<List<SavingProductSummaryRes>>
    getDepositProducts() {
        List<SavingProductSummaryRes> response =
                savingProductService.getDepositProducts();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "적금 상품 목록 조회", description = "활성 상태의 적금 상품 목록을 조회합니다.")
    @GetMapping("/installment-products")
    public ResponseEntity<List<SavingProductSummaryRes>>
    getInstallmentProducts() {
        List<SavingProductSummaryRes> response =
                savingProductService.getInstallmentProducts();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "예금 상품 상세 조회", description = "예금 상품의 금리, 기간, 가입 조건 등 상세 정보를 조회합니다.")
    @GetMapping("/deposit-products/{productId}")
    public ResponseEntity<SavingProductRes> getDepositProduct(
            @PathVariable Long productId
    ) {
        SavingProductRes response =
                savingProductService.getDepositProduct(productId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "적금 상품 상세 조회", description = "적금 상품의 금리, 기간, 월 납입 한도 등 상세 정보를 조회합니다.")
    @GetMapping("/installment-products/{productId}")
    public ResponseEntity<SavingProductRes> getInstallmentProduct(
            @PathVariable Long productId
    ) {
        SavingProductRes response =
                savingProductService.getInstallmentProduct(productId);
        return ResponseEntity.ok(response);
    }
}
