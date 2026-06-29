package com.team10.backend.domain.user.entity;

import com.team10.backend.domain.user.type.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    private User newUser() {
        return User.create("test@example.com", "encoded-pw", "홍길동", "01012345678", LocalDate.of(1990, 1, 1));
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("생성 시 식별 정보가 설정되고 미인증/활성 상태로 초기화된다")
        void initializesDefaults() {
            User user = newUser();

            assertThat(user.getEmail()).isEqualTo("test@example.com");
            assertThat(user.getPassword()).isEqualTo("encoded-pw");
            assertThat(user.getName()).isEqualTo("홍길동");
            assertThat(user.getPhoneNumber()).isEqualTo("01012345678");
            assertThat(user.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
            assertThat(user.getIdentityVerified()).isFalse();
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("completeIdentityVerification")
    class CompleteIdentityVerification {

        @Test
        @DisplayName("본인인증 완료 처리 시 identityVerified가 true로 전환된다")
        void marksVerified() {
            User user = newUser();

            user.completeIdentityVerification();

            assertThat(user.getIdentityVerified()).isTrue();
        }
    }

    @Nested
    @DisplayName("isIdentityVerificationValid")
    class IsIdentityVerificationValid {

        @Test
        @DisplayName("인증한 적 없으면 false")
        void neverVerified_false() {
            User user = newUser();

            assertThat(user.isIdentityVerificationValid()).isFalse();
        }

        @Test
        @DisplayName("방금 완료했으면 true")
        void justCompleted_true() {
            User user = newUser();

            user.completeIdentityVerification();

            assertThat(user.isIdentityVerificationValid()).isTrue();
        }

        @Test
        @DisplayName("완료 후 30일이 지나면 false (재인증 필요)")
        void expiredAfter30Days_false() {
            User user = newUser();
            user.completeIdentityVerification();
            ReflectionTestUtils.setField(user, "identityVerifiedAt", LocalDateTime.now().minusDays(31));

            assertThat(user.isIdentityVerificationValid()).isFalse();
        }

        @Test
        @DisplayName("완료 후 30일 이내면 true")
        void within30Days_true() {
            User user = newUser();
            user.completeIdentityVerification();
            ReflectionTestUtils.setField(user, "identityVerifiedAt", LocalDateTime.now().minusDays(29));

            assertThat(user.isIdentityVerificationValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("비밀번호가 전달된 값으로 교체된다")
        void replacesPassword() {
            User user = newUser();

            user.changePassword("new-encoded-pw");

            assertThat(user.getPassword()).isEqualTo("new-encoded-pw");
        }
    }

    @Nested
    @DisplayName("withdraw")
    class Withdraw {

        @Test
        @DisplayName("탈퇴 처리 시 상태가 WITHDRAWN으로 전환된다")
        void marksWithdrawn() {
            User user = newUser();

            user.withdraw();

            assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        }
    }
}
