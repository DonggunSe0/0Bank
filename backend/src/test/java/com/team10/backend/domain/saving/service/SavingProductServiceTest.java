package com.team10.backend.domain.saving.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.saving.dto.res.SavingProductRes;
import com.team10.backend.domain.saving.dto.res.SavingProductSummaryRes;
import com.team10.backend.domain.saving.entity.SavingProduct;
import com.team10.backend.domain.saving.exception.SavingErrorCode;
import com.team10.backend.domain.saving.repository.SavingProductRepository;
import com.team10.backend.domain.saving.type.SavingProductType;
import com.team10.backend.global.exception.BusinessException;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SavingProductServiceTest {

    @Mock
    private SavingProductRepository savingProductRepository;

    @InjectMocks
    private SavingProductService savingProductService;

    @Test
    @DisplayName("판매 중인 예금 상품 목록을 조회한다")
    void getDepositProducts() {
        SavingProduct product = createSavingProduct(1L, SavingProductType.DEPOSIT, "정기예금");

        when(savingProductRepository.findAllByTypeAndActiveTrue(SavingProductType.DEPOSIT))
                .thenReturn(List.of(product));

        List<SavingProductSummaryRes> responses = savingProductService.getDepositProducts();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(1L);
        assertThat(responses.get(0).name()).isEqualTo("정기예금");
        assertThat(responses.get(0).bankName()).isEqualTo("국민은행");
        assertThat(responses.get(0).interestRate()).isEqualTo(3.5);
        assertThat(responses.get(0).periodMonth()).isEqualTo(12);
        assertThat(responses.get(0).minAmount()).isEqualTo(100000L);
    }

    @Test
    @DisplayName("판매 중인 적금 상품 목록을 조회한다")
    void getInstallmentProducts() {
        SavingProduct product = createSavingProduct(2L, SavingProductType.INSTALLMENT, "청년 적금");

        when(savingProductRepository.findAllByTypeAndActiveTrue(SavingProductType.INSTALLMENT))
                .thenReturn(List.of(product));

        List<SavingProductSummaryRes> responses = savingProductService.getInstallmentProducts();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(2L);
        assertThat(responses.get(0).name()).isEqualTo("청년 적금");
        assertThat(responses.get(0).bankName()).isEqualTo("국민은행");
    }

    @Test
    @DisplayName("예금 상품 상세를 조회한다")
    void getDepositProduct() {
        SavingProduct product = createSavingProduct(1L, SavingProductType.DEPOSIT, "정기예금");

        when(savingProductRepository.findByIdAndTypeAndActiveTrue(1L, SavingProductType.DEPOSIT))
                .thenReturn(Optional.of(product));

        SavingProductRes response = savingProductService.getDepositProduct(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("정기예금");
        assertThat(response.type()).isEqualTo(SavingProductType.DEPOSIT);
        assertThat(response.terms()).isEqualTo("가입 조건");
    }

    @Test
    @DisplayName("적금 상품 상세를 조회한다")
    void getInstallmentProduct() {
        SavingProduct product = createSavingProduct(2L, SavingProductType.INSTALLMENT, "청년 적금");

        when(savingProductRepository.findByIdAndTypeAndActiveTrue(2L, SavingProductType.INSTALLMENT))
                .thenReturn(Optional.of(product));

        SavingProductRes response = savingProductService.getInstallmentProduct(2L);

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.name()).isEqualTo("청년 적금");
        assertThat(response.type()).isEqualTo(SavingProductType.INSTALLMENT);
        assertThat(response.monthlyLimit()).isEqualTo(500000L);
    }

    @Test
    @DisplayName("조회 가능한 상품이 없으면 상세 조회에 실패한다")
    void getProductWithNotFoundProduct() {
        when(savingProductRepository.findByIdAndTypeAndActiveTrue(999L, SavingProductType.DEPOSIT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> savingProductService.getDepositProduct(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SavingErrorCode.SAVING_PRODUCT_NOT_FOUND);
    }

    private SavingProduct createSavingProduct(Long id, SavingProductType type, String name) {
        SavingProduct product = SavingProduct.builder()
                .name(name)
                .bankName("국민은행")
                .bankCode("KB")
                .type(type)
                .interestRate(3.5)
                .periodMonth(12)
                .minAmount(100000L)
                .maxAmount(10000000L)
                .monthlyLimit(500000L)
                .terms("가입 조건")
                .active(true)
                .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
