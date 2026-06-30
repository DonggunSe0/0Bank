package com.team10.backend.domain.transfer.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.repository.TransactionHistoryRepository;
import com.team10.backend.domain.transaction.type.TransactionDirection;
import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.domain.transfer.dto.req.TransferReq;
import com.team10.backend.domain.transfer.repository.TransferRepository;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.idempotency.repository.IdempotencyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferConcurrencyE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private TransactionHistoryRepository transactionHistoryRepository;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        configureLockTimeout();
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("동시 송금 E2E - 같은 출금 계좌에서 병렬 송금이 발생해도 최종 잔액과 거래내역이 일관된다")
    void concurrentTransfers_sameSender_persistsConsistentBalancesAndHistories() throws Exception {
        // given 1. 실제 사용자와 계좌를 DB에 준비한다.
        // - 송금자 계좌: 100,000원
        // - 수취자 계좌: 0원
        // 20개 요청이 각각 1,000원씩 동시에 송금되면 최종적으로 20,000원만 이동해야 한다.
        User sender = saveUser("sender-concurrent@example.com", "송금자");
        User receiver = saveUser("receiver-concurrent@example.com", "수취자");
        Account senderAccount = saveAccount(sender, "100200300051", 100_000L);
        Account receiverAccount = saveAccount(receiver, "100200300052", 0L);

        int requestCount = 20;
        long amount = 1_000L;
        AtomicInteger keySequence = new AtomicInteger();

        // when. 같은 출금 계좌에서 서로 다른 멱등성 키를 가진 송금 요청 20개를 동시에 발사한다.
        // 계좌 조회는 비관적 락을 사용하므로, 모든 요청이 성공하되 잔액 갱신은 순차적으로 정합성을 유지해야 한다.
        List<Throwable> failures = runConcurrently(
                requestCount,
                () -> {
                    int sequence = keySequence.incrementAndGet();
                    TransferReq request = new TransferReq(
                            senderAccount.getId(),
                            receiverAccount.getAccountNumber(),
                            "123456",
                            amount,
                            "동시 송금"
                    );

                    mockMvc.perform(post("/api/v1/transfers")
                                    .with(authentication(authenticatedUser(sender.getId())))
                                    .header("Idempotency-Key", "concurrent-transfer-" + sequence)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.status").value("SUCCESS"))
                            .andExpect(jsonPath("$.amount").value(amount));
                }
        );

        entityManager.clear();

        // then 1. 병렬 요청 중 예외, 타임아웃, 데드락이 없어야 한다.
        assertNoConcurrentFailures(failures);

        // then 2. 최종 잔액은 20회 송금 총액만큼 정확히 이동해야 한다.
        Account savedSenderAccount = accountRepository.findById(senderAccount.getId()).orElseThrow();
        Account savedReceiverAccount = accountRepository.findById(receiverAccount.getId()).orElseThrow();

        assertEquals(100_000L - requestCount * amount, savedSenderAccount.getBalance());
        assertEquals(requestCount * amount, savedReceiverAccount.getBalance());

        // then 3. 실제 성공 송금은 요청 수와 동일하게 20건이어야 한다.
        assertEquals(requestCount, transferRepository.count());

        // then 4. 거래내역은 송금마다 OUT/IN 한 쌍씩 총 40건이어야 한다.
        List<TransactionHistory> histories = transactionHistoryRepository.findAll();

        assertEquals(requestCount * 2, histories.size());
        assertEquals(requestCount, histories.stream()
                .filter(history -> history.getType() == TransactionType.TRANSFER)
                .filter(history -> history.getDirection() == TransactionDirection.OUT)
                .filter(history -> history.getAccount().getId().equals(senderAccount.getId()))
                .filter(history -> history.getAmount().equals(amount))
                .count());
        assertEquals(requestCount, histories.stream()
                .filter(history -> history.getType() == TransactionType.TRANSFER)
                .filter(history -> history.getDirection() == TransactionDirection.IN)
                .filter(history -> history.getAccount().getId().equals(receiverAccount.getId()))
                .filter(history -> history.getAmount().equals(amount))
                .count());

        // then 5. 모든 요청은 서로 다른 멱등성 키를 사용했으므로 멱등성 레코드도 요청 수만큼 저장되어야 한다.
        assertEquals(requestCount, idempotencyRepository.count());
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
        assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));

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

    private UsernamePasswordAuthenticationToken authenticatedUser(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
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

    private void configureLockTimeout() {
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createNativeQuery("SET DEFAULT_LOCK_TIMEOUT 30000").executeUpdate()
        );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
