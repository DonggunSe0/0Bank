package com.team10.backend.domain.user.entity;

import com.team10.backend.domain.user.type.VerificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityVerificationTest {

    private User newUser() {
        return User.create("test@example.com", "encoded-pw", "홍길동", "01012345678", LocalDate.of(1990, 1, 1));
    }

    @Nested
    @DisplayName("startOcr")
    class StartOcr {

        @Test
        @DisplayName("OCR_PENDING 상태로 생성된다")
        void createsWithOcrPendingStatus() {
            IdentityVerification verification = IdentityVerification.startOcr(newUser());

            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.OCR_PENDING);
        }
    }

    @Nested
    @DisplayName("completeOcr")
    class CompleteOcr {

        @Test
        @DisplayName("주민번호는 즉시 마스킹되어 기록되고, 해시와 함께 OCR_COMPLETED로 전환된다")
        void recordsMaskedResultAndHash_andTransitions() {
            IdentityVerification verification = IdentityVerification.startOcr(newUser());

            verification.completeOcr("홍길동", "901201-1234567", "2023-01-15", "hashed-value");

            assertThat(verification.getOcrName()).isEqualTo("홍길동");
            assertThat(verification.getOcrResidentNumber()).isEqualTo("901201-*******");
            assertThat(verification.getOcrResidentNumberHash()).isEqualTo("hashed-value");
            assertThat(verification.getOcrIssueDate()).isEqualTo("2023-01-15");
            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.OCR_COMPLETED);
        }

        @Test
        @DisplayName("주민번호 평문은 어떤 필드에도 남지 않는다")
        void neverRetainsPlainResidentNumber() {
            IdentityVerification verification = IdentityVerification.startOcr(newUser());

            verification.completeOcr("홍길동", "901201-1234567", "2023-01-15", "hashed-value");

            assertThat(verification.getOcrResidentNumber()).doesNotContain("1234567");
        }
    }

    @Nested
    @DisplayName("completeGovernmentVerification")
    class CompleteGovernmentVerification {

        @Test
        @DisplayName("마스킹된 주민번호를 유지한 채 GOVERNMENT_VERIFIED로 전환된다")
        void transitionsKeepingMaskedResidentNumber() {
            IdentityVerification verification = IdentityVerification.startOcr(newUser());
            verification.completeOcr("홍길동", "901201-1234567", "2023-01-15", "hashed-value");

            verification.completeGovernmentVerification();

            assertThat(verification.getOcrResidentNumber()).isEqualTo("901201-*******");
            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.GOVERNMENT_VERIFIED);
        }

        @Test
        @DisplayName("주민번호가 없으면 상태만 전환된다")
        void noResidentNumber_transitions() {
            IdentityVerification verification = IdentityVerification.startOcr(newUser());

            verification.completeGovernmentVerification();

            assertThat(verification.getOcrResidentNumber()).isNull();
            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.GOVERNMENT_VERIFIED);
        }
    }

    @Nested
    @DisplayName("startOneWon / completeOneWon")
    class OneWonFlow {

        @Test
        @DisplayName("1원 송금 시작 시 ONE_WON_PENDING으로 전환된다")
        void startOneWon_transitions() {
            IdentityVerification verification = IdentityVerification.startOcr(newUser());

            verification.startOneWon();

            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.ONE_WON_PENDING);
        }

        @Test
        @DisplayName("1원 송금 코드 검증 완료 시 COMPLETED로 전환된다")
        void completeOneWon_transitions() {
            IdentityVerification verification = IdentityVerification.startOcr(newUser());
            verification.startOneWon();

            verification.completeOneWon();

            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("requestOneWonTransfer / revertOneWonRequest")
    class OneWonAsyncFlow {

        @Test
        @DisplayName("1원 송금 요청 접수 시 ONE_WON_IN_PROGRESS로 전환된다")
        void requestOneWonTransfer_transitions() {
            IdentityVerification verification = IdentityVerification.startOcr(newUser());

            verification.requestOneWonTransfer();

            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.ONE_WON_IN_PROGRESS);
        }

        @Test
        @DisplayName("비동기 송금 실패 시 사유를 기록하고 재시도 가능한 GOVERNMENT_VERIFIED로 복구된다")
        void revertOneWonRequest_recordsReasonAndRevertsToRetryable() {
            IdentityVerification verification = IdentityVerification.startOcr(newUser());
            verification.requestOneWonTransfer();

            verification.revertOneWonRequest("1원 송금에 실패했습니다. 다시 시도해주세요.");

            assertThat(verification.getFailureReason()).isEqualTo("1원 송금에 실패했습니다. 다시 시도해주세요.");
            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.GOVERNMENT_VERIFIED);
        }
    }

    @Nested
    @DisplayName("fail")
    class Fail {

        @Test
        @DisplayName("실패 사유를 기록하고 마스킹된 주민번호를 유지한 채 FAILED로 전환된다")
        void recordsReasonAndTransitions() {
            IdentityVerification verification = IdentityVerification.startOcr(newUser());
            verification.completeOcr("홍길동", "901201-1234567", "2023-01-15", "hashed-value");

            verification.fail("행안부 연동 타임아웃: 잠시 후 다시 시도해주세요.");

            assertThat(verification.getFailureReason()).isEqualTo("행안부 연동 타임아웃: 잠시 후 다시 시도해주세요.");
            assertThat(verification.getOcrResidentNumber()).isEqualTo("901201-*******");
            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.FAILED);
        }

        @Test
        @DisplayName("주민번호가 없는 상태에서 실패해도 예외 없이 FAILED로 전환된다")
        void noResidentNumber_stillTransitions() {
            IdentityVerification verification = IdentityVerification.startOcr(newUser());

            verification.fail("OCR 처리 실패");

            assertThat(verification.getFailureReason()).isEqualTo("OCR 처리 실패");
            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.FAILED);
        }
    }
}
