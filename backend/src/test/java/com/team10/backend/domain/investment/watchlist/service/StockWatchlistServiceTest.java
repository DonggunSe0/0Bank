package com.team10.backend.domain.investment.watchlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.investment.exception.InvestmentErrorCode;
import com.team10.backend.domain.investment.stock.dto.res.StockSummaryRes;
import com.team10.backend.domain.investment.stock.entity.Stock;
import com.team10.backend.domain.investment.stock.repository.StockRepository;
import com.team10.backend.domain.investment.stock.type.StockMarket;
import com.team10.backend.domain.investment.stock.type.StockStatus;
import com.team10.backend.domain.investment.type.CurrencyCode;
import com.team10.backend.domain.investment.watchlist.entity.StockWatchlist;
import com.team10.backend.domain.investment.watchlist.repository.StockWatchlistRepository;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StockWatchlistServiceTest {

    @Mock
    private StockWatchlistRepository stockWatchlistRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StockWatchlistService stockWatchlistService;

    @Test
    @DisplayName("관심 종목을 등록하고 등록된 종목 요약 정보를 반환한다")
    void addWatchlist() {
        User user = user(1L);
        Stock stock = stock(10L, StockStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(stockRepository.findById(10L)).thenReturn(Optional.of(stock));
        when(stockWatchlistRepository.existsByUserIdAndStockId(1L, 10L)).thenReturn(false);
        when(stockWatchlistRepository.countByUserId(1L)).thenReturn(0L);

        StockSummaryRes result = stockWatchlistService.addWatchlist(1L, 10L);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.stockCode()).isEqualTo("005930");
        assertThat(result.stockName()).isEqualTo("삼성전자");

        ArgumentCaptor<StockWatchlist> captor = ArgumentCaptor.forClass(StockWatchlist.class);
        verify(userRepository).findByIdForUpdate(1L);
        verify(stockWatchlistRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getStock()).isEqualTo(stock);
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 관심 종목을 등록할 수 없다")
    void addWatchlistWithMissingUser() {
        when(userRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockWatchlistService.addWatchlist(999L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);

        verifyNoInteractions(stockRepository, stockWatchlistRepository);
    }

    @Test
    @DisplayName("존재하지 않는 종목은 관심 종목으로 등록할 수 없다")
    void addWatchlistWithMissingStock() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(stockRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockWatchlistService.addWatchlist(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.STOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("ACTIVE 상태가 아닌 종목은 관심 종목으로 등록할 수 없다")
    void addWatchlistWithInactiveStock() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(stockRepository.findById(10L)).thenReturn(Optional.of(stock(10L, StockStatus.SUSPENDED)));

        assertThatThrownBy(() -> stockWatchlistService.addWatchlist(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.STOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 등록된 종목은 중복 등록할 수 없다")
    void addWatchlistWithDuplicatedStock() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(stockRepository.findById(10L)).thenReturn(Optional.of(stock(10L, StockStatus.ACTIVE)));
        when(stockWatchlistRepository.existsByUserIdAndStockId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> stockWatchlistService.addWatchlist(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.WATCHLIST_DUPLICATED);
    }

    @Test
    @DisplayName("관심 종목은 최대 20개까지 등록할 수 있다")
    void addWatchlistWithLimitExceeded() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(stockRepository.findById(10L)).thenReturn(Optional.of(stock(10L, StockStatus.ACTIVE)));
        when(stockWatchlistRepository.existsByUserIdAndStockId(1L, 10L)).thenReturn(false);
        when(stockWatchlistRepository.countByUserId(1L)).thenReturn(20L);

        assertThatThrownBy(() -> stockWatchlistService.addWatchlist(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.WATCHLIST_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("동시 요청으로 unique 제약 조건을 위반하면 중복 등록 예외가 발생한다")
    void addWatchlistWithDuplicatedByUniqueConstraint() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L)));
        when(stockRepository.findById(10L)).thenReturn(Optional.of(stock(10L, StockStatus.ACTIVE)));
        when(stockWatchlistRepository.existsByUserIdAndStockId(1L, 10L)).thenReturn(false);
        when(stockWatchlistRepository.countByUserId(1L)).thenReturn(0L);
        when(stockWatchlistRepository.saveAndFlush(any(StockWatchlist.class)))
                .thenThrow(new DataIntegrityViolationException("duplicated"));

        assertThatThrownBy(() -> stockWatchlistService.addWatchlist(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(InvestmentErrorCode.WATCHLIST_DUPLICATED);
    }

    @Test
    @DisplayName("사용자 관심 종목 목록을 종목 요약 정보로 조회한다")
    void getWatchlists() {
        User user = user(1L);
        Stock stock = stock(10L, StockStatus.ACTIVE);
        when(stockWatchlistRepository.findAllByUserIdWithStock(1L))
                .thenReturn(List.of(StockWatchlist.create(user, stock)));

        List<StockSummaryRes> result = stockWatchlistService.getWatchlists(1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(10L);
        assertThat(result.getFirst().stockCode()).isEqualTo("005930");
    }

    @Test
    @DisplayName("사용자 ID와 종목 ID로 관심 종목을 삭제한다")
    void removeWatchlist() {
        stockWatchlistService.removeWatchlist(1L, 10L);

        verify(stockWatchlistRepository).deleteByUserIdAndStockId(1L, 10L);
    }

    private User user(Long id) {
        User user = User.create(
                "test@example.com",
                "password",
                "테스터",
                "01012345678",
                LocalDate.of(1995, 1, 1)
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Stock stock(Long id, StockStatus status) {
        Stock stock = Stock.create(
                "005930",
                "KR7005930003",
                "삼성전자",
                StockMarket.KOSPI,
                CurrencyCode.KRW,
                status,
                LocalDate.of(1975, 6, 11),
                1_000_000L,
                2_000_000L,
                3_000_000L,
                4_000_000L,
                10_000L
        );
        ReflectionTestUtils.setField(stock, "id", id);
        return stock;
    }
}
