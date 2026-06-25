package com.team10.backend.domain.transfer.service;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.repository.TransactionHistoryRepository;
import com.team10.backend.domain.transaction.type.TransactionDirection;
import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.domain.transfer.dto.res.TopUpRes;
import com.team10.backend.domain.transfer.exception.TransferErrorCode;
import com.team10.backend.domain.transfer.repository.TransferRepository;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("입금 성공 시 실제 DB에 잔액과 입금 거래내역이 저장된다")
    void deposit_success_persistsBalanceAndDepositHistory() {
        User user = saveUser("sender@example.com", "홍길동");
        Account account = saveAccount(user, "100200300001", 10_000L);

        TopUpRes response = transferService.topUp(user.getId(), account.getId(), 5_000L, "입금 메모");
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
    @DisplayName("송금 금액이 유효하지 않으면 DB 변경 없이 INVALID_INPUT_VALUE 예외를 발생시킨다")
    void transfer_invalidAmount_doesNotPersistAnything() {
        User user = saveUser("sender@example.com", "홍길동");
        Account account = saveAccount(user, "100200300001", 10_000L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transferService.transfer(user.getId(), account.getId(), "100200300002", "123456", 0L, "잘못된 송금")
        );
        flushAndClear();

        Account savedAccount = accountRepository.findById(account.getId()).orElseThrow();
        assertEquals(TransferErrorCode.INVALID_INPUT_VALUE, exception.getErrorCode());
        assertEquals(10_000L, savedAccount.getBalance());
        assertEquals(0, transferRepository.count());
        assertEquals(0, transactionHistoryRepository.count());
    }

    @Test
    @DisplayName("같은 계좌에 동시 입금이 발생해도 모든 입금 잔액과 거래내역이 반영된다")
    void concurrentDeposits_sameAccount_persistsAllBalanceAndHistories() throws InterruptedException {
        User user = saveUser("sender@example.com", "홍길동");
        Account account = saveAccount(user, "100200300001", 0L);
        int threadCount = 20;
        long amount = 1_000L;

        List<Throwable> failures = runConcurrently(
                threadCount,
                () -> transferService.topUp(
                        user.getId(),
                        account.getId(),
                        amount,
                        "동시 입금"
                )
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
                    int currentSequence = sequence.getAndIncrement();
                    if (currentSequence % 2 == 0) {
                        transferService.transfer(owner.getId(), firstAccount.getId(), secondAccount.getAccountNumber(), "123456", amount, "교차 송금");
                    } else {
                        transferService.transfer(owner.getId(), secondAccount.getId(), firstAccount.getAccountNumber(), "123456", amount, "교차 송금");
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
        account.changePassword(passwordEncoder.encode("123456"));
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

    private void cleanDatabase() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM transaction_histories").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM transfers").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM idempotency_keys").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM accounts").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
            entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
            entityManager.clear();
        });
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
