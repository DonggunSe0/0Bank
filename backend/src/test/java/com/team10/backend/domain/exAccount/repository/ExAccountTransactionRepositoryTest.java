package com.team10.backend.domain.exAccount.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team10.backend.domain.exAccount.type.ExAccountTransactionDirection;
import com.team10.backend.domain.exAccount.type.ExAccountType;
import com.team10.backend.domain.exAccount.entity.ExAccount;
import com.team10.backend.domain.exAccount.entity.ExAccountTransaction;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExAccountTransactionRepositoryTest {

    @Autowired
    private ExAccountTransactionRepository transactionRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User other;
    private ExAccount ownerAccount;
    private ExAccount ownerSecondAccount;
    private ExAccount otherAccount;
    private ExAccountTransaction newestTransaction;
    private ExAccountTransaction oldestTransaction;
    private ExAccountTransaction otherAccountTransaction;
    private ExAccountTransaction otherUserTransaction;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner@example.com", "계좌주");
        other = persistUser("other@example.com", "타계좌주");
        ownerAccount = persistExAccount(owner, "국민은행", "12345678901234", "KB Star 입출금통장");
        ownerSecondAccount = persistExAccount(owner, "신한은행", "22222222222222", "신한 입출금통장");
        otherAccount = persistExAccount(other, "국민은행", "99999999999999", "타인 외부계좌");

        oldestTransaction = persistTransaction(
                ownerAccount,
                "KB-20260617090000-0001",
                LocalDateTime.of(2026, 6, 17, 9, 0),
                "편의점"
        );
        newestTransaction = persistTransaction(
                ownerAccount,
                "KB-20260618143000-0001",
                LocalDateTime.of(2026, 6, 18, 14, 30),
                "스타벅스"
        );
        otherAccountTransaction = persistTransaction(
                ownerSecondAccount,
                "SH-20260619100000-0001",
                LocalDateTime.of(2026, 6, 19, 10, 0),
                "다른내계좌"
        );
        otherUserTransaction = persistTransaction(
                otherAccount,
                "KB-20260620100000-0001",
                LocalDateTime.of(2026, 6, 20, 10, 0),
                "타인거래"
        );

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("사용자 ID로 모든 외부 계좌 거래내역을 최신순으로 조회한다")
    void findAllByExAccountUserIdOrderByTransactedAtDesc() {
        List<ExAccountTransaction> transactions = transactionRepository
                .findAllByExAccountUserIdOrderByTransactedAtDesc(owner.getId());

        assertThat(transactions)
                .hasSize(3)
                .extracting(ExAccountTransaction::getId)
                .containsExactly(
                        otherAccountTransaction.getId(),
                        newestTransaction.getId(),
                        oldestTransaction.getId()
                );
    }

    @Test
    @DisplayName("외부 계좌 ID와 사용자 ID로 해당 계좌 거래내역만 최신순으로 조회한다")
    void findAllByExAccountIdAndExAccountUserIdOrderByTransactedAtDesc() {
        List<ExAccountTransaction> transactions = transactionRepository
                .findAllByExAccountIdAndExAccountUserIdOrderByTransactedAtDesc(ownerAccount.getId(), owner.getId());
        List<ExAccountTransaction> notOwned = transactionRepository
                .findAllByExAccountIdAndExAccountUserIdOrderByTransactedAtDesc(otherAccount.getId(), owner.getId());

        assertThat(transactions)
                .hasSize(2)
                .extracting(ExAccountTransaction::getId)
                .containsExactly(newestTransaction.getId(), oldestTransaction.getId());
        assertThat(notOwned).isEmpty();
    }

    @Test
    @DisplayName("외부 계좌 ID와 거래 고유키로 거래내역을 조회한다")
    void findByExAccountIdAndTransactionKey() {
        Optional<ExAccountTransaction> found = transactionRepository.findByExAccountIdAndTransactionKey(
                ownerAccount.getId(),
                "KB-20260618143000-0001"
        );
        Optional<ExAccountTransaction> missing = transactionRepository.findByExAccountIdAndTransactionKey(
                ownerSecondAccount.getId(),
                "KB-20260618143000-0001"
        );

        assertThat(found).isPresent();
        assertThat(found.get().getCounterpartyName()).isEqualTo("스타벅스");
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("같은 외부 계좌에 같은 거래 고유키는 중복 저장할 수 없다")
    void duplicateTransactionKeyOnSameExAccountFails() {
        ExAccount managedAccount = entityManager.find(ExAccount.class, ownerAccount.getId());
        ExAccountTransaction duplicate = ExAccountTransaction.create(
                managedAccount,
                "KB-20260618143000-0001",
                LocalDateTime.of(2026, 6, 18, 14, 30),
                ExAccountTransactionDirection.OUT,
                BigDecimal.valueOf(45_000),
                BigDecimal.valueOf(1_455_000),
                "중복거래",
                "카드 결제",
                "식비"
        );

        assertThatThrownBy(() -> transactionRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User persistUser(String email, String name) {
        User user = User.create(
                email,
                "password",
                name,
                "01012345678",
                LocalDate.of(1995, 1, 1)
        );
        entityManager.persist(user);
        return user;
    }

    private ExAccount persistExAccount(User user, String organization, String accountNumber, String accountName) {
        ExAccount account = ExAccount.create(
                user,
                organization,
                "a".repeat(64),
                accountNumber.substring(0, 6) + "****" + accountNumber.substring(accountNumber.length() - 4),
                accountName,
                "생활비 통장",
                ExAccountType.DEMAND,
                BigDecimal.valueOf(1_500_000),
                BigDecimal.valueOf(1_200_000),
                LocalDate.of(2024, 1, 15),
                null,
                LocalDate.of(2026, 6, 18)
        );
        entityManager.persist(account);
        return account;
    }

    private ExAccountTransaction persistTransaction(
            ExAccount account,
            String transactionKey,
            LocalDateTime transactedAt,
            String counterpartyName
    ) {
        ExAccountTransaction transaction = ExAccountTransaction.create(
                account,
                transactionKey,
                transactedAt,
                ExAccountTransactionDirection.OUT,
                BigDecimal.valueOf(45_000),
                BigDecimal.valueOf(1_455_000),
                counterpartyName,
                "카드 결제",
                "식비"
        );
        entityManager.persist(transaction);
        return transaction;
    }
}
