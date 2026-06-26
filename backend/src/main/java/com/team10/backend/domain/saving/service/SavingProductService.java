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

    public List<SavingProductSummaryRes> getDepositProducts() {
        return getProductSummaries(SavingProductType.DEPOSIT);
    }

    public List<SavingProductSummaryRes> getInstallmentProducts() {
        return getProductSummaries(SavingProductType.INSTALLMENT);
    }

    public SavingProductRes getDepositProduct(Long productId) {
        return getProduct(productId, SavingProductType.DEPOSIT);
    }

    public SavingProductRes getInstallmentProduct(Long productId) {
        return getProduct(productId, SavingProductType.INSTALLMENT);
    }

    private List<SavingProductSummaryRes> getProductSummaries(SavingProductType type) {
        return savingProductRepository.findAllByTypeAndActiveTrue(type).stream()
                .map(SavingProductSummaryRes::from)
                .toList();
    }

    private SavingProductRes getProduct(Long productId, SavingProductType type) {
        return savingProductRepository.findByIdAndTypeAndActiveTrue(productId, type)
                .map(SavingProductRes::from)
                .orElseThrow(() -> new BusinessException(SavingErrorCode.SAVING_PRODUCT_NOT_FOUND));
    }
}
