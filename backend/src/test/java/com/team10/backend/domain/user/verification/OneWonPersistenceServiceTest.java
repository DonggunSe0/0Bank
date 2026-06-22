package com.team10.backend.domain.user.verification;

import com.team10.backend.domain.user.entity.IdentityVerification;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.repository.IdentityVerificationRepository;
import com.team10.backend.domain.user.type.VerificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OneWonPersistenceServiceTest {

    @Mock
    IdentityVerificationRepository identityVerificationRepository;

    @InjectMocks
    OneWonPersistenceService oneWonPersistenceService;

    private IdentityVerification newVerificationInProgress() {
        IdentityVerification v = IdentityVerification.startOcr(mock(User.class));
        v.requestOneWonTransfer();
        return v;
    }

    @Nested
    @DisplayName("markSent")
    class MarkSent {

        @Test
        @DisplayName("존재하는 세션 → ONE_WON_PENDING 상태로 전환")
        void found_transitionsToOneWonPending() {
            IdentityVerification verification = newVerificationInProgress();
            when(identityVerificationRepository.findById(10L)).thenReturn(Optional.of(verification));

            oneWonPersistenceService.markSent(10L);

            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.ONE_WON_PENDING);
        }

        @Test
        @DisplayName("존재하지 않는 세션 → 예외 없이 아무 동작도 하지 않는다")
        void notFound_doesNothing() {
            when(identityVerificationRepository.findById(10L)).thenReturn(Optional.empty());

            assertThatCode(() -> oneWonPersistenceService.markSent(10L))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("markFailed")
    class MarkFailed {

        @Test
        @DisplayName("존재하는 세션 → 사유 기록 후 재시도 가능한 GOVERNMENT_VERIFIED로 복구")
        void found_revertsToRetryable() {
            IdentityVerification verification = newVerificationInProgress();
            when(identityVerificationRepository.findById(10L)).thenReturn(Optional.of(verification));

            oneWonPersistenceService.markFailed(10L, "1원 송금에 실패했습니다. 다시 시도해주세요.");

            assertThat(verification.getFailureReason()).isEqualTo("1원 송금에 실패했습니다. 다시 시도해주세요.");
            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.GOVERNMENT_VERIFIED);
        }

        @Test
        @DisplayName("존재하지 않는 세션 → 예외 없이 아무 동작도 하지 않는다")
        void notFound_doesNothing() {
            when(identityVerificationRepository.findById(10L)).thenReturn(Optional.empty());

            assertThatCode(() -> oneWonPersistenceService.markFailed(10L, "사유"))
                    .doesNotThrowAnyException();
        }
    }
}
