package com.team10.backend.domain.transfer.service;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.repository.TransactionHistoryRepository;
import com.team10.backend.domain.transaction.type.TransactionDirection;
import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.domain.transfer.dto.res.TopUpRes;
import com.team10.backend.domain.transfer.dto.res.TransferRes;
import com.team10.backend.domain.transfer.entity.Transfer;
import com.team10.backend.domain.transfer.event.TransferFailedEvent;
import com.team10.backend.domain.transfer.exception.TransferErrorCode;
import com.team10.backend.domain.transfer.repository.TransferRepository;
import com.team10.backend.domain.transfer.type.TransferStatus;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferBusinessServiceTest {

    @Mock
    private TransactionHistoryRepository transactionHistoryRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private TransferBusinessService transferBusinessService;

    @Test
    @DisplayName("입금 성공 시 잔액을 증가시키고 입금 거래내역을 저장한다")
    void executeTopUp_success_increasesBalanceAndSavesDepositHistory() {
        Account account = account(1L, user(), "100200300001", 10_000L);
        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));
        when(transactionHistoryRepository.save(any(TransactionHistory.class)))
                .thenAnswer(invocation -> {
                    TransactionHistory history = invocation.getArgument(0);
                    ReflectionTestUtils.setField(history, "id", 100L);
                    return history;
                });

        TopUpRes response = transferBusinessService.executeTopUp(1L, 1L, 5_000L, "입금 메모");

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
    @DisplayName("입금 금액이 유효하지 않으면 INVALID_INPUT_VALUE 예외를 발생시킨다")
    void executeTopUp_invalidAmount_throwsInvalidInputValue() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferBusinessService.executeTopUp(1L, 1L, 0L, "입금 메모")
        );

        assertEquals(TransferErrorCode.INVALID_INPUT_VALUE, exception.getErrorCode());
        verify(accountRepository, never()).findByIdAndUserIdForUpdate(any(), any());
        verify(transactionHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("입금 대상 계좌를 찾을 수 없으면 ACCOUNT_NOT_FOUND 예외를 발생시킨다")
    void executeTopUp_accountNotFound_throwsAccountNotFound() {
        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferBusinessService.executeTopUp(1L, 1L, 5_000L, "입금 메모")
        );

        assertEquals(TransferErrorCode.ACCOUNT_NOT_FOUND, exception.getErrorCode());
        verify(transactionHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("예금 계좌는 일반 입금할 수 없다")
    void executeTopUp_savingDepositAccount_throwsInvalidAccountType() {
        Account account = account(1L, user(), "100200300001", 10_000L, AccountType.SAVING_DEPOSIT);
        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferBusinessService.executeTopUp(1L, 1L, 5_000L, "예금 입금")
        );

        assertEquals(TransferErrorCode.INVALID_ACCOUNT_TYPE, exception.getErrorCode());
        assertEquals(10_000L, account.getBalance());
        verify(transactionHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("적금 계좌는 일반 입금할 수 없다")
    void executeTopUp_savingInstallmentAccount_throwsInvalidAccountType() {
        Account account = account(1L, user(), "100200300001", 10_000L, AccountType.SAVING_INSTALLMENT);
        when(accountRepository.findByIdAndUserIdForUpdate(1L, 1L)).thenReturn(Optional.of(account));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferBusinessService.executeTopUp(1L, 1L, 5_000L, "적금 입금")
        );

        assertEquals(TransferErrorCode.INVALID_ACCOUNT_TYPE, exception.getErrorCode());
        assertEquals(10_000L, account.getBalance());
        verify(transactionHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("송금 성공 시 양쪽 계좌 잔액을 변경하고 송금 거래내역 두 건을 저장한다")
    void executeTransfer_success_changesBalancesAndSavesTwoHistories() {
        User sender = user();
        User receiver = user();
        when(sender.getId()).thenReturn(1L);
        when(sender.getName()).thenReturn("송금자");
        when(receiver.getName()).thenReturn("수취자");

        Account senderAccount = account(1L, sender, "100200300001", 100_000L);
        Account receiverAccount = account(2L, receiver, "100200300002", 10_000L);
        when(passwordEncoder.matches("123456", "encoded-password")).thenReturn(true);
        when(accountRepository.findIdByAccountNumber("100200300002")).thenReturn(Optional.of(2L));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));
        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(invocation -> {
                    Transfer transfer = invocation.getArgument(0);
                    ReflectionTestUtils.setField(transfer, "id", 20L);
                    return transfer;
                });
        when(transactionHistoryRepository.save(any(TransactionHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransferRes response = transferBusinessService.executeTransfer(1L, 1L, "100200300002", "123456", 50_000L, "점심값");

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
        verify(transferRepository).save(any(Transfer.class));

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
    @DisplayName("같은 계좌로 송금하면 INVALID_INPUT_VALUE 예외를 발생시킨다")
    void executeTransfer_sameAccount_throwsInvalidInputValue() {
        User user = user();
        account(1L, user, "100200300001", 100_000L);
        when(accountRepository.findIdByAccountNumber("100200300001")).thenReturn(Optional.of(1L));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferBusinessService.executeTransfer(1L, 1L, "100200300001", "123456", 50_000L, "점심값")
        );

        assertEquals(TransferErrorCode.INVALID_INPUT_VALUE, exception.getErrorCode());
        verify(transactionHistoryRepository, never()).save(any());
        verify(transferRepository, never()).save(any());
    }

    @Test
    @DisplayName("출금 계좌 비밀번호가 일치하지 않으면 송금에 실패한다")
    void executeTransfer_passwordMismatch_throwsAccountPasswordMismatch() {
        User sender = user();
        User receiver = user();
        when(sender.getId()).thenReturn(1L);

        Account senderAccount = account(1L, sender, "100200300001", 100_000L);
        Account receiverAccount = account(2L, receiver, "100200300002", 10_000L);
        when(passwordEncoder.matches("000000", "encoded-password")).thenReturn(false);
        when(accountRepository.findIdByAccountNumber("100200300002")).thenReturn(Optional.of(2L));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferBusinessService.executeTransfer(1L, 1L, "100200300002", "000000", 50_000L, "비밀번호 불일치")
        );

        assertEquals(TransferErrorCode.ACCOUNT_PASSWORD_MISMATCH, exception.getErrorCode());
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(transactionHistoryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("입출금계좌가 아닌 계좌는 송금 출금 계좌로 사용할 수 없다")
    void executeTransfer_savingSenderAccount_throwsInvalidAccountType() {
        User sender = user();
        User receiver = user();
        when(sender.getId()).thenReturn(1L);

        Account senderAccount = account(1L, sender, "100200300001", 100_000L, AccountType.SAVING_DEPOSIT);
        Account receiverAccount = account(2L, receiver, "100200300002", 10_000L);
        when(accountRepository.findIdByAccountNumber("100200300002")).thenReturn(Optional.of(2L));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferBusinessService.executeTransfer(1L, 1L, "100200300002", "123456", 50_000L, "예적금 출금")
        );

        assertEquals(TransferErrorCode.INVALID_ACCOUNT_TYPE, exception.getErrorCode());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(transactionHistoryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }


    @Test
    @DisplayName("예금/적금 계좌는 일반 송금으로 입금받을 수 없다")
    void executeTransfer_savingReceiverAccount_throwsInvalidAccountType() {
        User sender = user();
        User receiver = user();
        when(sender.getId()).thenReturn(1L);

        Account senderAccount = account(1L, sender, "100200300001", 100_000L);
        Account receiverAccount = account(2L, receiver, "100200300002", 10_000L, AccountType.SAVING_INSTALLMENT);
        when(accountRepository.findIdByAccountNumber("100200300002")).thenReturn(Optional.of(2L));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferBusinessService.executeTransfer(1L, 1L, "100200300002", "123456", 50_000L, "예적금 입금")
        );

        assertEquals(TransferErrorCode.INVALID_ACCOUNT_TYPE, exception.getErrorCode());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(transactionHistoryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("출금 계좌 비밀번호가 설정되지 않으면 송금에 실패한다")
    void executeTransfer_passwordNotSet_throwsAccountPasswordNotSet() {
        User sender = user();
        User receiver = user();
        when(sender.getId()).thenReturn(1L);

        Account senderAccount = account(1L, sender, "100200300001", 100_000L);
        ReflectionTestUtils.setField(senderAccount, "accountPasswordHash", null);
        Account receiverAccount = account(2L, receiver, "100200300002", 10_000L);
        when(accountRepository.findIdByAccountNumber("100200300002")).thenReturn(Optional.of(2L));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferBusinessService.executeTransfer(1L, 1L, "100200300002", "123456", 50_000L, "비밀번호 미설정")
        );

        assertEquals(TransferErrorCode.ACCOUNT_PASSWORD_NOT_SET, exception.getErrorCode());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(transactionHistoryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("잔액 부족 송금은 실패 송금 이벤트를 발행하고 INSUFFICIENT_BALANCE 예외를 발생시킨다")
    void executeTransfer_insufficientBalance_publishesFailedEvent() {
        User sender = user();
        User receiver = user();
        when(sender.getId()).thenReturn(1L);

        Account senderAccount = account(1L, sender, "100200300001", 10_000L);
        Account receiverAccount = account(2L, receiver, "100200300002", 10_000L);
        when(passwordEncoder.matches("123456", "encoded-password")).thenReturn(true);
        when(accountRepository.findIdByAccountNumber("100200300002")).thenReturn(Optional.of(2L));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(receiverAccount));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferBusinessService.executeTransfer(1L, 1L, "100200300002", "123456", 50_000L, "잔액 부족")
        );

        ArgumentCaptor<TransferFailedEvent> eventCaptor = ArgumentCaptor.forClass(TransferFailedEvent.class);
        assertEquals(TransferErrorCode.INSUFFICIENT_BALANCE, exception.getErrorCode());
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(transactionHistoryRepository, never()).save(any());

        TransferFailedEvent event = eventCaptor.getValue();
        assertEquals(1L, event.senderAccountId());
        assertEquals(2L, event.receiverAccountId());
        assertEquals(50_000L, event.amount());
        assertEquals("잔액 부족", event.memo());
    }

    private Account account(Long id, User user, String accountNumber, Long balance) {
        return account(id, user, accountNumber, balance, AccountType.DEPOSIT);
    }

    private Account account(Long id, User user, String accountNumber, Long balance, AccountType accountType) {
        Account account = Account.create(user, accountNumber, "테스트 계좌", accountType);
        account.changePassword("encoded-password");
        account.deposit(balance);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private User user() {
        return mock(User.class);
    }
}
