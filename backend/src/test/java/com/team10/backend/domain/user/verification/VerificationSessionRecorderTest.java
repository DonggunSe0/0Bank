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
class VerificationSessionRecorderTest {

    @Mock
    IdentityVerificationRepository identityVerificationRepository;

    @InjectMocks
    VerificationSessionRecorder verificationSessionRecorder;

    @Nested
    @DisplayName("markFailedInNewTransaction")
    class MarkFailedInNewTransaction {

        @Test
        @DisplayName("존재하는 세션 → FAILED 상태로 전환되고 사유가 기록된다")
        void found_marksFailed() {
            IdentityVerification verification = IdentityVerification.startOcr(mock(User.class));
            when(identityVerificationRepository.findById(10L)).thenReturn(Optional.of(verification));

            verificationSessionRecorder.markFailedInNewTransaction(10L, "행안부 연동 타임아웃: 잠시 후 다시 시도해주세요.");

            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.FAILED);
            assertThat(verification.getFailureReason()).isEqualTo("행안부 연동 타임아웃: 잠시 후 다시 시도해주세요.");
        }

        @Test
        @DisplayName("존재하지 않는 세션 → 예외 없이 아무 동작도 하지 않는다")
        void notFound_doesNothing() {
            when(identityVerificationRepository.findById(10L)).thenReturn(Optional.empty());

            assertThatCode(() ->
                    verificationSessionRecorder.markFailedInNewTransaction(10L, "사유")
            ).doesNotThrowAnyException();
        }
    }
}
