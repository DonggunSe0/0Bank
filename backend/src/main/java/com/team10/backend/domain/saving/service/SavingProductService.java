package com.team10.backend.domain.saving.service;

import com.team10.backend.domain.saving.dto.res.SavingProductRes;
import com.team10.backend.domain.saving.dto.res.SavingProductSummaryRes;
import com.team10.backend.domain.saving.exception.SavingErrorCode;
import com.team10.backend.domain.saving.repository.SavingProductRepository;
import com.team10.backend.domain.saving.type.SavingProductType;
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SavingProductService {

    private final SavingProductRepository savingProductRepository;

    // 예금 상품 목록 조회
    public List<SavingProductSummaryRes> getDepositProducts() {
        return
                savingProductRepository.findAllByTypeAndActiveTrue(SavingProductType.DEPOSIT).stream()
                        .map(SavingProductSummaryRes::from)
                        .toList();
    }

    // 적금 상품 목록 조회
    public List<SavingProductSummaryRes> getInstallmentProducts() {
        return
                savingProductRepository.findAllByTypeAndActiveTrue(SavingProductType.INSTALLMENT).stream()
                        .map(SavingProductSummaryRes::from)
                        .toList();
    }

    // 예금 상품 상세 조회
    public SavingProductRes getDepositProduct(Long productId) {
        return
                savingProductRepository.findByIdAndTypeAndActiveTrue(productId,
                                SavingProductType.DEPOSIT)
                        .map(SavingProductRes::from)
                        .orElseThrow(() -> new
                                BusinessException(SavingErrorCode.SAVING_PRODUCT_NOT_FOUND));
    }

    // 적금 상품 상세 조회
    public SavingProductRes getInstallmentProduct(Long productId) {
        return
                savingProductRepository.findByIdAndTypeAndActiveTrue(productId,
                                SavingProductType.INSTALLMENT)
                        .map(SavingProductRes::from)
                        .orElseThrow(() -> new
                                BusinessException(SavingErrorCode.SAVING_PRODUCT_NOT_FOUND));
    }
}
