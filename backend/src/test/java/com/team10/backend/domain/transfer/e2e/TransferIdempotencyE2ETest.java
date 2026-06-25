package com.team10.backend.domain.transfer.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.transaction.repository.TransactionHistoryRepository;
import com.team10.backend.domain.transfer.dto.req.DepositReq;
import com.team10.backend.domain.transfer.dto.req.TransferReq;
import com.team10.backend.domain.transfer.repository.TransferRepository;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.idempotency.entity.Idempotency;
import com.team10.backend.global.idempotency.repository.IdempotencyRepository;
import com.team10.backend.global.idempotency.type.IdempotencyStatus;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferIdempotencyE2ETest {

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
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("멱등성 재시도 E2E - 같은 키와 같은 요청은 최초 응답을 재사용하고 중복 송금하지 않는다")
    void transfer_sameIdempotencyKeyAndSameRequest_replaysFirstResponseWithoutDuplicateTransfer() throws Exception {
        // given 1. 실제 사용자와 계좌를 DB에 준비한다.
        // - 송금자 계좌: 100,000원
        // - 수취자 계좌: 10,000원
        User sender = saveUser("sender@example.com", "송금자");
        User receiver = saveUser("receiver@example.com", "수취자");
        Account senderAccount = saveAccount(sender, "100200300001", 100_000L);
        Account receiverAccount = saveAccount(receiver, "100200300002", 10_000L);

        String retryKey = "idempotency-retry-key";
        TransferReq firstRequest = new TransferReq(
                senderAccount.getId(),
                receiverAccount.getAccountNumber(),
                "123456",
                50_000L,
                "점심값"
        );

        // when 1. 멱등성 키를 포함해 1차 송금을 요청한다.
        // 이 요청은 실제 송금을 수행하고, 응답 본문을 멱등성 레코드에 저장해야 한다.
        String firstResponse = mockMvc.perform(post("/api/v1/transfers")
                        .with(authentication(authenticatedUser(sender.getId())))
                        .header("Idempotency-Key", retryKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(50_000L))
                .andExpect(jsonPath("$.senderBalanceAfter").value(50_000L))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // when 2. 같은 계좌 사이에 다른 송금을 한 번 더 수행한다.
        // 이후 재시도가 현재 잔액 기준으로 새로 계산되는지, 최초 응답을 replay하는지 구분하기 위한 장치다.
        TransferReq laterRequest = new TransferReq(
                senderAccount.getId(),
                receiverAccount.getAccountNumber(),
                "123456",
                20_000L,
                "후속 송금"
        );

        mockMvc.perform(post("/api/v1/transfers")
                        .with(authentication(authenticatedUser(sender.getId())))
                        .header("Idempotency-Key", "idempotency-later-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(laterRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderBalanceAfter").value(30_000L));

        // when 3. 1차 요청과 동일한 키, 동일한 payload로 재시도한다.
        // 정상 멱등성 동작이라면 실제 송금을 다시 수행하지 않고 1차 응답을 그대로 반환해야 한다.
        String retryResponse = mockMvc.perform(post("/api/v1/transfers")
                        .with(authentication(authenticatedUser(sender.getId())))
                        .header("Idempotency-Key", retryKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(50_000L))
                .andExpect(jsonPath("$.senderBalanceAfter").value(50_000L))
                .andReturn()
                .getResponse()
                .getContentAsString();

        entityManager.clear();

        // then 1. 재시도 응답은 1차 응답과 완전히 같아야 한다.
        assertEquals(firstResponse, retryResponse);

        // then 2. 실제 잔액은 1차 송금 50,000원과 후속 송금 20,000원만 반영되어야 한다.
        Account savedSenderAccount = accountRepository.findById(senderAccount.getId()).orElseThrow();
        Account savedReceiverAccount = accountRepository.findById(receiverAccount.getId()).orElseThrow();

        assertEquals(30_000L, savedSenderAccount.getBalance());
        assertEquals(80_000L, savedReceiverAccount.getBalance());

        // then 3. 재시도는 새 송금과 새 거래내역을 만들면 안 된다.
        // 실제 송금은 2건, 거래내역은 각 송금당 OUT/IN 2건씩 총 4건이어야 한다.
        assertEquals(2, transferRepository.count());
        assertEquals(4, transactionHistoryRepository.count());

        // then 4. 재시도 대상 멱등성 레코드는 SUCCESS 상태와 저장 응답을 유지해야 한다.
        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(sender.getId(), retryKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.SUCCESS, idempotency.getStatus());
        assertNotNull(idempotency.getResponseBody());
        assertNotNull(idempotency.getCompletedAt());
    }

    @Test
    @DisplayName("멱등성 충돌 E2E - 같은 키와 다른 요청은 409를 반환하고 추가 송금을 처리하지 않는다")
    void transfer_sameIdempotencyKeyAndDifferentRequest_returnsConflictWithoutAdditionalTransfer() throws Exception {
        // given 1. 실제 사용자와 계좌를 DB에 준비한다.
        // - 송금자 계좌: 100,000원
        // - 수취자 계좌: 10,000원
        User sender = saveUser("sender-conflict@example.com", "송금자");
        User receiver = saveUser("receiver-conflict@example.com", "수취자");
        Account senderAccount = saveAccount(sender, "100200300011", 100_000L);
        Account receiverAccount = saveAccount(receiver, "100200300012", 10_000L);

        String conflictKey = "idempotency-conflict-key";
        TransferReq firstRequest = new TransferReq(
                senderAccount.getId(),
                receiverAccount.getAccountNumber(),
                "123456",
                50_000L,
                "점심값"
        );

        // when 1. 멱등성 키를 포함해 1차 송금을 성공시킨다.
        // 이 시점의 요청 해시는 key + senderAccountId + receiverAccountNumber + amount + memo 조합으로 저장된다.
        mockMvc.perform(post("/api/v1/transfers")
                        .with(authentication(authenticatedUser(sender.getId())))
                        .header("Idempotency-Key", conflictKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amount").value(50_000L))
                .andExpect(jsonPath("$.senderBalanceAfter").value(50_000L));

        // when 2. 같은 멱등성 키로 금액만 다른 요청을 다시 보낸다.
        // 같은 키에 다른 payload가 들어왔으므로 replay가 아니라 충돌로 거부되어야 한다.
        TransferReq differentRequest = new TransferReq(
                senderAccount.getId(),
                receiverAccount.getAccountNumber(),
                "123456",
                60_000L,
                "점심값"
        );

        mockMvc.perform(post("/api/v1/transfers")
                        .with(authentication(authenticatedUser(sender.getId())))
                        .header("Idempotency-Key", conflictKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(differentRequest)))
                // then 1. API는 멱등성 요청 충돌을 명확히 반환해야 한다.
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_REQUEST_CONFLICT"))
                .andExpect(jsonPath("$.message").value("같은 키인데 요청 내용이 다릅니다."));

        entityManager.clear();

        // then 2. 충돌 요청은 계좌 잔액을 절대 변경하면 안 된다.
        // 1차 송금 50,000원만 반영된 상태가 유지되어야 한다.
        Account savedSenderAccount = accountRepository.findById(senderAccount.getId()).orElseThrow();
        Account savedReceiverAccount = accountRepository.findById(receiverAccount.getId()).orElseThrow();

        assertEquals(50_000L, savedSenderAccount.getBalance());
        assertEquals(60_000L, savedReceiverAccount.getBalance());

        // then 3. 충돌 요청은 새 송금과 새 거래내역을 만들면 안 된다.
        // 성공한 1차 송금 1건과 OUT/IN 거래내역 2건만 남아야 한다.
        assertEquals(1, transferRepository.count());
        assertEquals(2, transactionHistoryRepository.count());

        // then 4. 기존 멱등성 레코드는 SUCCESS 상태를 유지해야 한다.
        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(sender.getId(), conflictKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.SUCCESS, idempotency.getStatus());
        assertNotNull(idempotency.getResponseBody());
        assertNotNull(idempotency.getCompletedAt());
    }

    @Test
    @DisplayName("동시 동일 멱등성 키 E2E - 같은 키의 병렬 요청은 실제 송금을 1회만 처리한다")
    void transfer_sameIdempotencyKeyConcurrently_processesTransferOnlyOnce() throws Exception {
        // given 1. 실제 사용자와 계좌를 DB에 준비한다.
        // - 송금자 계좌: 100,000원
        // - 수취자 계좌: 10,000원
        User sender = saveUser("sender-same-key@example.com", "송금자");
        User receiver = saveUser("receiver-same-key@example.com", "수취자");
        Account senderAccount = saveAccount(sender, "100200300061", 100_000L);
        Account receiverAccount = saveAccount(receiver, "100200300062", 10_000L);

        String sameKey = "idempotency-concurrent-same-key";
        TransferReq request = new TransferReq(
                senderAccount.getId(),
                receiverAccount.getAccountNumber(),
                "123456",
                50_000L,
                "동시 동일 키 송금"
        );

        // when. 같은 멱등성 키와 같은 payload를 가진 요청 2개를 동시에 발사한다.
        // 먼저 도착한 요청은 PROCESSING 레코드를 선점하고 실제 송금을 수행한다.
        // 나중 요청은 타이밍에 따라 PROCESSING 충돌을 받거나, 선행 요청 완료 후 저장 응답을 replay 받을 수 있다.
        List<ConcurrentResponse> responses = runTwoRequestsConcurrently(() ->
                performTransfer(sender.getId(), sameKey, request)
        );

        entityManager.clear();

        // then 1. 두 요청 중 적어도 하나는 실제 송금 성공 응답을 받아야 한다.
        long successCount = responses.stream()
                .filter(response -> response.status() == 200)
                .count();
        long processingConflictCount = responses.stream()
                .filter(response -> response.status() == 409)
                .filter(response -> errorCode(response).equals("IDEMPOTENCY_REQUEST_PROCESSING"))
                .count();

        assertTrue(successCount >= 1);
        assertEquals(2, successCount + processingConflictCount);

        // then 2. 성공 응답이 2개라면 두 번째 성공은 replay이므로 응답 본문이 동일해야 한다.
        List<String> successBodies = responses.stream()
                .filter(response -> response.status() == 200)
                .map(ConcurrentResponse::body)
                .toList();

        if (successBodies.size() == 2) {
            assertEquals(successBodies.get(0), successBodies.get(1));
        }

        // then 3. 실제 DB 반영은 어떤 응답 타이밍에서도 송금 1회만 허용되어야 한다.
        Account savedSenderAccount = accountRepository.findById(senderAccount.getId()).orElseThrow();
        Account savedReceiverAccount = accountRepository.findById(receiverAccount.getId()).orElseThrow();

        assertEquals(50_000L, savedSenderAccount.getBalance());
        assertEquals(60_000L, savedReceiverAccount.getBalance());
        assertEquals(1, transferRepository.count());
        assertEquals(2, transactionHistoryRepository.count());

        // then 4. 같은 사용자와 같은 키의 멱등성 레코드는 1건만 존재하고, 최종 상태는 SUCCESS여야 한다.
        assertEquals(1, idempotencyRepository.count());

        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(sender.getId(), sameKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.SUCCESS, idempotency.getStatus());
        assertNotNull(idempotency.getResponseBody());
        assertNotNull(idempotency.getCompletedAt());
    }

    @Test
    @DisplayName("입금 멱등성 재시도 E2E - 같은 키와 같은 요청은 최초 응답을 재사용하고 중복 입금하지 않는다")
    void topUp_sameIdempotencyKeyAndSameRequest_replaysFirstResponseWithoutDuplicateDeposit() throws Exception {
        // given. 실제 사용자와 입금 대상 계좌를 DB에 준비한다.
        // - 최초 잔액: 10,000원
        User user = saveUser("topup-retry@example.com", "입금자");
        Account account = saveAccount(user, "100200300201", 10_000L);

        String retryKey = "topup-idempotency-retry-key";
        DepositReq firstRequest = new DepositReq(
                account.getId(),
                5_000L,
                "초기 입금"
        );

        // when 1. 멱등성 키를 포함해 1차 입금을 요청한다.
        // 이 요청은 실제 입금을 수행하고, 응답 본문을 멱등성 레코드에 저장해야 한다.
        String firstResponse = mockMvc.perform(post("/api/v1/transfers/topUp")
                        .with(authentication(authenticatedUser(user.getId())))
                        .header("Idempotency-Key", retryKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(5_000L))
                .andExpect(jsonPath("$.balanceBefore").value(10_000L))
                .andExpect(jsonPath("$.balanceAfter").value(15_000L))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // when 2. 같은 계좌에 다른 입금을 한 번 더 수행한다.
        // 이후 재시도가 현재 잔액 기준으로 새로 계산되는지, 최초 응답을 replay하는지 구분하기 위한 장치다.
        DepositReq laterRequest = new DepositReq(
                account.getId(),
                2_000L,
                "후속 입금"
        );

        mockMvc.perform(post("/api/v1/transfers/topUp")
                        .with(authentication(authenticatedUser(user.getId())))
                        .header("Idempotency-Key", "topup-idempotency-later-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(laterRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAfter").value(17_000L));

        // when 3. 1차 요청과 동일한 키, 동일한 payload로 재시도한다.
        // 정상 멱등성 동작이라면 실제 입금을 다시 수행하지 않고 1차 응답을 그대로 반환해야 한다.
        String retryResponse = mockMvc.perform(post("/api/v1/transfers/topUp")
                        .with(authentication(authenticatedUser(user.getId())))
                        .header("Idempotency-Key", retryKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(5_000L))
                .andExpect(jsonPath("$.balanceBefore").value(10_000L))
                .andExpect(jsonPath("$.balanceAfter").value(15_000L))
                .andReturn()
                .getResponse()
                .getContentAsString();

        entityManager.clear();

        // then 1. 재시도 응답은 1차 응답과 완전히 같아야 한다.
        assertEquals(firstResponse, retryResponse);

        // then 2. 실제 잔액은 1차 입금 5,000원과 후속 입금 2,000원만 반영되어야 한다.
        Account savedAccount = accountRepository.findById(account.getId()).orElseThrow();
        assertEquals(17_000L, savedAccount.getBalance());

        // then 3. 재시도는 새 거래내역을 만들면 안 된다.
        assertEquals(0, transferRepository.count());
        assertEquals(2, transactionHistoryRepository.count());

        // then 4. 재시도 대상 멱등성 레코드는 SUCCESS 상태와 저장 응답을 유지해야 한다.
        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(user.getId(), retryKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.SUCCESS, idempotency.getStatus());
        assertNotNull(idempotency.getResponseBody());
        assertNotNull(idempotency.getCompletedAt());
    }

    @Test
    @DisplayName("입금 멱등성 충돌 E2E - 같은 키와 다른 요청은 409를 반환하고 추가 입금하지 않는다")
    void topUp_sameIdempotencyKeyAndDifferentRequest_returnsConflictWithoutAdditionalDeposit() throws Exception {
        // given. 실제 사용자와 입금 대상 계좌를 DB에 준비한다.
        // - 최초 잔액: 10,000원
        User user = saveUser("topup-conflict@example.com", "입금자");
        Account account = saveAccount(user, "100200300202", 10_000L);

        String conflictKey = "topup-idempotency-conflict-key";
        DepositReq firstRequest = new DepositReq(
                account.getId(),
                5_000L,
                "초기 입금"
        );

        // when 1. 멱등성 키를 포함해 1차 입금을 성공시킨다.
        // 이 시점의 요청 해시는 key + accountId + amount + memo 조합으로 저장된다.
        mockMvc.perform(post("/api/v1/transfers/topUp")
                        .with(authentication(authenticatedUser(user.getId())))
                        .header("Idempotency-Key", conflictKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(5_000L))
                .andExpect(jsonPath("$.balanceAfter").value(15_000L));

        // when 2. 같은 멱등성 키로 금액만 다른 입금 요청을 다시 보낸다.
        // 같은 키에 다른 payload가 들어왔으므로 replay가 아니라 충돌로 거부되어야 한다.
        DepositReq differentRequest = new DepositReq(
                account.getId(),
                6_000L,
                "초기 입금"
        );

        mockMvc.perform(post("/api/v1/transfers/topUp")
                        .with(authentication(authenticatedUser(user.getId())))
                        .header("Idempotency-Key", conflictKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(differentRequest)))
                // then 1. API는 멱등성 요청 충돌을 명확히 반환해야 한다.
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_REQUEST_CONFLICT"))
                .andExpect(jsonPath("$.message").value("같은 키인데 요청 내용이 다릅니다."));

        entityManager.clear();

        // then 2. 충돌 요청은 계좌 잔액을 절대 변경하면 안 된다.
        // 1차 입금 5,000원만 반영된 상태가 유지되어야 한다.
        Account savedAccount = accountRepository.findById(account.getId()).orElseThrow();
        assertEquals(15_000L, savedAccount.getBalance());

        // then 3. 충돌 요청은 새 거래내역을 만들면 안 된다.
        assertEquals(0, transferRepository.count());
        assertEquals(1, transactionHistoryRepository.count());

        // then 4. 기존 멱등성 레코드는 SUCCESS 상태를 유지해야 한다.
        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(user.getId(), conflictKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.SUCCESS, idempotency.getStatus());
        assertNotNull(idempotency.getResponseBody());
        assertNotNull(idempotency.getCompletedAt());
    }

    private ConcurrentResponse performTransfer(Long userId, String idempotencyKey, TransferReq request) throws Exception {
        var result = mockMvc.perform(post("/api/v1/transfers")
                        .with(authentication(authenticatedUser(userId)))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn()
                .getResponse();

        return new ConcurrentResponse(result.getStatus(), result.getContentAsString());
    }

    private List<ConcurrentResponse> runTwoRequestsConcurrently(ThrowingSupplier<ConcurrentResponse> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ConcurrentResponse>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return task.get();
            }));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        List<ConcurrentResponse> responses = new ArrayList<>();
        for (Future<ConcurrentResponse> future : futures) {
            responses.add(future.get());
        }
        return responses;
    }

    private String errorCode(ConcurrentResponse response) {
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode code = root.get("code");
            return code == null ? "" : code.asText();
        } catch (Exception e) {
            return "";
        }
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

    private record ConcurrentResponse(int status, String body) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
