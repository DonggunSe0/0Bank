package com.team10.backend.domain.user.verification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class MockBankTransferServiceTest {

    private final MockBankTransferService service = new MockBankTransferService();

    @Nested
    @DisplayName("sendOneWon")
    class SendOneWon {

        @Test
        @DisplayName("Mock 송금은 예외 없이 완료된다")
        void doesNotThrowAnyException() {
            assertThatCode(() -> service.sendOneWon("004", "12345678901", "1234"))
                    .doesNotThrowAnyException();
        }
    }
}
