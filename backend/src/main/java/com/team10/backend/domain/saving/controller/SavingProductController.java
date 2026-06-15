package com.team10.backend.domain.saving.controller;

import com.team10.backend.domain.saving.dto.res.SavingProductRes;
import com.team10.backend.domain.saving.dto.res.SavingProductSummaryRes;
import com.team10.backend.domain.saving.service.SavingProductService;
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
public class SavingProductController {

    private final SavingProductService savingProductService;

    // 예금 상품 목록 조회
    @GetMapping("/deposit-products")
    public ResponseEntity<List<SavingProductSummaryRes>>
    getDepositProducts() {
        List<SavingProductSummaryRes> response =
                savingProductService.getDepositProducts();
        return ResponseEntity.ok(response);
    }

    // 적금 상품 목록 조회
    @GetMapping("/installment-products")
    public ResponseEntity<List<SavingProductSummaryRes>>
    getInstallmentProducts() {
        List<SavingProductSummaryRes> response =
                savingProductService.getInstallmentProducts();
        return ResponseEntity.ok(response);
    }

    // 예금 상품 상세 조회
    @GetMapping("/deposit-products/{productId}")
    public ResponseEntity<SavingProductRes> getDepositProduct(
            @PathVariable Long productId
    ) {
        SavingProductRes response =
                savingProductService.getDepositProduct(productId);
        return ResponseEntity.ok(response);
    }

    // 적금 상품 상세 조회
    @GetMapping("/installment-products/{productId}")
    public ResponseEntity<SavingProductRes> getInstallmentProduct(
            @PathVariable Long productId
    ) {
        SavingProductRes response =
                savingProductService.getInstallmentProduct(productId);
        return ResponseEntity.ok(response);
    }
}
