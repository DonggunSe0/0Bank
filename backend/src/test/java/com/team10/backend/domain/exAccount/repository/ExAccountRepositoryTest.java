package com.team10.backend.domain.exAccount.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.team10.backend.domain.exAccount.type.ExAccountType;
import com.team10.backend.domain.exAccount.entity.ExAccount;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class ExAccountRepositoryTest {

    private static final String OWNER_ACCOUNT_HASH = "a".repeat(64);
    private static final String OTHER_ACCOUNT_HASH = "b".repeat(64);

    @Autowired
    private ExAccountRepository exAccountRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User other;
    private ExAccount ownerAccount;
    private ExAccount otherAccount;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner@example.com", "계좌주");
        other = persistUser("other@example.com", "타계좌주");
        ownerAccount = persistExAccount(
                owner,
                "국민은행",
                OWNER_ACCOUNT_HASH,
                "123456****1234",
                "KB Star 입출금통장"
        );
        otherAccount = persistExAccount(
                other,
                "국민은행",
                OTHER_ACCOUNT_HASH,
                "999999****9999",
                "타인 외부계좌"
        );

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("사용자 ID로 연동된 외부 계좌 목록을 조회한다")
    void findAllByUserId() {
        List<ExAccount> accounts = exAccountRepository.findAllByUserId(owner.getId());

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getId()).isEqualTo(ownerAccount.getId());
        assertThat(accounts.get(0).getOrganization()).isEqualTo("국민은행");
        assertThat(accounts.get(0).getAccountNumberHash()).isEqualTo(OWNER_ACCOUNT_HASH);
        assertThat(accounts.get(0).getAccountNumberMasked()).isEqualTo("123456****1234");
    }

    @Test
    @DisplayName("외부 계좌 ID와 사용자 ID로 내 외부 계좌를 조회한다")
    void findByIdAndUserId() {
        Optional<ExAccount> found = exAccountRepository.findByIdAndUserId(ownerAccount.getId(), owner.getId());
        Optional<ExAccount> notOwned = exAccountRepository.findByIdAndUserId(otherAccount.getId(), owner.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getAccountName()).isEqualTo("KB Star 입출금통장");
        assertThat(notOwned).isEmpty();
    }

    @Test
    @DisplayName("같은 사용자, 기관, 계좌번호 해시로 기존 외부 계좌를 조회한다")
    void findByUserIdAndOrganizationAndAccountNumberHash() {
        Optional<ExAccount> found = exAccountRepository.findByUserIdAndOrganizationAndAccountNumberHash(
                owner.getId(),
                "국민은행",
                OWNER_ACCOUNT_HASH
        );
        Optional<ExAccount> missing = exAccountRepository.findByUserIdAndOrganizationAndAccountNumberHash(
                owner.getId(),
                "신한은행",
                OWNER_ACCOUNT_HASH
        );

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(ownerAccount.getId());
        assertThat(missing).isEmpty();
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

    private ExAccount persistExAccount(
            User user,
            String organization,
            String accountNumberHash,
            String accountNumberMasked,
            String accountName
    ) {
        ExAccount account = ExAccount.create(
                user,
                organization,
                accountNumberHash,
                accountNumberMasked,
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
}
