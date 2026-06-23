package com.team10.backend.domain.exAccount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team10.backend.domain.exAccount.type.ExAccountTransactionDirection;
import com.team10.backend.domain.exAccount.type.ExAccountType;
import com.team10.backend.domain.exAccount.dto.req.ExAccountLinkReq;
import com.team10.backend.domain.exAccount.dto.req.ExAccountTransactionSyncReq;
import com.team10.backend.domain.exAccount.dto.res.ExAccountDetailRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountRes;
import com.team10.backend.domain.exAccount.dto.res.ExAccountTransactionRefreshRes;
import com.team10.backend.domain.exAccount.entity.ExAccount;
import com.team10.backend.domain.exAccount.entity.ExAccountTransaction;
import com.team10.backend.domain.exAccount.exception.ExAccountErrorCode;
import com.team10.backend.domain.exAccount.repository.ExAccountRepository;
import com.team10.backend.domain.exAccount.repository.ExAccountTransactionRepository;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.exception.GlobalErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExAccountTransactionServiceTest {

    @Mock
    private ExAccountTransactionRepository transactionRepository;

    @Mock
    private ExAccountRepository accountRepository;

    @Mock
    private ExAccountService exAccountService;

    @InjectMocks
    private ExAccountTransactionService exAccountTransactionService;

    private User user;
    private ExAccount account;

    @BeforeEach
    void setUp() {
        user = createUser(1L);
        account = createExAccount(10L);
    }

    @Test
    @DisplayName("새 거래내역을 새로고침하면 신규 저장하고 상세 응답을 반환한다")
    void refreshTransactionsCreate() {
        ExAccountTransactionSyncReq request = createTransactionSyncReq("KB-20260618143000-0001", "스타벅스");
        ExAccountDetailRes detail = ExAccountDetailRes.of(ExAccountRes.from(account), List.of());

        when(accountRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(account));
        when(transactionRepository.findByExAccountIdAndTransactionKey(10L, "KB-20260618143000-0001"))
                .thenReturn(Optional.empty());
        when(exAccountService.getAccountDetail(1L, 10L)).thenReturn(detail);

        ExAccountTransactionRefreshRes response = exAccountTransactionService.refreshTransactions(
                1L,
                10L,
                List.of(request)
        );

        assertThat(response.requestedCount()).isEqualTo(1);
        assertThat(response.createdCount()).isEqualTo(1);
        assertThat(response.updatedCount()).isZero();
        assertThat(account.getLastTransactionAt()).isEqualTo(LocalDate.of(2026, 6, 18));

        verify(transactionRepository).saveAndFlush(any(ExAccountTransaction.class));
    }

    @Test
    @DisplayName("거래내역 insert unique 충돌이 나면 다시 조회해 기존 거래내역 스냅샷을 갱신한다")
    void refreshTransactionsCreateRecoversFromUniqueConflictByUpdatingExistingTransaction() {
        ExAccountTransactionSyncReq request = createTransactionSyncReq("KB-20260618143000-0001", "스타벅스");
        ExAccountTransaction existingTransaction = createTransaction(100L, "KB-20260618143000-0001", "기존상호");
        ExAccountDetailRes detail = ExAccountDetailRes.of(ExAccountRes.from(account), List.of());

        when(accountRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(account));
        when(transactionRepository.findByExAccountIdAndTransactionKey(10L, "KB-20260618143000-0001"))
                .thenReturn(Optional.empty(), Optional.of(existingTransaction));
        when(transactionRepository.saveAndFlush(any(ExAccountTransaction.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate external transaction"));
        when(exAccountService.getAccountDetail(1L, 10L)).thenReturn(detail);

        ExAccountTransactionRefreshRes response = exAccountTransactionService.refreshTransactions(
                1L,
                10L,
                List.of(request)
        );

        assertThat(response.createdCount()).isZero();
        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(existingTransaction.getCounterpartyName()).isEqualTo("스타벅스");
    }

    @Test
    @DisplayName("기존 거래내역을 새로고침하면 신규 저장하지 않고 스냅샷을 갱신한다")
    void refreshTransactionsUpdate() {
        ExAccountTransaction transaction = createTransaction(100L, "KB-20260618143000-0001", "스타벅스");
        ExAccountTransactionSyncReq request = createTransactionSyncReq("KB-20260618143000-0001", "편의점");
        ExAccountDetailRes detail = ExAccountDetailRes.of(ExAccountRes.from(account), List.of());

        when(accountRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(account));
        when(transactionRepository.findByExAccountIdAndTransactionKey(10L, "KB-20260618143000-0001"))
                .thenReturn(Optional.of(transaction));
        when(exAccountService.getAccountDetail(1L, 10L)).thenReturn(detail);

        ExAccountTransactionRefreshRes response = exAccountTransactionService.refreshTransactions(
                1L,
                10L,
                List.of(request)
        );

        assertThat(response.requestedCount()).isEqualTo(1);
        assertThat(response.createdCount()).isZero();
        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(transaction.getCounterpartyName()).isEqualTo("편의점");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("내 외부 계좌가 아니거나 존재하지 않으면 거래내역 새로고침에 실패한다")
    void refreshTransactionsWithNotFoundAccount() {
        ExAccountTransactionSyncReq request = createTransactionSyncReq("KB-20260618143000-0001", "스타벅스");

        when(accountRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exAccountTransactionService.refreshTransactions(1L, 999L, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExAccountErrorCode.EX_ACCOUNT_NOT_FOUND);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("거래내역 새로고침 목록이 비어 있으면 실패한다")
    void refreshTransactionsWithEmptyTransactions() {
        assertThatThrownBy(() -> exAccountTransactionService.refreshTransactions(1L, 10L, List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        verify(accountRepository, never()).findByIdAndUserId(any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("필수값이 누락된 거래내역은 계좌 조회 전에 공통 입력 오류로 실패한다")
    void refreshTransactionsWithRequiredFieldMissing() {
        ExAccountTransactionSyncReq request = createTransactionSyncReq("", "스타벅스");

        assertThatThrownBy(() -> exAccountTransactionService.refreshTransactions(1L, 10L, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        verify(accountRepository, never()).findByIdAndUserId(any(), any());
        verify(transactionRepository, never()).save(any());
    }

    private ExAccountTransactionSyncReq createTransactionSyncReq(String transactionKey, String counterpartyName) {
        return new ExAccountTransactionSyncReq(
                transactionKey,
                LocalDateTime.of(2026, 6, 18, 14, 30),
                ExAccountTransactionDirection.OUT,
                BigDecimal.valueOf(45_000),
                BigDecimal.valueOf(1_455_000),
                counterpartyName,
                "카드 결제",
                "식비"
        );
    }

    private ExAccount createExAccount(Long id) {
        ExAccount account = ExAccount.create(
                user,
                "국민은행",
                "account-number-hash",
                "123456****1234",
                "KB Star 입출금통장",
                "생활비 통장",
                ExAccountType.DEMAND,
                BigDecimal.valueOf(1_500_000),
                BigDecimal.valueOf(1_200_000),
                LocalDate.of(2024, 1, 15),
                null,
                LocalDate.of(2026, 6, 1)
        );
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private ExAccountTransaction createTransaction(Long id, String transactionKey, String counterpartyName) {
        ExAccountTransaction transaction = ExAccountTransaction.create(
                account,
                transactionKey,
                LocalDateTime.of(2026, 6, 18, 14, 30),
                ExAccountTransactionDirection.OUT,
                BigDecimal.valueOf(45_000),
                BigDecimal.valueOf(1_455_000),
                counterpartyName,
                "카드 결제",
                "식비"
        );
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
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
}
