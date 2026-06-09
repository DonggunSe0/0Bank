package com.team10.backend.domain.transfer.service;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.repository.TransactionHistoryRepository;
import com.team10.backend.domain.transaction.type.TransactionDirection;
import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.domain.transfer.dto.res.DepositRes;
import com.team10.backend.domain.transfer.dto.res.TransferRes;
import com.team10.backend.domain.transfer.entity.Transfer;
import com.team10.backend.domain.transfer.errorcode.TransferErrorCode;
import com.team10.backend.domain.transfer.repository.TransferRepository;
import com.team10.backend.domain.transfer.type.TransferStatus;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransactionHistoryRepository transactionHistoryRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferRepository transferRepository;

    @InjectMocks
    private TransferService transferService;

    @Test
    void deposit_success_increasesBalanceAndSavesDepositHistory() {
        Account account = account(1L, user(), "100200300001", 10_000L);
        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));
        when(transactionHistoryRepository.save(any(TransactionHistory.class)))
                .thenAnswer(invocation -> {
                    TransactionHistory history = invocation.getArgument(0);
                    ReflectionTestUtils.setField(history, "id", 100L);
                    return history;
                });

        DepositRes response = transferService.deposit(1L, 5_000L, "입금 메모");

        assertEquals(15_000L, account.getBalance());
        assertEquals(100L, response.transactionId());
        assertEquals(1L, response.accountId());
        assertEquals(TransactionType.DEPOSIT, response.type());
        assertEquals(5_000L, response.amount());
        assertEquals(10_000L, response.balanceBefore());
        assertEquals(15_000L, response.balanceAfter());
        assertEquals("입금 메모", response.memo());
        assertNotNull(response.transactedAt());

        ArgumentCaptor<TransactionHistory> historyCaptor = ArgumentCaptor.forClass(TransactionHistory.class);
        verify(transactionHistoryRepository).save(historyCaptor.capture());

        TransactionHistory savedHistory = historyCaptor.getValue();
        assertSame(account, savedHistory.getAccount());
        assertEquals(TransactionType.DEPOSIT, savedHistory.getType());
        assertEquals(TransactionDirection.IN, savedHistory.getDirection());
        assertEquals(5_000L, savedHistory.getAmount());
        assertEquals(10_000L, savedHistory.getBalanceBefore());
        assertEquals(15_000L, savedHistory.getBalanceAfter());
        assertEquals("입금 메모", savedHistory.getMemo());
    }

    @Test
    void deposit_invalidAmount_throwsInvalidInputValue() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferService.deposit(1L, 0L, "입금 메모")
        );

        assertEquals(TransferErrorCode.INVALID_INPUT_VALUE, exception.getErrorCode());
        verify(accountRepository, never()).findByIdAndUserId(any(), any());
        verify(transactionHistoryRepository, never()).save(any());
    }

    @Test
    void deposit_accountNotFound_throwsAccountNotFound() {
        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferService.deposit(1L, 5_000L, "입금 메모")
        );

        assertEquals(TransferErrorCode.ACCOUNT_NOT_FOUND, exception.getErrorCode());
        verify(transactionHistoryRepository, never()).save(any());
    }

    @Test
    void transfer_success_changesBalancesAndSavesTwoHistories() {
        User sender = user();
        User receiver = user();
        when(sender.getName()).thenReturn("송금자");
        when(receiver.getName()).thenReturn("수취자");

        Account senderAccount = account(1L, sender, "100200300001", 100_000L);
        Account receiverAccount = account(2L, receiver, "100200300002", 10_000L);
        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByAccountNumber("100200300002")).thenReturn(Optional.of(receiverAccount));
        when(transactionHistoryRepository.save(any(TransactionHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransferRes response = transferService.transfer(1L, "100200300002", 50_000L, "점심값");

        assertEquals(50_000L, senderAccount.getBalance());
        assertEquals(60_000L, receiverAccount.getBalance());
        assertEquals(TransferStatus.SUCCESS, response.status());
        assertEquals(1L, response.senderAccountId());
        assertEquals("100200300001", response.senderAccountNumber());
        assertEquals("100200300002", response.receiverAccountNumber());
        assertEquals(50_000L, response.amount());
        assertEquals(50_000L, response.senderBalanceAfter());
        assertEquals("점심값", response.memo());
        assertNotNull(response.transferredAt());

        ArgumentCaptor<TransactionHistory> historyCaptor = ArgumentCaptor.forClass(TransactionHistory.class);
        verify(transactionHistoryRepository, times(2)).save(historyCaptor.capture());
        verify(transferRepository, never()).save(any(Transfer.class));

        List<TransactionHistory> histories = historyCaptor.getAllValues();
        TransactionHistory senderHistory = histories.get(0);
        TransactionHistory receiverHistory = histories.get(1);

        assertSame(senderAccount, senderHistory.getAccount());
        assertEquals(TransactionType.TRANSFER, senderHistory.getType());
        assertEquals(TransactionDirection.OUT, senderHistory.getDirection());
        assertEquals(100_000L, senderHistory.getBalanceBefore());
        assertEquals(50_000L, senderHistory.getBalanceAfter());
        assertEquals("100200300002", senderHistory.getCounterpartyAccountNumber());
        assertEquals("수취자", senderHistory.getCounterpartyName());

        assertSame(receiverAccount, receiverHistory.getAccount());
        assertEquals(TransactionType.TRANSFER, receiverHistory.getType());
        assertEquals(TransactionDirection.IN, receiverHistory.getDirection());
        assertEquals(10_000L, receiverHistory.getBalanceBefore());
        assertEquals(60_000L, receiverHistory.getBalanceAfter());
        assertEquals("100200300001", receiverHistory.getCounterpartyAccountNumber());
        assertEquals("송금자", receiverHistory.getCounterpartyName());
    }

    @Test
    void transfer_sameAccount_throwsInvalidInputValue() {
        Account account = account(1L, user(), "100200300001", 100_000L);
        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));
        when(accountRepository.findByAccountNumber("100200300001")).thenReturn(Optional.of(account));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferService.transfer(1L, "100200300001", 50_000L, "점심값")
        );

        assertEquals(TransferErrorCode.INVALID_INPUT_VALUE, exception.getErrorCode());
        verify(transactionHistoryRepository, never()).save(any());
    }

    private Account account(Long id, User user, String accountNumber, Long balance) {
        Account account = Account.create(user, accountNumber, "테스트 계좌", AccountType.DEPOSIT);
        account.deposit(balance);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private User user() {
        return mock(User.class);
    }
}
