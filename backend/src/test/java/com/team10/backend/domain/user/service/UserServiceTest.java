package com.team10.backend.domain.user.service;

import com.team10.backend.domain.user.client.PortOneClient;
import com.team10.backend.domain.user.client.PortOneIdentityVerification;
import com.team10.backend.domain.user.client.PortOneIdentityVerification.VerifiedCustomer;
import com.team10.backend.domain.user.dto.req.ChangePasswordReq;
import com.team10.backend.domain.user.dto.req.LoginReq;
import com.team10.backend.domain.user.dto.req.TokenRefreshReq;
import com.team10.backend.domain.user.dto.req.UserCreateReq;
import com.team10.backend.domain.user.dto.res.LoginRes;
import com.team10.backend.domain.user.dto.res.TokenRefreshRes;
import com.team10.backend.domain.user.dto.res.UserRes;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.domain.user.type.AgeGroup;
import com.team10.backend.domain.user.type.FinancialInterest;
import com.team10.backend.domain.user.type.OccupationStatus;
import com.team10.backend.domain.user.type.Region;
import com.team10.backend.domain.user.type.UserStatus;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.jwt.JwtProvider;
import com.team10.backend.global.jwt.RefreshTokenService;
import com.team10.backend.global.jwt.TokenBlocklistService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock BCryptPasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;
    @Mock RefreshTokenService refreshTokenService;
    @Mock UserConsentService userConsentService;
    @Mock UserProfileService userProfileService;
    @Mock LoginAttemptService loginAttemptService;
    @Mock TokenBlocklistService tokenBlocklistService;
    @Mock PortOneClient portOneClient;
    @Mock PlatformTransactionManager txManager;

    @InjectMocks
    UserService userService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = createUser(1L, "test@test.com", "encoded_password", UserStatus.ACTIVE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // signup
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("signup")
    class Signup {

        @Test
        @DisplayName("정상 회원가입 — 유저 저장 후 UserRes 반환")
        void success() {
            UserCreateReq req = signupReq("test@test.com", "홍길동", "01012345678",
                    LocalDate.of(1990, 1, 1));
            PortOneIdentityVerification verification = verifiedPortOne(
                    "홍길동", "1990-01-01", "01012345678");

            when(portOneClient.getIdentityVerification("portone-id")).thenReturn(verification);
            TransactionStatus txStatus = mock(TransactionStatus.class);
            when(txManager.getTransaction(any())).thenReturn(txStatus);
            when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
            when(passwordEncoder.encode("Password1!")).thenReturn("encoded");
            when(userRepository.saveAndFlush(any())).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                ReflectionTestUtils.setField(u, "id", 1L);
                return u;
            });

            UserRes res = userService.signup(req);

            assertThat(res.email()).isEqualTo("test@test.com");
            assertThat(res.name()).isEqualTo("홍길동");
            verify(userConsentService).saveAll(any(), eq(true), eq(true), eq(true), eq(false));
            verify(userProfileService).create(eq(1L), any());
        }

        @Test
        @DisplayName("PortOne 미인증 → IDENTITY_VERIFICATION_FAILED")
        void portOneNotVerified() {
            UserCreateReq req = signupReq("test@test.com", "홍길동", "01012345678",
                    LocalDate.of(1990, 1, 1));
            PortOneIdentityVerification unverified =
                    new PortOneIdentityVerification("UNVERIFIED", null);

            when(portOneClient.getIdentityVerification("portone-id")).thenReturn(unverified);

            assertThatThrownBy(() -> userService.signup(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        @Test
        @DisplayName("이름 불일치 → IDENTITY_VERIFICATION_NAME_MISMATCH")
        void nameMismatch() {
            UserCreateReq req = signupReq("test@test.com", "홍길동", "01012345678",
                    LocalDate.of(1990, 1, 1));
            PortOneIdentityVerification verification = verifiedPortOne(
                    "김철수", "1990-01-01", "01012345678"); // 다른 이름

            when(portOneClient.getIdentityVerification("portone-id")).thenReturn(verification);

            assertThatThrownBy(() -> userService.signup(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.IDENTITY_VERIFICATION_NAME_MISMATCH);
        }

        @Test
        @DisplayName("생년월일 불일치 → IDENTITY_VERIFICATION_FAILED")
        void birthDateMismatch() {
            UserCreateReq req = signupReq("test@test.com", "홍길동", "01012345678",
                    LocalDate.of(1990, 1, 1));
            PortOneIdentityVerification verification = verifiedPortOne(
                    "홍길동", "1991-01-01", "01012345678"); // 다른 생년월일

            when(portOneClient.getIdentityVerification("portone-id")).thenReturn(verification);

            assertThatThrownBy(() -> userService.signup(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        @Test
        @DisplayName("전화번호 불일치 → IDENTITY_VERIFICATION_FAILED")
        void phoneMismatch() {
            UserCreateReq req = signupReq("test@test.com", "홍길동", "01012345678",
                    LocalDate.of(1990, 1, 1));
            PortOneIdentityVerification verification = verifiedPortOne(
                    "홍길동", "1990-01-01", "01099999999"); // 다른 번호

            when(portOneClient.getIdentityVerification("portone-id")).thenReturn(verification);

            assertThatThrownBy(() -> userService.signup(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        @Test
        @DisplayName("이메일 중복 → DUPLICATE_EMAIL")
        void duplicateEmail() {
            UserCreateReq req = signupReq("test@test.com", "홍길동", "01012345678",
                    LocalDate.of(1990, 1, 1));
            PortOneIdentityVerification verification = verifiedPortOne(
                    "홍길동", "1990-01-01", "01012345678");

            when(portOneClient.getIdentityVerification("portone-id")).thenReturn(verification);
            TransactionStatus txStatus = mock(TransactionStatus.class);
            when(txManager.getTransaction(any())).thenReturn(txStatus);
            when(userRepository.existsByEmail("test@test.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.signup(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.DUPLICATE_EMAIL);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getMe
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMe")
    class GetMe {

        @Test
        @DisplayName("정상 조회 — UserRes 반환")
        void success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

            UserRes res = userService.getMe(1L);

            assertThat(res.id()).isEqualTo(1L);
            assertThat(res.email()).isEqualTo("test@test.com");
        }

        @Test
        @DisplayName("사용자 없음 → USER_NOT_FOUND")
        void userNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getMe(99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // login
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("정상 로그인 — AT + RT 반환")
        void success() {
            LoginReq req = new LoginReq("test@test.com", "Password1!");

            when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("Password1!", "encoded_password")).thenReturn(true);
            when(jwtProvider.createAccessToken(1L, "test@test.com")).thenReturn("access-token");
            when(refreshTokenService.issue(1L)).thenReturn("refresh-token");

            LoginRes res = userService.login(req);

            assertThat(res.accessToken()).isEqualTo("access-token");
            assertThat(res.refreshToken()).isEqualTo("refresh-token");
            assertThat(res.user().email()).isEqualTo("test@test.com");
            verify(loginAttemptService).clearFailures("test@test.com");
        }

        @Test
        @DisplayName("이메일 없음 → INVALID_CREDENTIALS + 실패 횟수 증가")
        void emailNotFound() {
            LoginReq req = new LoginReq("none@test.com", "Password1!");

            when(userRepository.findByEmail("none@test.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.login(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_CREDENTIALS);

            verify(loginAttemptService).recordFailure("none@test.com");
        }

        @Test
        @DisplayName("비밀번호 불일치 → INVALID_CREDENTIALS + 실패 횟수 증가")
        void wrongPassword() {
            LoginReq req = new LoginReq("test@test.com", "WrongPass1!");

            when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("WrongPass1!", "encoded_password")).thenReturn(false);

            assertThatThrownBy(() -> userService.login(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_CREDENTIALS);

            verify(loginAttemptService).recordFailure("test@test.com");
        }

        @Test
        @DisplayName("로그인 잠금 상태 → LOGIN_LOCKED")
        void loginLocked() {
            LoginReq req = new LoginReq("test@test.com", "Password1!");

            doThrow(new BusinessException(UserErrorCode.LOGIN_LOCKED))
                    .when(loginAttemptService).checkAndThrowIfLocked("test@test.com");

            assertThatThrownBy(() -> userService.login(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.LOGIN_LOCKED);
        }

        @Test
        @DisplayName("휴면 계정 → DORMANT_ACCOUNT")
        void dormantAccount() {
            User dormant = createUser(2L, "dormant@test.com", "encoded_password", UserStatus.DORMANT);
            LoginReq req = new LoginReq("dormant@test.com", "Password1!");

            when(userRepository.findByEmail("dormant@test.com")).thenReturn(Optional.of(dormant));
            when(passwordEncoder.matches("Password1!", "encoded_password")).thenReturn(true);

            assertThatThrownBy(() -> userService.login(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.DORMANT_ACCOUNT);
        }

        @Test
        @DisplayName("탈퇴 계정 → WITHDRAWN_ACCOUNT")
        void withdrawnAccount() {
            User withdrawn = createUser(3L, "out@test.com", "encoded_password", UserStatus.WITHDRAWN);
            LoginReq req = new LoginReq("out@test.com", "Password1!");

            when(userRepository.findByEmail("out@test.com")).thenReturn(Optional.of(withdrawn));
            when(passwordEncoder.matches("Password1!", "encoded_password")).thenReturn(true);

            assertThatThrownBy(() -> userService.login(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.WITHDRAWN_ACCOUNT);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // withdraw
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("withdraw")
    class Withdraw {

        @Test
        @DisplayName("정상 탈퇴 — WITHDRAWN 상태 + RT 삭제")
        void success() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

            userService.withdraw(1L, null);

            assertThat(activeUser.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
            verify(refreshTokenService).delete(1L);
        }

        @Test
        @DisplayName("사용자 없음 → USER_NOT_FOUND")
        void userNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.withdraw(99L, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // refresh
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        @DisplayName("정상 재발급 — 새 AT + 새 RT")
        void success() {
            TokenRefreshReq req = new TokenRefreshReq("old-access-token");

            when(jwtProvider.parseUserIdIgnoreExpiry("old-access-token")).thenReturn(1L);
            when(refreshTokenService.validateAndConsume(1L, "old-refresh-token")).thenReturn(true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
            when(jwtProvider.createAccessToken(1L, "test@test.com")).thenReturn("new-access-token");
            when(refreshTokenService.issue(1L)).thenReturn("new-refresh-token");

            TokenRefreshRes res = userService.refresh(req, "old-refresh-token");

            assertThat(res.accessToken()).isEqualTo("new-access-token");
            assertThat(res.refreshToken()).isEqualTo("new-refresh-token");
        }

        @Test
        @DisplayName("AT 파싱 실패 → INVALID_REFRESH_TOKEN")
        void invalidAccessToken() {
            TokenRefreshReq req = new TokenRefreshReq("malformed-token");

            when(jwtProvider.parseUserIdIgnoreExpiry("malformed-token"))
                    .thenThrow(new JwtException("invalid"));

            assertThatThrownBy(() -> userService.refresh(req, "refresh-token"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        @Test
        @DisplayName("RT 불일치 → INVALID_REFRESH_TOKEN")
        void refreshTokenMismatch() {
            TokenRefreshReq req = new TokenRefreshReq("old-access-token");

            when(jwtProvider.parseUserIdIgnoreExpiry("old-access-token")).thenReturn(1L);
            when(refreshTokenService.validateAndConsume(1L, "wrong-refresh")).thenReturn(false);

            assertThatThrownBy(() -> userService.refresh(req, "wrong-refresh"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        @Test
        @DisplayName("사용자 없음 → USER_NOT_FOUND")
        void userNotFound() {
            TokenRefreshReq req = new TokenRefreshReq("old-access-token");

            when(jwtProvider.parseUserIdIgnoreExpiry("old-access-token")).thenReturn(99L);
            when(refreshTokenService.validateAndConsume(99L, "refresh-token")).thenReturn(true);
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.refresh(req, "refresh-token"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // changePassword
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("정상 변경 — 새 비밀번호 저장 + RT 삭제")
        void success() {
            ChangePasswordReq req = new ChangePasswordReq("OldPass1!", "NewPass1!");

            when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("OldPass1!", "encoded_password")).thenReturn(true);
            when(passwordEncoder.encode("NewPass1!")).thenReturn("new_encoded");

            userService.changePassword(1L, req, null);

            assertThat(activeUser.getPassword()).isEqualTo("new_encoded");
            verify(refreshTokenService).delete(1L);
        }

        @Test
        @DisplayName("AT 블랙리스트 등록 — accessToken 전달 시")
        void blacklistsAccessToken() {
            ChangePasswordReq req = new ChangePasswordReq("OldPass1!", "NewPass1!");

            when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("OldPass1!", "encoded_password")).thenReturn(true);
            when(passwordEncoder.encode("NewPass1!")).thenReturn("new_encoded");
            when(jwtProvider.extractJti("access-token")).thenReturn("jti-123");
            when(jwtProvider.getRemainingExpirySeconds("access-token")).thenReturn(3600L);

            // resolveBearerToken은 이제 컨트롤러 단에서 끝나므로, 서비스는 이미 resolve된
            // accessToken("Bearer " 접두사 제거 후 값)을 그대로 받는다.
            userService.changePassword(1L, req, "access-token");

            verify(tokenBlocklistService).block("jti-123", 3600L);
        }

        @Test
        @DisplayName("사용자 없음 → USER_NOT_FOUND")
        void userNotFound() {
            ChangePasswordReq req = new ChangePasswordReq("OldPass1!", "NewPass1!");

            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changePassword(99L, req, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("현재 비밀번호 불일치 → INVALID_CURRENT_PASSWORD")
        void wrongCurrentPassword() {
            ChangePasswordReq req = new ChangePasswordReq("WrongOld1!", "NewPass1!");

            when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("WrongOld1!", "encoded_password")).thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword(1L, req, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.INVALID_CURRENT_PASSWORD);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // logout
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("정상 로그아웃 — RT 삭제 + AT 블랙리스트 등록")
        void success() {
            when(jwtProvider.extractJti("access-token")).thenReturn("jti-123");
            when(jwtProvider.getRemainingExpirySeconds("access-token")).thenReturn(3600L);

            userService.logout(1L, "access-token");

            verify(refreshTokenService).delete(1L);
            verify(tokenBlocklistService).block("jti-123", 3600L);
        }

        @Test
        @DisplayName("AT 파싱 실패해도 RT는 삭제됨")
        void atParseFailure_rtStillDeleted() {
            when(jwtProvider.extractJti(anyString())).thenThrow(new RuntimeException("parse error"));

            userService.logout(1L, "malformed-token");

            verify(refreshTokenService).delete(1L);
            verify(tokenBlocklistService, never()).block(anyString(), anyLong());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────────────────

    private User createUser(Long id, String email, String encodedPassword, UserStatus status) {
        try {
            Constructor<User> c = User.class.getDeclaredConstructor();
            c.setAccessible(true);
            User u = c.newInstance();
            ReflectionTestUtils.setField(u, "id", id);
            ReflectionTestUtils.setField(u, "email", email);
            ReflectionTestUtils.setField(u, "password", encodedPassword);
            ReflectionTestUtils.setField(u, "name", "홍길동");
            ReflectionTestUtils.setField(u, "phoneNumber", "01012345678");
            ReflectionTestUtils.setField(u, "birthDate", LocalDate.of(1990, 1, 1));
            ReflectionTestUtils.setField(u, "identityVerified", false);
            ReflectionTestUtils.setField(u, "status", status);
            return u;
        } catch (Exception e) {
            throw new IllegalStateException("User 생성 실패", e);
        }
    }

    private UserCreateReq signupReq(String email, String name, String phone, LocalDate birthDate) {
        return new UserCreateReq(
                "portone-id", email, "Password1!", name, phone, birthDate,
                AgeGroup.TWENTIES, Region.SEOUL, OccupationStatus.EMPLOYED, Set.of(FinancialInterest.SAVINGS),
                true, true, true, false);
    }

    private PortOneIdentityVerification verifiedPortOne(String name, String birthDate, String phone) {
        return new PortOneIdentityVerification("VERIFIED", new VerifiedCustomer(name, birthDate, phone));
    }
}
