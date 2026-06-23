package com.team10.backend.domain.exAccount.repository;

import com.team10.backend.domain.exAccount.entity.ExAccountConnection;
import com.team10.backend.domain.exAccount.entity.value.EncryptedConnectedId;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class ExAccountConnectionRepositoryTest {

    @Autowired
    private ExAccountConnectionRepository connectionRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        owner = persistUser("connection-owner@example.com");
        other = persistUser("connection-other@example.com");
    }

    @Test
    void findsConnectionOnlyForItsOwner() {
        ExAccountConnection connection = persistConnection(owner, "0004", "ciphertext-owner");
        entityManager.flush();
        entityManager.clear();

        assertThat(connectionRepository.findByUserIdAndOrganization(owner.getId(), "0004"))
                .get()
                .extracting(ExAccountConnection::getId)
                .isEqualTo(connection.getId());
        assertThat(connectionRepository.findByUserIdAndOrganization(other.getId(), "0004"))
                .isEmpty();
        assertThat(connectionRepository.findByIdAndUserId(connection.getId(), other.getId()))
                .isEmpty();
    }

    @Test
    void rejectsDuplicateOrganizationForSameUser() {
        persistConnection(owner, "0004", "ciphertext-first");
        entityManager.flush();

        ExAccountConnection duplicate = ExAccountConnection.create(
                owner,
                "0004",
                encrypted("ciphertext-second")
        );

        assertThatThrownBy(() -> connectionRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void storesOnlyEncryptedConnectedIdMaterial() {
        String plaintextConnectedId = "plain-connected-id-must-not-be-stored";
        ExAccountConnection connection = persistConnection(owner, "0004", "encrypted-value");
        entityManager.flush();
        entityManager.clear();

        Object[] row = (Object[]) entityManager.createNativeQuery("""
                        select connected_id_ciphertext, connected_id_iv, encryption_key_version
                        from codef_external_account_connection
                        where id = :id
                        """)
                .setParameter("id", connection.getId())
                .getSingleResult();

        assertThat(row).containsExactly("encrypted-value", "base64-iv", "v1");
        assertThat(row).allSatisfy(value -> assertThat(value.toString())
                .doesNotContain(plaintextConnectedId));
    }

    private User persistUser(String email) {
        User user = User.create(
                email,
                "password",
                "사용자",
                "01012345678",
                LocalDate.of(1995, 1, 1)
        );
        entityManager.persist(user);
        return user;
    }

    private ExAccountConnection persistConnection(
            User user,
            String organization,
            String ciphertext
    ) {
        ExAccountConnection connection = ExAccountConnection.create(
                user,
                organization,
                encrypted(ciphertext)
        );
        entityManager.persist(connection);
        return connection;
    }

    private EncryptedConnectedId encrypted(String ciphertext) {
        return new EncryptedConnectedId(ciphertext, "base64-iv", "v1");
    }
}
