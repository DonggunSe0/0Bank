package com.team10.backend.domain.user.entity;

import com.team10.backend.domain.user.type.TermsType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserConsentTest {

    private User newUser() {
        return User.create("test@example.com", "encoded-pw", "홍길동", "01012345678", LocalDate.of(1990, 1, 1));
    }

    @Nested
    @DisplayName("of")
    class Of {

        @Test
        @DisplayName("동의함(true)이면 agreedAt이 현재 시각으로 채워진다")
        void agreed_setsAgreedAt() {
            UserConsent consent = UserConsent.of(newUser(), TermsType.MARKETING, true);

            assertThat(consent.getAgreed()).isTrue();
            assertThat(consent.getAgreedAt()).isNotNull();
        }

        @Test
        @DisplayName("동의안함(false)이면 agreedAt이 null이다")
        void notAgreed_agreedAtIsNull() {
            UserConsent consent = UserConsent.of(newUser(), TermsType.MARKETING, false);

            assertThat(consent.getAgreed()).isFalse();
            assertThat(consent.getAgreedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("true로 갱신하면 동의 상태와 agreedAt이 채워진다")
        void updateToAgreed_setsAgreedAt() {
            UserConsent consent = UserConsent.of(newUser(), TermsType.MARKETING, false);

            consent.update(true);

            assertThat(consent.getAgreed()).isTrue();
            assertThat(consent.getAgreedAt()).isNotNull();
        }

        @Test
        @DisplayName("false로 갱신하면 동의 상태가 해제되고 agreedAt이 null로 초기화된다")
        void updateToNotAgreed_clearsAgreedAt() {
            UserConsent consent = UserConsent.of(newUser(), TermsType.MARKETING, true);

            consent.update(false);

            assertThat(consent.getAgreed()).isFalse();
            assertThat(consent.getAgreedAt()).isNull();
        }
    }
}
