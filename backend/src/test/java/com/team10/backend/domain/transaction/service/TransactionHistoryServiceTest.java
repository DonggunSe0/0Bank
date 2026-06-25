package com.team10.backend.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.exception.AccountErrorCode;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.transaction.dto.req.TransactionHistorySearchReq;
import com.team10.backend.domain.transaction.dto.res.TransactionHistoryDetailRes;
import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.exception.TransactionHistoryErrorCode;
import com.team10.backend.domain.transaction.repository.TransactionHistoryRepository;
import com.team10.backend.domain.transaction.type.TransactionDirection;
import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.global.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class TransactionHistoryServiceTest {

    @Mock
    private TransactionHistoryRepository transactionHistoryRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private TransactionHistoryService transactionHistoryService;

    @Test
    @DisplayName("계좌 소유자 검증 성공 시 거래내역을 조회한다")
    void getTransactionHistoriesSucceedsWhenUserOwnsAccount() {
        TransactionHistorySearchReq filter = new TransactionHistorySearchReq(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 9),
                TransactionDirection.IN,
                1_000L,
                10_000L,
                "홍길동"
        );
        given(accountRepository.findByIdAndUserId(1L, 10L))
                .willReturn(Optional.of(mock(Account.class)));
        given(transactionHistoryRepository.search(eq(1L), eq(filter), any(Pageable.class)))
                .willReturn(Page.empty());

        Page<?> result = transactionHistoryService.getTransactionHistories(
                1L,
                10L,
                filter,
                2,
                Sort.Direction.ASC
        );

        assertThat(result).isEmpty();
        verify(accountRepository).findByIdAndUserId(1L, 10L);
        verify(transactionHistoryRepository).search(eq(1L), eq(filter), any(Pageable.class));
    }

    @Test
    @DisplayName("계좌 소유자가 아니면 예외가 발생하고 조회하지 않는다")
    void getTransactionHistoriesFailsWhenUserDoesNotOwnAccount() {
        TransactionHistorySearchReq filter = emptyFilter();
        given(accountRepository.findByIdAndUserId(1L, 10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> transactionHistoryService.getTransactionHistories(
                1L,
                10L,
                filter,
                0,
                Sort.Direction.DESC
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_ACCESS_DENIED);

        verify(transactionHistoryRepository, never()).search(any(), any(), any());
    }

    @Test
    @DisplayName("페이지 크기 20과 transactedAt 정렬로 Pageable을 생성한다")
    void createsPageableWithFixedPageSizeAndTransactedAtSort() {
        TransactionHistorySearchReq filter = emptyFilter();
        given(accountRepository.findByIdAndUserId(1L, 10L))
                .willReturn(Optional.of(mock(Account.class)));
        given(transactionHistoryRepository.search(eq(1L), eq(filter), any(Pageable.class)))
                .willReturn(Page.empty());

        transactionHistoryService.getTransactionHistories(
                1L,
                10L,
                filter,
                3,
                Sort.Direction.ASC
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionHistoryRepository).search(eq(1L), eq(filter), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        Sort.Order order = pageable.getSort().getOrderFor(TransactionHistoryService.SORT_PROPERTY_TRANSACTED_AT);
        assertThat(pageable.getPageNumber()).isEqualTo(3);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("단건 조회 시 계좌 소유자 검증 후 상세 응답을 반환한다")
    void getTransactionHistoryDetailSucceedsWhenUserOwnsAccount() {
        TransactionHistory transactionHistory = mockTransactionHistory();
        given(accountRepository.findByIdAndUserId(1L, 10L))
                .willReturn(Optional.of(mock(Account.class)));
        given(transactionHistoryRepository.findByIdAndAccountId(100L, 1L))
                .willReturn(Optional.of(transactionHistory));

        TransactionHistoryDetailRes result = transactionHistoryService.getTransactionHistoryDetail(1L, 100L, 10L);

        assertThat(result.transactionHistoryId()).isEqualTo(100L);
        assertThat(result.type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(result.direction()).isEqualTo(TransactionDirection.OUT);
        assertThat(result.amount()).isEqualTo(5_000L);
        assertThat(result.balanceAfter()).isEqualTo(95_000L);
        assertThat(result.counterpartyName()).isEqualTo("홍길동");
        assertThat(result.memo()).isEqualTo("점심값");
        assertThat(result.transactedAt()).isEqualTo(LocalDateTime.of(2026, 6, 9, 12, 30));
        verify(accountRepository).findByIdAndUserId(1L, 10L);
        verify(transactionHistoryRepository).findByIdAndAccountId(100L, 1L);
    }

    @Test
    @DisplayName("단건 조회 시 계좌 소유자가 아니면 예외가 발생하고 거래내역을 조회하지 않는다")
    void getTransactionHistoryDetailFailsWhenUserDoesNotOwnAccount() {
        given(accountRepository.findByIdAndUserId(1L, 10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> transactionHistoryService.getTransactionHistoryDetail(1L, 100L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_ACCESS_DENIED);

        verify(transactionHistoryRepository, never()).findByIdAndAccountId(any(), any());
    }

    @Test
    @DisplayName("단건 조회 시 거래내역이 없으면 예외가 발생한다")
    void getTransactionHistoryDetailFailsWhenTransactionHistoryDoesNotExist() {
        given(accountRepository.findByIdAndUserId(1L, 10L))
                .willReturn(Optional.of(mock(Account.class)));
        given(transactionHistoryRepository.findByIdAndAccountId(100L, 1L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> transactionHistoryService.getTransactionHistoryDetail(1L, 100L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransactionHistoryErrorCode.TRANSACTION_HISTORY_NOT_FOUND);
    }

    private TransactionHistorySearchReq emptyFilter() {
        return new TransactionHistorySearchReq(null, null, null, null, null, null);
    }

    private TransactionHistory mockTransactionHistory() {
        TransactionHistory transactionHistory = mock(TransactionHistory.class);
        given(transactionHistory.getId()).willReturn(100L);
        given(transactionHistory.getType()).willReturn(TransactionType.TRANSFER);
        given(transactionHistory.getDirection()).willReturn(TransactionDirection.OUT);
        given(transactionHistory.getAmount()).willReturn(5_000L);
        given(transactionHistory.getBalanceAfter()).willReturn(95_000L);
        given(transactionHistory.getCounterpartyName()).willReturn("홍길동");
        given(transactionHistory.getMemo()).willReturn("점심값");
        given(transactionHistory.getTransactedAt()).willReturn(LocalDateTime.of(2026, 6, 9, 12, 30));
        return transactionHistory;
    }
}
