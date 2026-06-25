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
        return
                savingProductRepository.findAllByTypeAndActiveTrue(SavingProductType.DEPOSIT).stream()
                        .map(SavingProductSummaryRes::from)
                        .toList();
    }

    public List<SavingProductSummaryRes> getInstallmentProducts() {
        return
                savingProductRepository.findAllByTypeAndActiveTrue(SavingProductType.INSTALLMENT).stream()
                        .map(SavingProductSummaryRes::from)
                        .toList();
    }

    public SavingProductRes getDepositProduct(Long productId) {
        return
                savingProductRepository.findByIdAndTypeAndActiveTrue(productId,
                                SavingProductType.DEPOSIT)
                        .map(SavingProductRes::from)
                        .orElseThrow(() -> new
                                BusinessException(SavingErrorCode.SAVING_PRODUCT_NOT_FOUND));
    }

    public SavingProductRes getInstallmentProduct(Long productId) {
        return
                savingProductRepository.findByIdAndTypeAndActiveTrue(productId,
                                SavingProductType.INSTALLMENT)
                        .map(SavingProductRes::from)
                        .orElseThrow(() -> new
                                BusinessException(SavingErrorCode.SAVING_PRODUCT_NOT_FOUND));
    }
}
