package com.team10.backend.domain.user.repository;

import com.team10.backend.domain.user.entity.IdentityVerification;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.global.config.QuerydslConfig;
import com.team10.backend.global.crypto.HmacConfig;
import com.team10.backend.global.crypto.HmacHasher;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IdentityVerification.ocrResidentNumber가 가역 암호화 없이
 * "마스킹된 표시값 + 단방향 해시(HmacHasher)"로만 저장되는지 확인하는 통합 테스트.
 *
 * <p>주민번호 평문은 어떤 컬럼에도 남아서는 안 되고, 해시는 항상 결정론적(deterministic)이어야 한다
 * — 즉, 같은 평문은 항상 같은 해시를 가져야 하며(동등 비교/중복 검사에 활용 가능), 그 해시로부터
 * 원문을 복원할 수는 없다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({QuerydslConfig.class, HmacConfig.class, HmacHasher.class})
class IdentityVerificationEncryptionTest {

    @Autowired
    private IdentityVerificationRepository identityVerificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private HmacHasher hmacHasher;

    private User persistUser(String email) {
        return userRepository.save(
                User.create(email, "encoded-pw", "홍길동", "01012345678", LocalDate.of(1990, 1, 1)));
    }

    private String rawResidentNumber(Long verificationId) {
        return (String) entityManager
                .createNativeQuery("SELECT ocr_resident_number FROM identity_verifications WHERE id = :id")
                .setParameter("id", verificationId)
                .getSingleResult();
    }

    private String rawResidentNumberHash(Long verificationId) {
        return (String) entityManager
                .createNativeQuery("SELECT ocr_resident_number_hash FROM identity_verifications WHERE id = :id")
                .setParameter("id", verificationId)
                .getSingleResult();
    }

    @Test
    @DisplayName("DB에는 마스킹된 값만 저장되고, 주민번호 평문은 어디에도 남지 않는다")
    void ocrResidentNumber_isMaskedAtRest_neverStoresPlainText() {
        User user = persistUser("hash-test@example.com");

        IdentityVerification verification = IdentityVerification.startOcr(user);
        verification.completeOcr("홍길동", "901201-1234567", "2023-01-15", hmacHasher.hash("901201-1234567"));
        Long id = identityVerificationRepository.save(verification).getId();

        entityManager.flush();
        entityManager.clear();

        String rawValue = rawResidentNumber(id);
        assertThat(rawValue).isEqualTo("901201-*******");
        assertThat(rawValue).doesNotContain("1234567");

        IdentityVerification reloaded = identityVerificationRepository.findById(id).orElseThrow();
        assertThat(reloaded.getOcrResidentNumber()).isEqualTo("901201-*******");
    }

    @Test
    @DisplayName("해시는 복호화 불가능한 형태로 저장되고, 동일 평문은 항상 동일한 해시를 가진다 (동등 비교 가능)")
    void residentNumberHash_isDeterministic_andNotReversible() {
        User user1 = persistUser("hash-test-1@example.com");
        User user2 = persistUser("hash-test-2@example.com");
        String expectedHash = hmacHasher.hash("901201-1234567");

        IdentityVerification v1 = IdentityVerification.startOcr(user1);
        v1.completeOcr("홍길동", "901201-1234567", "2023-01-15", expectedHash);
        Long id1 = identityVerificationRepository.save(v1).getId();

        IdentityVerification v2 = IdentityVerification.startOcr(user2);
        v2.completeOcr("홍길동", "901201-1234567", "2023-01-15", expectedHash);
        Long id2 = identityVerificationRepository.save(v2).getId();

        entityManager.flush();
        entityManager.clear();

        String hash1 = rawResidentNumberHash(id1);
        String hash2 = rawResidentNumberHash(id2);

        assertThat(hash1).isEqualTo(expectedHash);
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).doesNotContain("1234567").doesNotContain("901201");
    }

    @Test
    @DisplayName("행안부 인증 처리 후에도 마스킹된 값과 해시가 그대로 유지된다")
    void maskedValueAndHash_surviveStatusTransitions() {
        User user = persistUser("hash-test-masked@example.com");
        String hash = hmacHasher.hash("901201-1234567");

        IdentityVerification verification = IdentityVerification.startOcr(user);
        verification.completeOcr("홍길동", "901201-1234567", "2023-01-15", hash);
        verification.completeGovernmentVerification();
        Long id = identityVerificationRepository.save(verification).getId();

        entityManager.flush();
        entityManager.clear();

        IdentityVerification reloaded = identityVerificationRepository.findById(id).orElseThrow();
        assertThat(reloaded.getOcrResidentNumber()).isEqualTo("901201-*******");
        assertThat(reloaded.getOcrResidentNumberHash()).isEqualTo(hash);
    }
}
