package com.team10.backend.domain.transaction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.team10.backend.domain.account.entity.Account;
import com.team10.backend.domain.account.type.AccountType;
import com.team10.backend.domain.transaction.dto.req.TransactionHistorySearchReq;
import com.team10.backend.domain.transaction.dto.res.TransactionHistorySearchRes;
import com.team10.backend.domain.transaction.entity.TransactionHistory;
import com.team10.backend.domain.transaction.type.TransactionDirection;
import com.team10.backend.domain.transaction.type.TransactionType;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class TransactionHistoryRepositoryTest {

    @Autowired
    private TransactionHistoryRepository transactionHistoryRepository;

    @Autowired
    private EntityManager entityManager;

    private Account ownerAccount;
    private Account otherAccount;

    private Long juneOneDepositId;
    private Long juneTwoTransferOutId;
    private Long juneThreeTransferInId;
    private Long juneFourPaymentOutId;

    @BeforeEach
    void setUp() {
        User owner = persistUser("owner@example.com", "계좌주");
        User other = persistUser("other@example.com", "타계좌주");
        ownerAccount = persistAccount(owner, "100200300001", "주계좌");
        otherAccount = persistAccount(other, "100200300002", "타계좌");

        juneOneDepositId = persistHistory(
                ownerAccount,
                TransactionType.DEPOSIT,
                TransactionDirection.IN,
                1_000L,
                101_000L,
                "김입금",
                LocalDateTime.of(2026, 6, 1, 9, 0)
        ).getId();
        juneTwoTransferOutId = persistHistory(
                ownerAccount,
                TransactionType.TRANSFER,
                TransactionDirection.OUT,
                2_000L,
                99_000L,
                "박송금",
                LocalDateTime.of(2026, 6, 2, 10, 0)
        ).getId();
        juneThreeTransferInId = persistHistory(
                ownerAccount,
                TransactionType.TRANSFER,
                TransactionDirection.IN,
                3_000L,
                102_000L,
                "최수취",
                LocalDateTime.of(2026, 6, 3, 11, 0)
        ).getId();
        juneFourPaymentOutId = persistHistory(
                ownerAccount,
                TransactionType.PAYMENT,
                TransactionDirection.OUT,
                4_000L,
                98_000L,
                "카페결제",
                LocalDateTime.of(2026, 6, 4, 12, 0)
        ).getId();
        persistHistory(
                otherAccount,
                TransactionType.DEPOSIT,
                TransactionDirection.IN,
                9_999L,
                9_999L,
                "타인거래",
                LocalDateTime.of(2026, 6, 5, 13, 0)
        );

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("요청 계좌의 거래내역만 조회한다")
    void searchReturnsOnlyRequestedAccountHistories() {
        Page<TransactionHistorySearchRes> result = transactionHistoryRepository.search(
                ownerAccount.getId(),
                emptyFilter(),
                pageRequest(0, 20, Sort.Direction.DESC)
        );

        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getContent())
                .extracting(TransactionHistorySearchRes::transactionHistoryId)
                .containsExactly(juneFourPaymentOutId, juneThreeTransferInId, juneTwoTransferOutId, juneOneDepositId);
    }

    @Test
    @DisplayName("다건 조회 응답 필드를 정확히 매핑한다")
    void searchMapsProjectionFields() {
        Page<TransactionHistorySearchRes> result = transactionHistoryRepository.search(
                ownerAccount.getId(),
                emptyFilter(),
                pageRequest(0, 20, Sort.Direction.ASC)
        );

        TransactionHistorySearchRes first = result.getContent().getFirst();
        assertThat(first.transactionHistoryId()).isEqualTo(juneOneDepositId);
        assertThat(first.counterpartyName()).isEqualTo("김입금");
        assertThat(first.amount()).isEqualTo(1_000L);
        assertThat(first.balanceAfter()).isEqualTo(101_000L);
        assertThat(first.transactedAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 9, 0));
        assertThat(first.direction()).isEqualTo(TransactionDirection.IN);
    }

    @Test
    @DisplayName("기간 범위를 날짜 기준으로 필터링한다")
    void searchFiltersByDateRangeInclusivelyByDate() {
        TransactionHistorySearchReq filter = new TransactionHistorySearchReq(
                LocalDate.of(2026, 6, 2),
                LocalDate.of(2026, 6, 3),
                null,
                null,
                null,
                null
        );

        Page<TransactionHistorySearchRes> result = transactionHistoryRepository.search(
                ownerAccount.getId(),
                filter,
                pageRequest(0, 20, Sort.Direction.ASC)
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(TransactionHistorySearchRes::transactionHistoryId)
                .containsExactly(juneTwoTransferOutId, juneThreeTransferInId);
    }

    @Test
    @DisplayName("입출금 방향으로 필터링한다")
    void searchFiltersByDirection() {
        TransactionHistorySearchReq filter = new TransactionHistorySearchReq(
                null,
                null,
                TransactionDirection.OUT,
                null,
                null,
                null
        );

        Page<TransactionHistorySearchRes> result = transactionHistoryRepository.search(
                ownerAccount.getId(),
                filter,
                pageRequest(0, 20, Sort.Direction.ASC)
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(TransactionHistorySearchRes::transactionHistoryId)
                .containsExactly(juneTwoTransferOutId, juneFourPaymentOutId);
    }

    @Test
    @DisplayName("거래액 범위로 필터링한다")
    void searchFiltersByAmountRange() {
        TransactionHistorySearchReq filter = new TransactionHistorySearchReq(
                null,
                null,
                null,
                2_000L,
                3_000L,
                null
        );

        Page<TransactionHistorySearchRes> result = transactionHistoryRepository.search(
                ownerAccount.getId(),
                filter,
                pageRequest(0, 20, Sort.Direction.ASC)
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(TransactionHistorySearchRes::transactionHistoryId)
                .containsExactly(juneTwoTransferOutId, juneThreeTransferInId);
    }

    @Test
    @DisplayName("거래 상대명을 부분 일치로 필터링한다")
    void searchFiltersByCounterpartyNameContainingIgnoreCase() {
        TransactionHistorySearchReq filter = new TransactionHistorySearchReq(
                null,
                null,
                null,
                null,
                null,
                "송"
        );

        Page<TransactionHistorySearchRes> result = transactionHistoryRepository.search(
                ownerAccount.getId(),
                filter,
                pageRequest(0, 20, Sort.Direction.ASC)
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().transactionHistoryId()).isEqualTo(juneTwoTransferOutId);
    }

    @Test
    @DisplayName("기간, 방향, 금액, 상대명 복합 조건으로 필터링한다")
    void searchAppliesComplexFilterCombination() {
        TransactionHistorySearchReq filter = new TransactionHistorySearchReq(
                LocalDate.of(2026, 6, 2),
                LocalDate.of(2026, 6, 4),
                TransactionDirection.OUT,
                3_000L,
                5_000L,
                "결제"
        );

        Page<TransactionHistorySearchRes> result = transactionHistoryRepository.search(
                ownerAccount.getId(),
                filter,
                pageRequest(0, 20, Sort.Direction.DESC)
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().transactionHistoryId()).isEqualTo(juneFourPaymentOutId);
    }

    @Test
    @DisplayName("페이지 번호와 크기에 맞게 조회한다")
    void searchAppliesPaging() {
        Page<TransactionHistorySearchRes> firstPage = transactionHistoryRepository.search(
                ownerAccount.getId(),
                emptyFilter(),
                pageRequest(0, 2, Sort.Direction.DESC)
        );
        Page<TransactionHistorySearchRes> secondPage = transactionHistoryRepository.search(
                ownerAccount.getId(),
                emptyFilter(),
                pageRequest(1, 2, Sort.Direction.DESC)
        );

        assertThat(firstPage.getTotalElements()).isEqualTo(4);
        assertThat(firstPage.getContent())
                .extracting(TransactionHistorySearchRes::transactionHistoryId)
                .containsExactly(juneFourPaymentOutId, juneThreeTransferInId);
        assertThat(secondPage.getTotalElements()).isEqualTo(4);
        assertThat(secondPage.getContent())
                .extracting(TransactionHistorySearchRes::transactionHistoryId)
                .containsExactly(juneTwoTransferOutId, juneOneDepositId);
    }

    @Test
    @DisplayName("거래일시 기준 오름차순과 내림차순으로 정렬한다")
    void searchSortsByTransactedAtAscendingAndDescending() {
        Page<TransactionHistorySearchRes> ascResult = transactionHistoryRepository.search(
                ownerAccount.getId(),
                emptyFilter(),
                pageRequest(0, 20, Sort.Direction.ASC)
        );
        Page<TransactionHistorySearchRes> descResult = transactionHistoryRepository.search(
                ownerAccount.getId(),
                emptyFilter(),
                pageRequest(0, 20, Sort.Direction.DESC)
        );

        assertThat(ascResult.getContent())
                .extracting(TransactionHistorySearchRes::transactionHistoryId)
                .containsExactly(juneOneDepositId, juneTwoTransferOutId, juneThreeTransferInId, juneFourPaymentOutId);
        assertThat(descResult.getContent())
                .extracting(TransactionHistorySearchRes::transactionHistoryId)
                .containsExactly(juneFourPaymentOutId, juneThreeTransferInId, juneTwoTransferOutId, juneOneDepositId);
    }

    @Test
    @DisplayName("거래내역 ID와 계좌 ID가 모두 일치할 때 단건 조회한다")
    void findByIdAndAccountIdReturnsOnlyMatchingAccountHistory() {
        assertThat(transactionHistoryRepository.findByIdAndAccountId(juneOneDepositId, ownerAccount.getId()))
                .isPresent();
        assertThat(transactionHistoryRepository.findByIdAndAccountId(juneOneDepositId, otherAccount.getId()))
                .isEmpty();
    }

    private TransactionHistorySearchReq emptyFilter() {
        return new TransactionHistorySearchReq(null, null, null, null, null, null);
    }

    private PageRequest pageRequest(int page, int size, Sort.Direction direction) {
        return PageRequest.of(page, size, Sort.by(direction, "transactedAt"));
    }

    private User persistUser(String email, String name) {
        User user = newInstance(User.class);
        setField(user, "email", email);
        setField(user, "password", "password");
        setField(user, "name", name);
        setField(user, "phoneNumber", "01012345678");
        setField(user, "birthDate", LocalDate.of(1990, 1, 1));
        setField(user, "identityVerified", true);
        entityManager.persist(user);
        return user;
    }

    private Account persistAccount(User user, String accountNumber, String nickname) {
        Account account = Account.create(user, accountNumber, nickname, AccountType.DEPOSIT);
        entityManager.persist(account);
        return account;
    }

    private TransactionHistory persistHistory(
            Account account,
            TransactionType type,
            TransactionDirection direction,
            Long amount,
            Long balanceAfter,
            String counterpartyName,
            LocalDateTime transactedAt
    ) {
        TransactionHistory history = newInstance(TransactionHistory.class);
        setField(history, "account", account);
        setField(history, "type", type);
        setField(history, "direction", direction);
        setField(history, "amount", amount);
        setField(history, "balanceBefore", balanceAfter + amount);
        setField(history, "balanceAfter", balanceAfter);
        setField(history, "counterpartyAccountNumber", "999999999999");
        setField(history, "counterpartyName", counterpartyName);
        setField(history, "memo", counterpartyName + " 메모");
        setField(history, "transactedAt", transactedAt);
        entityManager.persist(history);
        return history;
    }

    private void setField(Object target, String fieldName, Object value) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("필드를 찾을 수 없습니다: " + fieldName);
    }

    private <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
