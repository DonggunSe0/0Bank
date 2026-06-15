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
import com.team10.backend.domain.transfer.exception.TransferErrorCode;
import com.team10.backend.domain.transfer.repository.TransferRepository;
import com.team10.backend.domain.transfer.type.TransferStatus;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TransferServiceIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionHistoryRepository transactionHistoryRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("입금 성공 시 실제 DB에 잔액과 입금 거래내역이 저장된다")
    void deposit_success_persistsBalanceAndDepositHistory() {
        Account account = saveAccount(saveUser("sender@example.com", "홍길동"), "100200300001", 10_000L);

        DepositRes response = transferService.deposit(account.getId(), 5_000L, "입금 메모");
        flushAndClear();

        Account savedAccount = accountRepository.findById(account.getId()).orElseThrow();
        List<TransactionHistory> histories = transactionHistoryRepository.findAll();

        assertEquals(15_000L, savedAccount.getBalance());
        assertEquals(1, histories.size());

        TransactionHistory history = histories.getFirst();
        assertEquals(response.transactionId(), history.getId());
        assertEquals(savedAccount.getId(), history.getAccount().getId());
        assertEquals(TransactionType.DEPOSIT, history.getType());
        assertEquals(TransactionDirection.IN, history.getDirection());
        assertEquals(5_000L, history.getAmount());
        assertEquals(10_000L, history.getBalanceBefore());
        assertEquals(15_000L, history.getBalanceAfter());
        assertEquals("입금 메모", history.getMemo());
        assertNotNull(history.getTransactedAt());
    }

    @Test
    @DisplayName("송금 성공 시 실제 DB에 양쪽 잔액, 송금, 거래내역 두 건이 저장된다")
    void transfer_success_persistsBalancesTransferAndTwoHistories() {
        User sender = saveUser("sender@example.com", "송금자");
        User receiver = saveUser("receiver@example.com", "수취자");
        Account senderAccount = saveAccount(sender, "100200300001", 100_000L);
        Account receiverAccount = saveAccount(receiver, "100200300002", 10_000L);

        TransferRes response = transferService.transfer(senderAccount.getId(), "100200300002", 50_000L, "점심값");
        flushAndClear();

        Account savedSenderAccount = accountRepository.findById(senderAccount.getId()).orElseThrow();
        Account savedReceiverAccount = accountRepository.findById(receiverAccount.getId()).orElseThrow();
        List<TransactionHistory> histories = transactionHistoryRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(TransactionHistory::getDirection).reversed())
                .toList();

        assertEquals(50_000L, savedSenderAccount.getBalance());
        assertEquals(60_000L, savedReceiverAccount.getBalance());
        assertEquals(1, transferRepository.count());
        assertEquals(2, histories.size());

        assertNotNull(response.transferId());
        assertEquals(TransferStatus.SUCCESS, response.status());
        assertEquals(senderAccount.getId(), response.senderAccountId());
        assertEquals("100200300001", response.senderAccountNumber());
        assertEquals("100200300002", response.receiverAccountNumber());
        assertEquals(50_000L, response.amount());
        assertEquals(50_000L, response.senderBalanceAfter());
        assertEquals("점심값", response.memo());

        TransactionHistory senderHistory = histories.get(0);
        assertEquals(savedSenderAccount.getId(), senderHistory.getAccount().getId());
        assertEquals(TransactionType.TRANSFER, senderHistory.getType());
        assertEquals(TransactionDirection.OUT, senderHistory.getDirection());
        assertEquals(50_000L, senderHistory.getAmount());
        assertEquals(100_000L, senderHistory.getBalanceBefore());
        assertEquals(50_000L, senderHistory.getBalanceAfter());
        assertEquals("100200300002", senderHistory.getCounterpartyAccountNumber());
        assertEquals("수취자", senderHistory.getCounterpartyName());
        assertEquals(response.transferId(), senderHistory.getTransfer().getId());

        TransactionHistory receiverHistory = histories.get(1);
        assertEquals(savedReceiverAccount.getId(), receiverHistory.getAccount().getId());
        assertEquals(TransactionType.TRANSFER, receiverHistory.getType());
        assertEquals(TransactionDirection.IN, receiverHistory.getDirection());
        assertEquals(50_000L, receiverHistory.getAmount());
        assertEquals(10_000L, receiverHistory.getBalanceBefore());
        assertEquals(60_000L, receiverHistory.getBalanceAfter());
        assertEquals("100200300001", receiverHistory.getCounterpartyAccountNumber());
        assertEquals("송금자", receiverHistory.getCounterpartyName());
        assertEquals(response.transferId(), receiverHistory.getTransfer().getId());
    }

    @Test
    @DisplayName("송금 금액이 유효하지 않으면 DB 변경 없이 INVALID_INPUT_VALUE 예외를 발생시킨다")
    void transfer_invalidAmount_doesNotPersistAnything() {
        Account account = saveAccount(saveUser("sender@example.com", "홍길동"), "100200300001", 10_000L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferService.transfer(account.getId(), "100200300002", 0L, "잘못된 송금")
        );
        flushAndClear();

        Account savedAccount = accountRepository.findById(account.getId()).orElseThrow();
        assertEquals(TransferErrorCode.INVALID_INPUT_VALUE, exception.getErrorCode());
        assertEquals(10_000L, savedAccount.getBalance());
        assertEquals(0, transferRepository.count());
        assertEquals(0, transactionHistoryRepository.count());
    }

    @Test
    @DisplayName("잔액 부족 송금은 잔액과 거래내역 변경 없이 실패 송금 기록을 저장한다")
    void transfer_insufficientBalance_rollsBackBalanceAndHistory() {
        User sender = saveUser("sender@example.com", "송금자");
        User receiver = saveUser("receiver@example.com", "수취자");
        Account senderAccount = saveAccount(sender, "100200300001", 10_000L);
        Account receiverAccount = saveAccount(receiver, "100200300002", 10_000L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferService.transfer(senderAccount.getId(), "100200300002", 50_000L, "잔액 부족")
        );
        flushAndClear();

        Account savedSenderAccount = accountRepository.findById(senderAccount.getId()).orElseThrow();
        Account savedReceiverAccount = accountRepository.findById(receiverAccount.getId()).orElseThrow();
        List<Transfer> transfers = transferRepository.findAll();

        assertEquals(TransferErrorCode.INSUFFICIENT_BALANCE, exception.getErrorCode());
        assertEquals(10_000L, savedSenderAccount.getBalance());
        assertEquals(10_000L, savedReceiverAccount.getBalance());
        assertEquals(1, transfers.size());
        assertEquals(0, transactionHistoryRepository.count());

        Transfer failedTransfer = transfers.getFirst();
        assertEquals(TransferStatus.FAILED, failedTransfer.getStatus());
        assertEquals(senderAccount.getId(), failedTransfer.getSenderAccount().getId());
        assertEquals(receiverAccount.getId(), failedTransfer.getReceiverAccount().getId());
        assertEquals(50_000L, failedTransfer.getAmount());
        assertEquals("잔액 부족", failedTransfer.getMemo());
    }

    @Test
    @DisplayName("같은 계좌에 동시 입금이 발생해도 모든 입금 잔액과 거래내역이 반영된다")
    void concurrentDeposits_sameAccount_persistsAllBalanceAndHistories() throws InterruptedException {
        Account account = saveAccount(saveUser("sender@example.com", "홍길동"), "100200300001", 0L);
        int threadCount = 20;
        long amount = 1_000L;

        List<Throwable> failures = runConcurrently(
                threadCount,
                () -> transferService.deposit(account.getId(), amount, "동시 입금")
        );
        entityManager.clear();

        Account savedAccount = accountRepository.findById(account.getId()).orElseThrow();
        List<TransactionHistory> histories = transactionHistoryRepository.findAll();

        assertNoConcurrentFailures(failures);
        assertEquals(threadCount * amount, savedAccount.getBalance());
        assertEquals(threadCount, histories.size());
        assertTrue(histories.stream().allMatch(history -> history.getType() == TransactionType.DEPOSIT));
        assertTrue(histories.stream().allMatch(history -> history.getDirection() == TransactionDirection.IN));
        assertTrue(histories.stream().allMatch(history -> history.getAmount().equals(amount)));
    }

    @Test
    @DisplayName("같은 송금자 계좌에서 동시 송금이 발생해도 잔액과 거래내역이 일관되게 저장된다")
    void concurrentTransfers_sameSender_persistsConsistentBalancesAndHistories() throws InterruptedException {
        User sender = saveUser("sender@example.com", "송금자");
        User receiver = saveUser("receiver@example.com", "수취자");
        Account senderAccount = saveAccount(sender, "100200300001", 100_000L);
        Account receiverAccount = saveAccount(receiver, "100200300002", 0L);
        int threadCount = 20;
        long amount = 1_000L;

        List<Throwable> failures = runConcurrently(
                threadCount,
                () -> transferService.transfer(senderAccount.getId(), receiverAccount.getAccountNumber(), amount, "동시 송금")
        );
        entityManager.clear();

        Account savedSenderAccount = accountRepository.findById(senderAccount.getId()).orElseThrow();
        Account savedReceiverAccount = accountRepository.findById(receiverAccount.getId()).orElseThrow();
        List<TransactionHistory> histories = transactionHistoryRepository.findAll();

        assertNoConcurrentFailures(failures);
        assertEquals(100_000L - threadCount * amount, savedSenderAccount.getBalance());
        assertEquals(threadCount * amount, savedReceiverAccount.getBalance());
        assertEquals(threadCount, transferRepository.count());
        assertEquals(threadCount * 2, histories.size());
        assertEquals(threadCount, histories.stream()
                .filter(history -> history.getDirection() == TransactionDirection.OUT)
                .count());
        assertEquals(threadCount, histories.stream()
                .filter(history -> history.getDirection() == TransactionDirection.IN)
                .count());
    }

    @Test
    @DisplayName("양방향 교차 송금이 동시에 발생해도 데드락 없이 모든 송금과 거래내역이 저장된다")
    void concurrentTransfers_oppositeDirections_doesNotDeadlockAndPersistsAllHistories() throws InterruptedException {
        User owner = saveUser("sender@example.com", "계좌주");
        Account firstAccount = saveAccount(owner, "100200300001", 100_000L);
        Account secondAccount = saveAccount(owner, "100200300002", 100_000L);
        int threadCount = 20;
        long amount = 1_000L;
        AtomicInteger sequence = new AtomicInteger();

        List<Throwable> failures = runConcurrently(
                threadCount,
                () -> {
                    if (sequence.getAndIncrement() % 2 == 0) {
                        transferService.transfer(firstAccount.getId(), secondAccount.getAccountNumber(), amount, "교차 송금");
                    } else {
                        transferService.transfer(secondAccount.getId(), firstAccount.getAccountNumber(), amount, "교차 송금");
                    }
                }
        );
        entityManager.clear();

        Account savedFirstAccount = accountRepository.findById(firstAccount.getId()).orElseThrow();
        Account savedSecondAccount = accountRepository.findById(secondAccount.getId()).orElseThrow();

        assertNoConcurrentFailures(failures);
        assertEquals(100_000L, savedFirstAccount.getBalance());
        assertEquals(100_000L, savedSecondAccount.getBalance());
        assertEquals(threadCount, transferRepository.count());
        assertEquals(threadCount * 2, transactionHistoryRepository.count());
    }

    private Account saveAccount(User user, String accountNumber, Long balance) {
        Account account = Account.create(user, accountNumber, "테스트 계좌", AccountType.DEPOSIT);
        account.deposit(balance);
        return transactionTemplate.execute(status -> accountRepository.save(account));
    }

    private User saveUser(String email, String name) {
        return transactionTemplate.execute(status -> {
            User user = newUser();
            ReflectionTestUtils.setField(user, "email", email);
            ReflectionTestUtils.setField(user, "password", "password");
            ReflectionTestUtils.setField(user, "name", name);
            ReflectionTestUtils.setField(user, "phoneNumber", "01012345678");
            ReflectionTestUtils.setField(user, "birthDate", LocalDate.of(1990, 1, 1));
            ReflectionTestUtils.setField(user, "identityVerified", true);
            entityManager.persist(user);
            return user;
        });
    }

    private User newUser() {
        try {
            Constructor<User> constructor = User.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("User test fixture creation failed", e);
        }
    }

    private void flushAndClear() {
        entityManager.clear();
    }

    private List<Throwable> runConcurrently(int threadCount, ThrowingRunnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    task.run();
                    return null;
                } catch (Throwable throwable) {
                    return throwable;
                }
            }));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> future : futures) {
            try {
                Throwable failure = future.get();
                if (failure != null) {
                    failures.add(failure);
                }
            } catch (Exception e) {
                failures.add(e);
            }
        }
        return failures;
    }

    private void assertNoConcurrentFailures(List<Throwable> failures) {
        assertTrue(failures.isEmpty(), () -> failures.stream()
                .map(throwable -> throwable.getClass().getSimpleName() + ": " + throwable.getMessage())
                .toList()
                .toString());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
