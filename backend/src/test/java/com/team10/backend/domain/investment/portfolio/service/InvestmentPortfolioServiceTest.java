package com.team10.backend.domain.investment.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.investment.account.entity.InvestmentAccount;
import com.team10.backend.domain.investment.account.repository.InvestmentAccountRepository;
import com.team10.backend.domain.investment.account.type.InvestmentAccountStatus;
import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.domain.investment.portfolio.dto.res.InvestmentHoldingRes;
import com.team10.backend.domain.investment.portfolio.entity.InvestmentHolding;
import com.team10.backend.domain.investment.portfolio.repository.InvestmentHoldingRepository;
import com.team10.backend.domain.investment.stock.entity.Stock;
import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import com.team10.backend.domain.investment.type.CurrencyCode;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvestmentPortfolioServiceTest {

    private static final Long INITIAL_CASH_BALANCE = 5_000_000L;

    @Mock
    private InvestmentAccountRepository investmentAccountRepository;

    @Mock
    private InvestmentHoldingRepository investmentHoldingRepository;

    @InjectMocks
    private InvestmentPortfolioService investmentPortfolioService;

    private User user;
    private InvestmentAccount account;
    private Stock stock;

    @BeforeEach
    void setUp() {
        user = User.create(
                "test@example.com",
                "encoded-password",
                "테스터",
                "01012345678",
                LocalDate.of(1995, 1, 1)
        );
        ReflectionTestUtils.setField(user, "id", 1L);

        account = InvestmentAccount.create(
                user,
                "1234567890-12",
                "투자 계좌",
                "encoded-account-password",
                INITIAL_CASH_BALANCE,
                CurrencyCode.KRW)
        ;
        ReflectionTestUtils.setField(account, "id", 10L);

        stock = Stock.create(
                "005930",
                "KR7005930003",
                "삼성전자",
                StockMarket.KOSPI,
                CurrencyCode.KRW,
                StockStatus.ACTIVE,
                LocalDate.of(1975, 6, 11),
                null,
                null,
                null,
                4_000_000L,
                10_000L
        );
        ReflectionTestUtils.setField(stock, "id", 20L);
    }

    @Test
    @DisplayName("인증 사용자의 투자 계좌 보유 종목을 페이지 조회한다")
    void getHoldings() {
        InvestmentHolding holding = InvestmentHolding.create(
                account,
                stock,
                3L,
                new BigDecimal("70000.00")
        );
        ReflectionTestUtils.setField(holding, "id", 30L);

        when(investmentAccountRepository.findByIdAndUserIdAndStatusNot(10L, 1L, InvestmentAccountStatus.CLOSED))
                .thenReturn(Optional.of(account));
        when(investmentHoldingRepository.findPageByInvestmentAccountIdWithStock(10L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(holding), PageRequest.of(0, 20), 1));

        Page<InvestmentHoldingRes> response = investmentPortfolioService.getHoldings(1L, 10L, 0, 20);

        assertThat(response.getTotalElements()).isEqualTo(1);
        InvestmentHoldingRes result = response.getContent().get(0);
        assertThat(result.id()).isEqualTo(30L);
        assertThat(result.stockId()).isEqualTo(20L);
        assertThat(result.stockCode()).isEqualTo("005930");
        assertThat(result.stockName()).isEqualTo("삼성전자");
        assertThat(result.quantity()).isEqualTo(3L);
        assertThat(result.averagePrice()).isEqualByComparingTo("70000.00");
    }

    @Test
    @DisplayName("내 계좌가 아니거나 해지된 계좌이면 보유 종목 조회에 실패한다")
    void getHoldingsWithMissingAccount() {
        when(investmentAccountRepository.findByIdAndUserIdAndStatusNot(10L, 1L, InvestmentAccountStatus.CLOSED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> investmentPortfolioService.getHoldings(1L, 10L, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.INVESTMENT_ACCOUNT_NOT_FOUND);

        verify(investmentHoldingRepository, never())
                .findPageByInvestmentAccountIdWithStock(10L, PageRequest.of(0, 20));
    }
}
