package com.team10.backend.domain.user.ocr;

import com.team10.backend.domain.codef.auth.ocr.IdCardOcrResult;
import com.team10.backend.domain.user.entity.IdentityVerification;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.IdentityVerificationRepository;
import com.team10.backend.domain.user.type.VerificationStatus;
import com.team10.backend.global.crypto.HmacHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OcrPersistenceServiceTest {

    @Mock
    IdentityVerificationRepository identityVerificationRepository;

    @Mock
    HmacHasher hmacHasher;

    @InjectMocks
    OcrPersistenceService ocrPersistenceService;

    private IdentityVerification newVerification() {
        return IdentityVerification.startOcr(mock(User.class));
    }

    @Nested
    @DisplayName("loadVerification")
    class LoadVerification {

        @Test
        @DisplayName("존재하는 세션 → 해당 엔티티 반환")
        void found_returnsEntity() {
            IdentityVerification verification = newVerification();
            when(identityVerificationRepository.findById(1L)).thenReturn(Optional.of(verification));

            IdentityVerification result = ocrPersistenceService.loadVerification(1L);

            assertThat(result).isSameAs(verification);
        }

        @Test
        @DisplayName("존재하지 않는 세션 → null 반환")
        void notFound_returnsNull() {
            when(identityVerificationRepository.findById(1L)).thenReturn(Optional.empty());

            IdentityVerification result = ocrPersistenceService.loadVerification(1L);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("saveOcrSuccess")
    class SaveOcrSuccess {

        @Test
        @DisplayName("존재하는 세션 → 마스킹된 주민번호와 해시가 기록되고 상태 전환된다")
        void found_completesOcr() {
            IdentityVerification verification = newVerification();
            when(identityVerificationRepository.findById(1L)).thenReturn(Optional.of(verification));
            when(hmacHasher.hash("901201-1234567")).thenReturn("hashed-value");

            ocrPersistenceService.saveOcrSuccess(1L, new IdCardOcrResult("홍길동", "901201-1234567", "2023-01-15"));

            assertThat(verification.getOcrName()).isEqualTo("홍길동");
            assertThat(verification.getOcrResidentNumber()).isEqualTo("901201-*******");
            assertThat(verification.getOcrResidentNumberHash()).isEqualTo("hashed-value");
            assertThat(verification.getOcrIssueDate()).isEqualTo("2023-01-15");
            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.OCR_COMPLETED);
        }

        @Test
        @DisplayName("존재하지 않는 세션 → 예외 없이 아무 동작도 하지 않는다")
        void notFound_doesNothing() {
            when(identityVerificationRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatCode(() ->
                    ocrPersistenceService.saveOcrSuccess(1L, new IdCardOcrResult("홍길동", "901201-1234567", "2023-01-15"))
            ).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("saveGovSuccess")
    class SaveGovSuccess {

        @Test
        @DisplayName("존재하는 세션 → 행안부 인증 완료 상태로 전환")
        void found_completesGovernmentVerification() {
            IdentityVerification verification = newVerification();
            when(identityVerificationRepository.findById(1L)).thenReturn(Optional.of(verification));

            ocrPersistenceService.saveGovSuccess(1L);

            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.GOVERNMENT_VERIFIED);
        }

        @Test
        @DisplayName("존재하지 않는 세션 → 예외 없이 아무 동작도 하지 않는다")
        void notFound_doesNothing() {
            when(identityVerificationRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatCode(() -> ocrPersistenceService.saveGovSuccess(1L))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("saveFailure")
    class SaveFailure {

        @Test
        @DisplayName("존재하는 세션 → 실패 사유 기록 및 FAILED 상태로 전환")
        void found_marksFailed() {
            IdentityVerification verification = newVerification();
            when(identityVerificationRepository.findById(1L)).thenReturn(Optional.of(verification));

            ocrPersistenceService.saveFailure(1L, "OCR 처리 실패");

            assertThat(verification.getFailureReason()).isEqualTo("OCR 처리 실패");
            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.FAILED);
        }

        @Test
        @DisplayName("존재하지 않는 세션 → 예외 없이 아무 동작도 하지 않는다")
        void notFound_doesNothing() {
            when(identityVerificationRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatCode(() -> ocrPersistenceService.saveFailure(1L, "사유"))
                    .doesNotThrowAnyException();
        }
    }
}
