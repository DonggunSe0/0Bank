package com.team10.backend.domain.user.verification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockGovernmentVerifyServiceTest {

    private final MockGovernmentVerifyService service = new MockGovernmentVerifyService();

    @Nested
    @DisplayName("verify")
    class Verify {

        @Test
        @DisplayName("등록된 시나리오에 해당하지 않으면 VERIFIED")
        void unknownResidentNumber_returnsVerified() {
            GovernmentVerifyResult result = service.verify("홍길동", "901201-1234567", "2023-01-15");

            assertThat(result).isEqualTo(GovernmentVerifyResult.VERIFIED);
        }

        @Test
        @DisplayName("분실·재발급 신분증 트리거 주민번호 → ISSUE_DATE_MISMATCH")
        void issueDateMismatchTrigger_returnsIssueDateMismatch() {
            GovernmentVerifyResult result = service.verify("홍길동", "900101-1111111", "2023-01-15");

            assertThat(result).isEqualTo(GovernmentVerifyResult.ISSUE_DATE_MISMATCH);
        }

        @Test
        @DisplayName("위조 신분증 트리거 주민번호 → IDENTITY_NOT_FOUND")
        void identityNotFoundTrigger_returnsIdentityNotFound() {
            GovernmentVerifyResult result = service.verify("홍길동", "991231-3333333", "2023-01-15");

            assertThat(result).isEqualTo(GovernmentVerifyResult.IDENTITY_NOT_FOUND);
        }

        @Test
        @DisplayName("타임아웃 트리거 주민번호 → GovernmentVerifyTimeoutException (mock 지연 포함, 약 3초 소요)")
        void timeoutTrigger_throwsTimeoutException() {
            assertThatThrownBy(() -> service.verify("홍길동", "800101-1999999", "2023-01-15"))
                    .isInstanceOf(GovernmentVerifyTimeoutException.class);
        }
    }
}
