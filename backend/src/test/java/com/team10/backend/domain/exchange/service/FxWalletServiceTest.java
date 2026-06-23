package com.team10.backend.domain.exchange.service;

import com.team10.backend.domain.exchange.dto.res.FxWalletRes;
import com.team10.backend.domain.exchange.entity.Currency;
import com.team10.backend.domain.exchange.entity.FxWallet;
import com.team10.backend.domain.exchange.exception.ExchangeErrorCode;
import com.team10.backend.domain.exchange.repository.CurrencyRepository;
import com.team10.backend.domain.exchange.repository.FxWalletRepository;
import com.team10.backend.domain.exchange.type.CurrencyCode;
import com.team10.backend.domain.exchange.type.CurrencyStatus;
import com.team10.backend.domain.exchange.type.FxWalletStatus;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FxWalletServiceTest {

    @Mock
    private FxWalletRepository fxWalletRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrencyRepository currencyRepository;

    @InjectMocks
    private FxWalletService fxWalletService;

    private User user;
    private Currency usd;

    @BeforeEach
    void setUp() {
        user = createUser(1L);
        usd = Currency.create(CurrencyCode.USD, "미국 달러", "미국", 2);
    }

    @Test
    @DisplayName("외화 지갑을 생성한다")
    void createFxWallet() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(currencyRepository.findByCurrencyCode(CurrencyCode.USD)).thenReturn(Optional.of(usd));
        when(fxWalletRepository.findByUserIdAndCurrencyCurrencyCode(1L, CurrencyCode.USD))
                .thenReturn(Optional.empty());
        when(fxWalletRepository.save(any(FxWallet.class))).thenAnswer(invocation -> {
            FxWallet fxWallet = invocation.getArgument(0);
            ReflectionTestUtils.setField(fxWallet, "id", 10L);
            return fxWallet;
        });

        FxWalletRes response = fxWalletService.createFxWallet(CurrencyCode.USD, 1L);

        assertThat(response.walletId()).isEqualTo(10L);
        assertThat(response.currencyCode()).isEqualTo(CurrencyCode.USD);
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.status()).isEqualTo(FxWalletStatus.ACTIVE);

        verify(fxWalletRepository).save(any(FxWallet.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 외화 지갑을 생성할 수 없다")
    void createFxWalletWithNotFoundUser() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fxWalletService.createFxWallet(CurrencyCode.USD, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExchangeErrorCode.USER_NOT_FOUND);

        verify(currencyRepository, never()).findByCurrencyCode(any());
        verify(fxWalletRepository, never()).save(any());
    }

    @Test
    @DisplayName("동기화된 통화가 없으면 외화 지갑을 생성할 수 없다")
    void createFxWalletWithNotFoundCurrency() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(currencyRepository.findByCurrencyCode(CurrencyCode.USD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fxWalletService.createFxWallet(CurrencyCode.USD, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExchangeErrorCode.CURRENCY_NOT_FOUND);

        verify(fxWalletRepository, never()).save(any());
    }

    @Test
    @DisplayName("비활성 통화는 외화 지갑을 생성할 수 없다")
    void createFxWalletWithInactiveCurrency() {
        ReflectionTestUtils.setField(usd, "status", CurrencyStatus.INACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(currencyRepository.findByCurrencyCode(CurrencyCode.USD)).thenReturn(Optional.of(usd));

        assertThatThrownBy(() -> fxWalletService.createFxWallet(CurrencyCode.USD, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExchangeErrorCode.CURRENCY_NOT_SUPPORTED);

        verify(fxWalletRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 사용자와 같은 통화의 외화 지갑은 중복 생성할 수 없다")
    void createFxWalletWithDuplicateWallet() {
        FxWallet wallet = createWallet(10L, user, usd);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(currencyRepository.findByCurrencyCode(CurrencyCode.USD)).thenReturn(Optional.of(usd));
        when(fxWalletRepository.findByUserIdAndCurrencyCurrencyCode(1L, CurrencyCode.USD))
                .thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> fxWalletService.createFxWallet(CurrencyCode.USD, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExchangeErrorCode.FX_WALLET_ALREADY_EXISTS);

        verify(fxWalletRepository, never()).save(any());
    }

    @Test
    @DisplayName("해지된 외화 지갑은 재개설하면 ACTIVE 상태로 변경된다")
    void createFxWalletWithClosedWallet() {
        FxWallet wallet = createWallet(10L, user, usd);
        wallet.close();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(currencyRepository.findByCurrencyCode(CurrencyCode.USD)).thenReturn(Optional.of(usd));
        when(fxWalletRepository.findByUserIdAndCurrencyCurrencyCode(1L, CurrencyCode.USD))
                .thenReturn(Optional.of(wallet));

        FxWalletRes response = fxWalletService.createFxWallet(CurrencyCode.USD, 1L);

        assertThat(response.walletId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(FxWalletStatus.ACTIVE);
        assertThat(wallet.getStatus()).isEqualTo(FxWalletStatus.ACTIVE);

        verify(fxWalletRepository, never()).save(any());
    }

    @Test
    @DisplayName("내 외화 지갑 목록을 조회한다")
    void getFxWallets() {
        FxWallet wallet = createWallet(10L, user, usd);
        when(fxWalletRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(wallet));

        List<FxWalletRes> responses = fxWalletService.getFxWallets(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).walletId()).isEqualTo(10L);
        assertThat(responses.get(0).currencyCode()).isEqualTo(CurrencyCode.USD);
    }

    @Test
    @DisplayName("외화 지갑 상세를 조회한다")
    void getFxWallet() {
        FxWallet wallet = createWallet(10L, user, usd);
        when(fxWalletRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(wallet));

        FxWalletRes response = fxWalletService.getFxWallet(10L, 1L);

        assertThat(response.walletId()).isEqualTo(10L);
        assertThat(response.currencyCode()).isEqualTo(CurrencyCode.USD);
        assertThat(response.status()).isEqualTo(FxWalletStatus.ACTIVE);
    }

    @Test
    @DisplayName("내 지갑이 아니거나 존재하지 않는 외화 지갑은 상세 조회에 실패한다")
    void getFxWalletWithNotFoundWallet() {
        when(fxWalletRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fxWalletService.getFxWallet(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExchangeErrorCode.FX_WALLET_NOT_FOUND);
    }

    @Test
    @DisplayName("잔액이 없는 ACTIVE 외화 지갑을 해지한다")
    void closeFxWallet() {
        FxWallet wallet = createWallet(10L, user, usd);
        when(fxWalletRepository.findByIdAndUserIdForUpdate(10L, 1L)).thenReturn(Optional.of(wallet));

        FxWalletRes response = fxWalletService.closeFxWallet(10L, 1L);

        assertThat(response.walletId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(FxWalletStatus.CLOSED);
        assertThat(wallet.getStatus()).isEqualTo(FxWalletStatus.CLOSED);
    }

    @Test
    @DisplayName("ACTIVE 상태가 아닌 외화 지갑은 해지할 수 없다")
    void closeFxWalletWithNotActiveStatus() {
        FxWallet wallet = createWallet(10L, user, usd);
        wallet.close();
        when(fxWalletRepository.findByIdAndUserIdForUpdate(10L, 1L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> fxWalletService.closeFxWallet(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExchangeErrorCode.FX_WALLET_NOT_ACTIVE);
    }

    @Test
    @DisplayName("잔액이 남아있는 외화 지갑은 해지할 수 없다")
    void closeFxWalletWithRemainingBalance() {
        FxWallet wallet = createWallet(10L, user, usd);
        ReflectionTestUtils.setField(wallet, "balance", BigDecimal.ONE);
        when(fxWalletRepository.findByIdAndUserIdForUpdate(10L, 1L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> fxWalletService.closeFxWallet(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExchangeErrorCode.FX_WALLET_BALANCE_REMAINING);
    }

    private User createUser(Long id) {
        User user = User.create(
                "user" + id + "@example.com",
                "password",
                "홍길동",
                "01012345678",
                LocalDate.of(1995, 1, 1)
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private FxWallet createWallet(Long id, User user, Currency currency) {
        FxWallet wallet = FxWallet.create(user, currency);
        ReflectionTestUtils.setField(wallet, "id", id);
        return wallet;
    }
}
