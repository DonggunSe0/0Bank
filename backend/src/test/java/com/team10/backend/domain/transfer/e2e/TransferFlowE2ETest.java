package com.team10.backend.domain.transfer.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.repository.AccountRepository;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.repository.TransactionHistoryRepository;
import com.team10.backend.domain.transaction.type.TransactionDirection;
import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.domain.transfer.dto.req.DepositReq;
import com.team10.backend.domain.transfer.dto.req.TransferReq;
import com.team10.backend.domain.transfer.entity.Transfer;
import com.team10.backend.domain.transfer.repository.TransferRepository;
import com.team10.backend.domain.transfer.type.TransferStatus;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.idempotency.entity.Idempotency;
import com.team10.backend.global.idempotency.repository.IdempotencyRepository;
import com.team10.backend.global.idempotency.type.IdempotencyStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferFlowE2ETest {

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
    @DisplayName("정상 송금 E2E - API 응답, 계좌 잔액, 송금 기록, 양방향 거래내역, 멱등성 성공 상태를 검증한다")
    void transfer_success_persistsConsistentState() throws Exception {
        // given 1. 실제 사용자와 계좌를 DB에 준비한다.
        // - 송금자 계좌: 100,000원
        // - 수취자 계좌: 10,000원
        User sender = saveUser("sender@example.com", "송금자");
        User receiver = saveUser("receiver@example.com", "수취자");
        Account senderAccount = saveAccount(sender, "100200300001", 100_000L);
        Account receiverAccount = saveAccount(receiver, "100200300002", 10_000L);
        String idempotencyKey = "flow-success-key";

        TransferReq request = new TransferReq(
                senderAccount.getId(),
                receiverAccount.getAccountNumber(),
                "123456",
                50_000L,
                "점심값"
        );

        // when. 인증된 송금자 관점에서 실제 송금 API를 호출한다.
        // 컨트롤러 -> 서비스 -> 멱등성 AOP -> 트랜잭션 -> DB 저장 흐름을 모두 통과한다.
        mockMvc.perform(post("/api/v1/transfers")
                        .with(authentication(authenticatedUser(sender.getId())))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // then 1. HTTP 응답은 성공 송금 결과를 반환해야 한다.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.senderAccountId").value(senderAccount.getId()))
                .andExpect(jsonPath("$.senderAccountNumber").value(senderAccount.getAccountNumber()))
                .andExpect(jsonPath("$.receiverAccountNumber").value(receiverAccount.getAccountNumber()))
                .andExpect(jsonPath("$.amount").value(50_000L))
                .andExpect(jsonPath("$.senderBalanceAfter").value(50_000L))
                .andExpect(jsonPath("$.memo").value("점심값"))
                .andExpect(jsonPath("$.transferredAt").exists());

        entityManager.clear();

        // then 2. 계좌 잔액은 원자적으로 반영되어야 한다.
        Account savedSenderAccount = accountRepository.findById(senderAccount.getId()).orElseThrow();
        Account savedReceiverAccount = accountRepository.findById(receiverAccount.getId()).orElseThrow();

        assertEquals(50_000L, savedSenderAccount.getBalance());
        assertEquals(60_000L, savedReceiverAccount.getBalance());

        // then 3. 송금 원장에는 SUCCESS 송금 1건만 저장되어야 한다.
        List<Transfer> transfers = transferRepository.findAll();
        assertEquals(1, transfers.size());

        Transfer transfer = transfers.getFirst();
        assertEquals(TransferStatus.SUCCESS, transfer.getStatus());
        assertEquals(savedSenderAccount.getId(), transfer.getSenderAccount().getId());
        assertEquals(savedReceiverAccount.getId(), transfer.getReceiverAccount().getId());
        assertEquals(50_000L, transfer.getAmount());
        assertEquals("점심값", transfer.getMemo());

        // then 4. 거래내역은 송금자 OUT 1건, 수취자 IN 1건으로 쌍을 이뤄야 한다.
        List<TransactionHistory> histories = transactionHistoryRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(TransactionHistory::getDirection).reversed())
                .toList();

        assertEquals(2, histories.size());

        TransactionHistory senderHistory = histories.get(0);
        assertEquals(savedSenderAccount.getId(), senderHistory.getAccount().getId());
        assertEquals(transfer.getId(), senderHistory.getTransfer().getId());
        assertEquals(TransactionType.TRANSFER, senderHistory.getType());
        assertEquals(TransactionDirection.OUT, senderHistory.getDirection());
        assertEquals(50_000L, senderHistory.getAmount());
        assertEquals(100_000L, senderHistory.getBalanceBefore());
        assertEquals(50_000L, senderHistory.getBalanceAfter());
        assertEquals(receiverAccount.getAccountNumber(), senderHistory.getCounterpartyAccountNumber());
        assertEquals("수취자", senderHistory.getCounterpartyName());
        assertEquals("점심값", senderHistory.getMemo());
        assertNotNull(senderHistory.getTransactedAt());

        TransactionHistory receiverHistory = histories.get(1);
        assertEquals(savedReceiverAccount.getId(), receiverHistory.getAccount().getId());
        assertEquals(transfer.getId(), receiverHistory.getTransfer().getId());
        assertEquals(TransactionType.TRANSFER, receiverHistory.getType());
        assertEquals(TransactionDirection.IN, receiverHistory.getDirection());
        assertEquals(50_000L, receiverHistory.getAmount());
        assertEquals(10_000L, receiverHistory.getBalanceBefore());
        assertEquals(60_000L, receiverHistory.getBalanceAfter());
        assertEquals(senderAccount.getAccountNumber(), receiverHistory.getCounterpartyAccountNumber());
        assertEquals("송금자", receiverHistory.getCounterpartyName());
        assertEquals("점심값", receiverHistory.getMemo());
        assertNotNull(receiverHistory.getTransactedAt());

        // then 5. 멱등성 레코드는 SUCCESS로 완료되고, 재시도 응답에 쓸 responseBody를 보관해야 한다.
        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(sender.getId(), idempotencyKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.SUCCESS, idempotency.getStatus());
        assertNotNull(idempotency.getResponseBody());
        assertNotNull(idempotency.getCompletedAt());
    }

    @Test
    @DisplayName("잔액 부족 E2E - 잔액과 거래내역은 롤백하고 실패 송금 기록과 멱등성 실패 상태를 남긴다")
    void transfer_insufficientBalance_rollsBackBalancesAndHistoriesThenRecordsFailure() throws Exception {
        // given 1. 실제 사용자와 계좌를 DB에 준비한다.
        // - 송금자 계좌: 10,000원
        // - 수취자 계좌: 10,000원
        // 송금 요청 금액은 50,000원이므로 출금 단계에서 잔액 부족이 발생해야 한다.
        User sender = saveUser("sender-insufficient@example.com", "송금자");
        User receiver = saveUser("receiver-insufficient@example.com", "수취자");
        Account senderAccount = saveAccount(sender, "100200300021", 10_000L);
        Account receiverAccount = saveAccount(receiver, "100200300022", 10_000L);
        String idempotencyKey = "flow-insufficient-key";

        TransferReq request = new TransferReq(
                senderAccount.getId(),
                receiverAccount.getAccountNumber(),
                "123456",
                50_000L,
                "잔액 부족"
        );

        // when 1. 인증된 송금자 관점에서 보유 잔액보다 큰 금액을 송금 요청한다.
        // 출금 실패로 비즈니스 트랜잭션은 롤백되고, 실패 이벤트가 별도 트랜잭션으로 기록되어야 한다.
        mockMvc.perform(post("/api/v1/transfers")
                        .with(authentication(authenticatedUser(sender.getId())))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // then 1. API는 잔액 부족 에러를 명확히 반환해야 한다.
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.message").value("잔액이 부족합니다."));

        entityManager.clear();

        // then 2. 실패한 송금은 양쪽 계좌 잔액을 절대 변경하면 안 된다.
        Account savedSenderAccount = accountRepository.findById(senderAccount.getId()).orElseThrow();
        Account savedReceiverAccount = accountRepository.findById(receiverAccount.getId()).orElseThrow();

        assertEquals(10_000L, savedSenderAccount.getBalance());
        assertEquals(10_000L, savedReceiverAccount.getBalance());

        // then 3. 성공 거래내역은 생성되면 안 된다.
        // 출금도 입금도 확정되지 않았으므로 transaction_histories는 비어 있어야 한다.
        assertEquals(0, transactionHistoryRepository.count());

        // then 4. 감사/추적 목적의 실패 송금 원장은 FAILED 상태로 1건 저장되어야 한다.
        List<Transfer> transfers = transferRepository.findAll();
        assertEquals(1, transfers.size());

        Transfer failedTransfer = transfers.getFirst();
        assertEquals(TransferStatus.FAILED, failedTransfer.getStatus());
        assertEquals(savedSenderAccount.getId(), failedTransfer.getSenderAccount().getId());
        assertEquals(savedReceiverAccount.getId(), failedTransfer.getReceiverAccount().getId());
        assertEquals(50_000L, failedTransfer.getAmount());
        assertEquals("잔액 부족", failedTransfer.getMemo());

        // then 5. 멱등성 레코드는 FAILED로 완료되어야 한다.
        // 같은 키의 실패 요청은 이후 다시 실제 송금 로직으로 진입하면 안 된다.
        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(sender.getId(), idempotencyKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.FAILED, idempotency.getStatus());
        assertNotNull(idempotency.getCompletedAt());

        // when 2. 같은 멱등성 키와 같은 payload로 재시도한다.
        // 이미 실패 처리된 키이므로 도메인 송금을 다시 수행하지 않고 멱등성 실패 응답을 반환해야 한다.
        mockMvc.perform(post("/api/v1/transfers")
                        .with(authentication(authenticatedUser(sender.getId())))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // then 6. 재시도는 기존 실패 상태 때문에 409로 거부되어야 한다.
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_REQUEST_FAILED"))
                .andExpect(jsonPath("$.message").value("같은 키 요청은 이미 실패 처리되었습니다."));

        entityManager.clear();

        // then 7. 실패 요청 재시도는 실패 송금 기록이나 거래내역을 추가로 만들면 안 된다.
        assertEquals(1, transferRepository.count());
        assertEquals(0, transactionHistoryRepository.count());

        Account retriedSenderAccount = accountRepository.findById(senderAccount.getId()).orElseThrow();
        Account retriedReceiverAccount = accountRepository.findById(receiverAccount.getId()).orElseThrow();

        assertEquals(10_000L, retriedSenderAccount.getBalance());
        assertEquals(10_000L, retriedReceiverAccount.getBalance());
    }

    @Test
    @DisplayName("비소유 계좌 송금 E2E - 로그인 사용자가 소유하지 않은 출금 계좌는 송금 처리하지 않는다")
    void transfer_senderAccountOwnedByAnotherUser_returnsAccountNotFoundWithoutPersistingTransfer() throws Exception {
        // given 1. 계좌 소유자, 수취자, 공격자 역할의 사용자를 DB에 준비한다.
        // - 실제 출금 계좌 소유자: owner
        // - API 인증 사용자: attacker
        // 공격자가 owner의 계좌 ID를 알고 있어도 송금에 사용할 수 없어야 한다.
        User owner = saveUser("owner@example.com", "계좌주");
        User receiver = saveUser("receiver-not-owned@example.com", "수취자");
        User attacker = saveUser("attacker@example.com", "공격자");
        Account ownerAccount = saveAccount(owner, "100200300031", 100_000L);
        Account receiverAccount = saveAccount(receiver, "100200300032", 10_000L);
        String idempotencyKey = "flow-not-owned-key";

        TransferReq request = new TransferReq(
                ownerAccount.getId(),
                receiverAccount.getAccountNumber(),
                "123456",
                50_000L,
                "비소유 계좌 송금 시도"
        );

        // when. 공격자 인증으로 타인의 계좌를 출금 계좌로 지정해 송금 요청한다.
        mockMvc.perform(post("/api/v1/transfers")
                        .with(authentication(authenticatedUser(attacker.getId())))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // then 1. 권한/존재 정보를 노출하지 않도록 ACCOUNT_NOT_FOUND로 거부해야 한다.
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("계좌를 찾을 수 없습니다."));

        entityManager.clear();

        // then 2. 실패한 권한 검증 요청은 양쪽 계좌 잔액을 절대 변경하면 안 된다.
        Account savedOwnerAccount = accountRepository.findById(ownerAccount.getId()).orElseThrow();
        Account savedReceiverAccount = accountRepository.findById(receiverAccount.getId()).orElseThrow();

        assertEquals(100_000L, savedOwnerAccount.getBalance());
        assertEquals(10_000L, savedReceiverAccount.getBalance());

        // then 3. 권한 검증에서 실패했으므로 송금 원장과 거래내역은 생성되면 안 된다.
        assertEquals(0, transferRepository.count());
        assertEquals(0, transactionHistoryRepository.count());

        // then 4. 멱등성 레코드는 실패로 완료되어 같은 키 재시도를 다시 처리하지 않도록 해야 한다.
        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(attacker.getId(), idempotencyKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.FAILED, idempotency.getStatus());
        assertNotNull(idempotency.getCompletedAt());
    }

    @Test
    @DisplayName("비활성 계좌 송금 E2E - CLOSED 상태의 출금 또는 수취 계좌는 송금 처리하지 않는다")
    void transfer_inactiveAccount_returnsAccountNotActiveWithoutPersistingTransfer() throws Exception {
        // given 1. 송금자와 수취자 계좌를 준비하고, 수취자 계좌를 CLOSED 상태로 변경한다.
        // 한쪽이라도 비활성 계좌라면 출금/입금/거래내역 저장이 일어나면 안 된다.
        User sender = saveUser("sender-inactive@example.com", "송금자");
        User receiver = saveUser("receiver-inactive@example.com", "수취자");
        Account senderAccount = saveAccount(sender, "100200300041", 100_000L);
        Account receiverAccount = saveAccount(receiver, "100200300042", 10_000L);
        closeAccount(receiverAccount.getId());
        String idempotencyKey = "flow-inactive-key";

        TransferReq request = new TransferReq(
                senderAccount.getId(),
                receiverAccount.getAccountNumber(),
                "123456",
                50_000L,
                "비활성 계좌 송금 시도"
        );

        // when. CLOSED 상태의 수취 계좌로 송금을 요청한다.
        mockMvc.perform(post("/api/v1/transfers")
                        .with(authentication(authenticatedUser(sender.getId())))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // then 1. API는 비활성 계좌 에러를 반환해야 한다.
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"))
                .andExpect(jsonPath("$.message").value("활성 계좌가 아닙니다."));

        entityManager.clear();

        // then 2. 비활성 계좌 검증 실패는 양쪽 계좌 잔액을 절대 변경하면 안 된다.
        Account savedSenderAccount = accountRepository.findById(senderAccount.getId()).orElseThrow();
        Account savedReceiverAccount = accountRepository.findById(receiverAccount.getId()).orElseThrow();

        assertEquals(100_000L, savedSenderAccount.getBalance());
        assertEquals(10_000L, savedReceiverAccount.getBalance());

        // then 3. 송금 원장과 거래내역은 생성되면 안 된다.
        assertEquals(0, transferRepository.count());
        assertEquals(0, transactionHistoryRepository.count());

        // then 4. 멱등성 레코드는 FAILED로 완료되어 실패 요청의 재처리 가능성을 막아야 한다.
        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(sender.getId(), idempotencyKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.FAILED, idempotency.getStatus());
        assertNotNull(idempotency.getCompletedAt());
    }

    @Test
    @DisplayName("수취 계좌번호 미존재 E2E - 존재하지 않는 수취 계좌번호는 송금 처리하지 않는다")
    void transfer_receiverAccountNumberNotFound_returnsAccountNotFoundWithoutChangingState() throws Exception {
        // given. 송금자 계좌만 존재하고, 요청의 수취 계좌번호는 DB에 존재하지 않는다.
        User sender = saveUser("sender-missing-receiver@example.com", "송금자");
        Account senderAccount = saveAccount(sender, "100200300071", 100_000L);
        String idempotencyKey = "flow-missing-receiver-key";

        TransferReq request = new TransferReq(
                senderAccount.getId(),
                "999999999999",
                "123456",
                50_000L,
                "없는 수취 계좌"
        );

        // when. 존재하지 않는 수취 계좌번호로 송금을 요청한다.
        mockMvc.perform(post("/api/v1/transfers")
                        .with(authentication(authenticatedUser(sender.getId())))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // then 1. 계좌를 찾을 수 없다는 에러를 반환해야 한다.
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("계좌를 찾을 수 없습니다."));

        entityManager.clear();

        // then 2. 수취 계좌 조회 단계에서 실패했으므로 송금자 잔액과 원장 데이터는 그대로여야 한다.
        Account savedSenderAccount = accountRepository.findById(senderAccount.getId()).orElseThrow();
        assertEquals(100_000L, savedSenderAccount.getBalance());
        assertEquals(0, transferRepository.count());
        assertEquals(0, transactionHistoryRepository.count());

        // then 3. 멱등성 레코드는 FAILED로 완료되어 같은 실패 요청이 재처리되지 않도록 한다.
        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(sender.getId(), idempotencyKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.FAILED, idempotency.getStatus());
        assertNotNull(idempotency.getCompletedAt());
    }

    @Test
    @DisplayName("출금 계좌 ID 미존재 E2E - 존재하지 않는 출금 계좌 ID는 송금 처리하지 않는다")
    void transfer_senderAccountIdNotFound_returnsAccountNotFoundWithoutPersistingTransfer() throws Exception {
        // given. 수취 계좌는 존재하지만 요청의 출금 계좌 ID는 DB에 존재하지 않는다.
        User sender = saveUser("sender-missing-account@example.com", "송금자");
        User receiver = saveUser("receiver-missing-account@example.com", "수취자");
        Account receiverAccount = saveAccount(receiver, "100200300082", 10_000L);
        Long missingSenderAccountId = 999_999L;
        String idempotencyKey = "flow-missing-sender-key";

        TransferReq request = new TransferReq(
                missingSenderAccountId,
                receiverAccount.getAccountNumber(),
                "123456",
                50_000L,
                "없는 출금 계좌"
        );

        // when. 존재하지 않는 출금 계좌 ID로 송금을 요청한다.
        mockMvc.perform(post("/api/v1/transfers")
                        .with(authentication(authenticatedUser(sender.getId())))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // then 1. 계좌를 찾을 수 없다는 에러를 반환해야 한다.
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("계좌를 찾을 수 없습니다."));

        entityManager.clear();

        // then 2. 출금 계좌 락 획득 단계에서 실패했으므로 수취 계좌와 원장 데이터는 그대로여야 한다.
        Account savedReceiverAccount = accountRepository.findById(receiverAccount.getId()).orElseThrow();
        assertEquals(10_000L, savedReceiverAccount.getBalance());
        assertEquals(0, transferRepository.count());
        assertEquals(0, transactionHistoryRepository.count());

        // then 3. 멱등성 레코드는 FAILED로 완료되어 같은 실패 요청이 재처리되지 않도록 한다.
        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(sender.getId(), idempotencyKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.FAILED, idempotency.getStatus());
        assertNotNull(idempotency.getCompletedAt());
    }

    @Test
    @DisplayName("자기 자신 계좌 송금 E2E - 같은 계좌로 송금하면 송금 처리하지 않는다")
    void transfer_sameAccount_returnsInvalidInputWithoutChangingState() throws Exception {
        // given. 송금자가 소유한 하나의 계좌를 준비한다.
        User sender = saveUser("sender-same-account@example.com", "송금자");
        Account senderAccount = saveAccount(sender, "100200300091", 100_000L);
        String idempotencyKey = "flow-same-account-key";

        TransferReq request = new TransferReq(
                senderAccount.getId(),
                senderAccount.getAccountNumber(),
                "123456",
                50_000L,
                "자기 자신 송금"
        );

        // when. 출금 계좌와 수취 계좌가 같은 송금을 요청한다.
        mockMvc.perform(post("/api/v1/transfers")
                        .with(authentication(authenticatedUser(sender.getId())))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // then 1. 자기 자신 송금은 잘못된 입력으로 거부되어야 한다.
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));

        entityManager.clear();

        // then 2. 같은 계좌 검증 단계에서 실패했으므로 잔액과 원장 데이터는 그대로여야 한다.
        Account savedSenderAccount = accountRepository.findById(senderAccount.getId()).orElseThrow();
        assertEquals(100_000L, savedSenderAccount.getBalance());
        assertEquals(0, transferRepository.count());
        assertEquals(0, transactionHistoryRepository.count());

        // then 3. 멱등성 레코드는 FAILED로 완료되어 같은 실패 요청이 재처리되지 않도록 한다.
        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(sender.getId(), idempotencyKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.FAILED, idempotency.getStatus());
        assertNotNull(idempotency.getCompletedAt());
    }

    @Test
    @DisplayName("멱등성 키 형식 오류 E2E - 유효하지 않은 Idempotency-Key는 송금 로직에 진입하지 않는다")
    void transfer_invalidIdempotencyKey_returnsInvalidKeyWithoutChangingState() throws Exception {
        // given. 정상 송금이 가능한 계좌를 준비하되, 멱등성 키는 허용되지 않는 공백 포함 문자열로 보낸다.
        User sender = saveUser("sender-invalid-key@example.com", "송금자");
        User receiver = saveUser("receiver-invalid-key@example.com", "수취자");
        Account senderAccount = saveAccount(sender, "100200300101", 100_000L);
        Account receiverAccount = saveAccount(receiver, "100200300102", 10_000L);

        TransferReq request = new TransferReq(
                senderAccount.getId(),
                receiverAccount.getAccountNumber(),
                "123456",
                50_000L,
                "잘못된 멱등성 키"
        );

        // when. 형식이 잘못된 Idempotency-Key로 송금을 요청한다.
        mockMvc.perform(post("/api/v1/transfers")
                        .with(authentication(authenticatedUser(sender.getId())))
                        .header("Idempotency-Key", "invalid key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // then 1. 멱등성 키 형식 오류를 반환해야 한다.
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_INVALID"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 키 형식입니다."));

        entityManager.clear();

        // then 2. 멱등성 예약 전에 실패했으므로 잔액, 송금, 거래내역, 멱등성 레코드가 모두 없어야 한다.
        Account savedSenderAccount = accountRepository.findById(senderAccount.getId()).orElseThrow();
        Account savedReceiverAccount = accountRepository.findById(receiverAccount.getId()).orElseThrow();

        assertEquals(100_000L, savedSenderAccount.getBalance());
        assertEquals(10_000L, savedReceiverAccount.getBalance());
        assertEquals(0, transferRepository.count());
        assertEquals(0, transactionHistoryRepository.count());
        assertEquals(0, idempotencyRepository.count());
    }

    @Test
    @Disabled("ATM/외부 입금 채널 연동 전까지 사용자 직접 입금 API 비활성화")
    @DisplayName("정상 입금 E2E - API 응답, 계좌 잔액, 입금 거래내역, 멱등성 성공 상태를 검증한다")
    void topUp_success_persistsConsistentState() throws Exception {
        // given. 실제 사용자와 입금 대상 계좌를 DB에 준비한다.
        // - 입금 전 잔액: 10,000원
        User user = saveUser("topup-success@example.com", "입금자");
        Account account = saveAccount(user, "100200300111", 10_000L);
        String idempotencyKey = "flow-topup-success-key";

        DepositReq request = new DepositReq(
                account.getId(),
                5_000L,
                "정상 입금"
        );

        // when. 인증된 계좌 소유자 관점에서 실제 입금 API를 호출한다.
        // 컨트롤러 -> 서비스 -> 멱등성 AOP -> 트랜잭션 -> DB 저장 흐름을 모두 통과한다.
        mockMvc.perform(post("/api/v1/transfers/topUp")
                        .with(authentication(authenticatedUser(user.getId())))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // then 1. HTTP 응답은 입금 거래내역 스냅샷을 반환해야 한다.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getId()))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(5_000L))
                .andExpect(jsonPath("$.balanceBefore").value(10_000L))
                .andExpect(jsonPath("$.balanceAfter").value(15_000L))
                .andExpect(jsonPath("$.memo").value("정상 입금"))
                .andExpect(jsonPath("$.transactedAt").exists());

        entityManager.clear();

        // then 2. 계좌 잔액은 입금액만큼 증가해야 한다.
        Account savedAccount = accountRepository.findById(account.getId()).orElseThrow();
        assertEquals(15_000L, savedAccount.getBalance());

        // then 3. 입금은 송금 원장을 만들지 않고, 입금 거래내역 1건만 남겨야 한다.
        assertEquals(0, transferRepository.count());

        List<TransactionHistory> histories = transactionHistoryRepository.findAll();
        assertEquals(1, histories.size());

        TransactionHistory history = histories.getFirst();
        assertEquals(savedAccount.getId(), history.getAccount().getId());
        assertEquals(TransactionType.DEPOSIT, history.getType());
        assertEquals(TransactionDirection.IN, history.getDirection());
        assertEquals(5_000L, history.getAmount());
        assertEquals(10_000L, history.getBalanceBefore());
        assertEquals(15_000L, history.getBalanceAfter());
        assertEquals("정상 입금", history.getMemo());
        assertNotNull(history.getTransactedAt());

        // then 4. 멱등성 레코드는 SUCCESS로 완료되고, 재시도 응답에 쓸 responseBody를 보관해야 한다.
        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(user.getId(), idempotencyKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.SUCCESS, idempotency.getStatus());
        assertNotNull(idempotency.getResponseBody());
        assertNotNull(idempotency.getCompletedAt());
    }

    @Test
    @Disabled("ATM/외부 입금 채널 연동 전까지 사용자 직접 입금 API 비활성화")
    @DisplayName("비소유 계좌 입금 E2E - 로그인 사용자가 소유하지 않은 계좌에는 입금하지 않는다")
    void topUp_accountOwnedByAnotherUser_returnsAccountNotFoundWithoutChangingState() throws Exception {
        // given. 실제 계좌 소유자와 공격자 역할의 사용자를 DB에 준비한다.
        // 공격자가 타인의 계좌 ID를 알고 있어도 입금에 사용할 수 없어야 한다.
        User owner = saveUser("topup-owner@example.com", "계좌주");
        User attacker = saveUser("topup-attacker@example.com", "공격자");
        Account ownerAccount = saveAccount(owner, "100200300121", 10_000L);
        String idempotencyKey = "flow-topup-not-owned-key";

        DepositReq request = new DepositReq(
                ownerAccount.getId(),
                5_000L,
                "비소유 계좌 입금 시도"
        );

        // when. 공격자 인증으로 타인의 계좌에 입금을 요청한다.
        mockMvc.perform(post("/api/v1/transfers/topUp")
                        .with(authentication(authenticatedUser(attacker.getId())))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // then 1. 권한/존재 정보를 노출하지 않도록 ACCOUNT_NOT_FOUND로 거부해야 한다.
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("계좌를 찾을 수 없습니다."));

        entityManager.clear();

        // then 2. 실패한 소유권 검증 요청은 계좌 잔액과 원장 데이터를 변경하면 안 된다.
        Account savedOwnerAccount = accountRepository.findById(ownerAccount.getId()).orElseThrow();
        assertEquals(10_000L, savedOwnerAccount.getBalance());
        assertEquals(0, transferRepository.count());
        assertEquals(0, transactionHistoryRepository.count());

        // then 3. 멱등성 레코드는 FAILED로 완료되어 같은 실패 요청이 재처리되지 않도록 한다.
        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(attacker.getId(), idempotencyKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.FAILED, idempotency.getStatus());
        assertNotNull(idempotency.getCompletedAt());
    }

    @Test
    @Disabled("ATM/외부 입금 채널 연동 전까지 사용자 직접 입금 API 비활성화")
    @DisplayName("비활성 계좌 입금 E2E - CLOSED 상태의 계좌에는 입금하지 않는다")
    void topUp_inactiveAccount_returnsAccountNotActiveWithoutChangingState() throws Exception {
        // given. 입금 대상 계좌를 준비하고 CLOSED 상태로 변경한다.
        User user = saveUser("topup-inactive@example.com", "입금자");
        Account account = saveAccount(user, "100200300131", 10_000L);
        closeAccount(account.getId());
        String idempotencyKey = "flow-topup-inactive-key";

        DepositReq request = new DepositReq(
                account.getId(),
                5_000L,
                "비활성 계좌 입금 시도"
        );

        // when. CLOSED 상태의 계좌에 입금을 요청한다.
        mockMvc.perform(post("/api/v1/transfers/topUp")
                        .with(authentication(authenticatedUser(user.getId())))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // then 1. API는 비활성 계좌 에러를 반환해야 한다.
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"))
                .andExpect(jsonPath("$.message").value("활성 계좌가 아닙니다."));

        entityManager.clear();

        // then 2. 비활성 계좌 검증 실패는 계좌 잔액과 원장 데이터를 변경하면 안 된다.
        Account savedAccount = accountRepository.findById(account.getId()).orElseThrow();
        assertEquals(10_000L, savedAccount.getBalance());
        assertEquals(0, transferRepository.count());
        assertEquals(0, transactionHistoryRepository.count());

        // then 3. 멱등성 레코드는 FAILED로 완료되어 실패 요청의 재처리 가능성을 막아야 한다.
        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(user.getId(), idempotencyKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.FAILED, idempotency.getStatus());
        assertNotNull(idempotency.getCompletedAt());
    }

    @Test
    @Disabled("ATM/외부 입금 채널 연동 전까지 사용자 직접 입금 API 비활성화")
    @DisplayName("계좌 ID 미존재 입금 E2E - 존재하지 않는 계좌 ID에는 입금하지 않는다")
    void topUp_accountIdNotFound_returnsAccountNotFoundWithoutPersistingHistory() throws Exception {
        // given. 인증 사용자는 존재하지만 요청의 입금 대상 계좌 ID는 DB에 존재하지 않는다.
        User user = saveUser("topup-missing-account@example.com", "입금자");
        Long missingAccountId = 999_999L;
        String idempotencyKey = "flow-topup-missing-account-key";

        DepositReq request = new DepositReq(
                missingAccountId,
                5_000L,
                "없는 계좌 입금"
        );

        // when. 존재하지 않는 계좌 ID로 입금을 요청한다.
        mockMvc.perform(post("/api/v1/transfers/topUp")
                        .with(authentication(authenticatedUser(user.getId())))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // then 1. 계좌를 찾을 수 없다는 에러를 반환해야 한다.
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("계좌를 찾을 수 없습니다."));

        entityManager.clear();

        // then 2. 계좌 조회 단계에서 실패했으므로 송금 원장과 거래내역은 생성되면 안 된다.
        assertEquals(0, transferRepository.count());
        assertEquals(0, transactionHistoryRepository.count());

        // then 3. 멱등성 레코드는 FAILED로 완료되어 같은 실패 요청이 재처리되지 않도록 한다.
        Idempotency idempotency = idempotencyRepository
                .findByUser_IdAndIdempotencyKey(user.getId(), idempotencyKey)
                .orElseThrow();

        assertEquals(IdempotencyStatus.FAILED, idempotency.getStatus());
        assertNotNull(idempotency.getCompletedAt());
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

    private void closeAccount(Long accountId) {
        transactionTemplate.executeWithoutResult(status -> {
            Account account = accountRepository.findById(accountId).orElseThrow();
            account.close();
        });
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
}
