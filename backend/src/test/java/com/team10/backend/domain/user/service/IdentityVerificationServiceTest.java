package com.team10.backend.domain.user.service;

import com.team10.backend.domain.user.dto.req.OneWonStartReq;
import com.team10.backend.domain.user.dto.req.OneWonVerifyReq;
import com.team10.backend.domain.user.dto.res.OcrAcceptedRes;
import com.team10.backend.domain.user.dto.res.OneWonStartRes;
import com.team10.backend.domain.user.dto.res.OneWonVerifyRes;
import com.team10.backend.domain.user.entity.IdentityVerification;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.ocr.OcrService;
import com.team10.backend.domain.user.repository.IdentityVerificationRepository;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.domain.user.type.VerificationStatus;
import com.team10.backend.domain.user.verification.BankTransferService;
import com.team10.backend.domain.user.verification.OneWonVerificationService;
import com.team10.backend.domain.user.verification.OneWonVerificationService.VerifyResult;
import com.team10.backend.domain.user.verification.VerificationSessionRecorder;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityVerificationServiceTest {

    @Mock UserRepository userRepository;
    @Mock IdentityVerificationRepository identityVerificationRepository;
    @Mock OcrService ocrService;
    @Mock BankTransferService bankTransferService;
    @Mock OneWonVerificationService oneWonVerificationService;
    @Mock VerificationSessionRecorder verificationSessionRecorder;
    @Mock PlatformTransactionManager txManager;
    @Mock StringRedisTemplate redisTemplate;

    @InjectMocks
    IdentityVerificationService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = createUser(1L, false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // submitIdCardOcr
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("submitIdCardOcr")
    class SubmitIdCardOcr {

        @Test
        @DisplayName("정상 접수 — OCR_PENDING 상태와 세션 ID 반환")
        void success() {
            MockMultipartFile image = jpegFile(1024);

            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(identityVerificationRepository.save(any())).thenAnswer(inv -> {
                IdentityVerification v = inv.getArgument(0);
                ReflectionTestUtils.setField(v, "id", 10L);
                return v;
            });

            try (MockedStatic<TransactionSynchronizationManager> tsm =
                         mockStatic(TransactionSynchronizationManager.class)) {
                tsm.when(() -> TransactionSynchronizationManager.registerSynchronization(any()))
                        .then(inv -> null);

                OcrAcceptedRes res = service.submitIdCardOcr(1L, image);

                assertThat(res.verificationId()).isEqualTo(10L);
                assertThat(res.status()).isEqualTo(VerificationStatus.OCR_PENDING);
            }
        }

        @Test
        @DisplayName("이미지 없음 → OCR_IMAGE_REQUIRED")
        void noImage() {
            MockMultipartFile empty = new MockMultipartFile("image", new byte[0]);

            assertThatThrownBy(() -> service.submitIdCardOcr(1L, empty))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_IMAGE_REQUIRED);
        }

        @Test
        @DisplayName("10MB 초과 → OCR_IMAGE_TOO_LARGE")
        void tooLarge() {
            MockMultipartFile large = new MockMultipartFile(
                    "image", "test.jpg", "image/jpeg", new byte[11 * 1024 * 1024]);

            assertThatThrownBy(() -> service.submitIdCardOcr(1L, large))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_IMAGE_TOO_LARGE);
        }

        @Test
        @DisplayName("지원하지 않는 이미지 타입 → OCR_IMAGE_INVALID_TYPE")
        void invalidType() {
            MockMultipartFile gif = new MockMultipartFile(
                    "image", "test.gif", "image/gif", new byte[1024]);

            assertThatThrownBy(() -> service.submitIdCardOcr(1L, gif))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_IMAGE_INVALID_TYPE);
        }

        @Test
        @DisplayName("일일 한도(5회) 초과 → OCR_DAILY_LIMIT_EXCEEDED")
        void dailyLimitExceeded() {
            MockMultipartFile image = jpegFile(1024);

            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(6L);

            assertThatThrownBy(() -> service.submitIdCardOcr(1L, image))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_DAILY_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("사용자 없음 → USER_NOT_FOUND")
        void userNotFound() {
            MockMultipartFile image = jpegFile(1024);

            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submitIdCardOcr(1L, image))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 본인인증 완료 → IDENTITY_ALREADY_VERIFIED")
        void alreadyVerified() {
            MockMultipartFile image = jpegFile(1024);
            User alreadyVerified = createUser(1L, true);

            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(alreadyVerified));

            assertThatThrownBy(() -> service.submitIdCardOcr(1L, image))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.IDENTITY_ALREADY_VERIFIED);
        }

        @Test
        @DisplayName("스레드풀 포화로 OCR 작업 거부 → FAILED로 별도 트랜잭션 기록")
        void ocrRejectedDueToPoolSaturation() {
            MockMultipartFile image = jpegFile(1024);

            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(identityVerificationRepository.save(any())).thenAnswer(inv -> {
                IdentityVerification v = inv.getArgument(0);
                ReflectionTestUtils.setField(v, "id", 10L);
                return v;
            });
            doThrow(new RejectedExecutionException("queue full"))
                    .when(ocrService).processAsync(any(), eq(10L));

            try (MockedStatic<TransactionSynchronizationManager> tsm =
                         mockStatic(TransactionSynchronizationManager.class)) {
                tsm.when(() -> TransactionSynchronizationManager.registerSynchronization(any()))
                        .then(inv -> {
                            TransactionSynchronization sync = inv.getArgument(0);
                            sync.afterCommit(); // 실제 커밋 후 콜백을 즉시 실행하여 거부 처리 분기 검증
                            return null;
                        });

                OcrAcceptedRes res = service.submitIdCardOcr(1L, image);

                // 응답 자체는 접수 시점 상태(OCR_PENDING) 그대로 반환 — 거부는 비동기 처리 단계에서 발생
                assertThat(res.status()).isEqualTo(VerificationStatus.OCR_PENDING);
            }

            verify(verificationSessionRecorder).markFailedInNewTransaction(eq(10L), anyString());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // startOneWonVerification
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("startOneWonVerification")
    class StartOneWonVerification {

        @Test
        @DisplayName("정상 흐름 — 1원 송금 후 ONE_WON_PENDING 반환")
        void success() {
            IdentityVerification verification =
                    createVerification(10L, user, VerificationStatus.GOVERNMENT_VERIFIED);
            OneWonStartReq req = new OneWonStartReq("12345678901", "090"); // 카카오뱅크 (점검 없음)

            when(oneWonVerificationService.tryAcquireStartLock(1L)).thenReturn(true);
            when(identityVerificationRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(verification));
            when(oneWonVerificationService.generateAndStore(10L, 1L)).thenReturn("1234");
            when(identityVerificationRepository.findById(10L)).thenReturn(Optional.of(verification));
            TransactionStatus txStatus = mock(TransactionStatus.class);
            when(txManager.getTransaction(any())).thenReturn(txStatus);

            OneWonStartRes res = service.startOneWonVerification(1L, req);

            assertThat(res.status()).isEqualTo(VerificationStatus.ONE_WON_PENDING);
            verify(bankTransferService).sendOneWon("090", "12345678901", "1234");
            verify(oneWonVerificationService).releaseStartLock(1L);
        }

        @Test
        @DisplayName("행안부 인증 미완료 상태 → VERIFICATION_NOT_READY_FOR_ONE_WON")
        void notReady() {
            IdentityVerification verification =
                    createVerification(10L, user, VerificationStatus.OCR_PENDING);
            OneWonStartReq req = new OneWonStartReq("12345678901", "004");

            when(oneWonVerificationService.tryAcquireStartLock(1L)).thenReturn(true);
            when(identityVerificationRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(verification));

            assertThatThrownBy(() -> service.startOneWonVerification(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.VERIFICATION_NOT_READY_FOR_ONE_WON);
        }

        @Test
        @DisplayName("세션 없음 → VERIFICATION_NOT_READY_FOR_ONE_WON")
        void sessionNotFound() {
            OneWonStartReq req = new OneWonStartReq("12345678901", "004");

            when(oneWonVerificationService.tryAcquireStartLock(1L)).thenReturn(true);
            when(identityVerificationRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.startOneWonVerification(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.VERIFICATION_NOT_READY_FOR_ONE_WON);
        }

        @Test
        @DisplayName("지원하지 않는 기관코드 → UNSUPPORTED_BANK")
        void unsupportedBank() {
            IdentityVerification verification =
                    createVerification(10L, user, VerificationStatus.GOVERNMENT_VERIFIED);
            OneWonStartReq req = new OneWonStartReq("12345678901", "999");

            when(oneWonVerificationService.tryAcquireStartLock(1L)).thenReturn(true);
            when(identityVerificationRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(verification));

            assertThatThrownBy(() -> service.startOneWonVerification(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.UNSUPPORTED_BANK);
        }

        @Test
        @DisplayName("은행 점검 시간대 → BANK_MAINTENANCE")
        void bankMaintenance() {
            IdentityVerification verification =
                    createVerification(10L, user, VerificationStatus.GOVERNMENT_VERIFIED);
            OneWonStartReq req = new OneWonStartReq("12345678901", "004"); // 국민은행 23:30~00:30

            when(oneWonVerificationService.tryAcquireStartLock(1L)).thenReturn(true);
            when(identityVerificationRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(verification));

            LocalTime maintenanceTime = LocalTime.of(23, 45); // mockStatic 블록 밖에서 미리 생성
        try (MockedStatic<LocalTime> lt = mockStatic(LocalTime.class, CALLS_REAL_METHODS)) {
                lt.when(() -> LocalTime.now(any(ZoneId.class))).thenReturn(maintenanceTime);

                assertThatThrownBy(() -> service.startOneWonVerification(1L, req))
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode").isEqualTo(UserErrorCode.BANK_MAINTENANCE);
            }
        }

        @Test
        @DisplayName("송금 실패 시 Redis 코드 + 카운터 롤백")
        void transferFailed_rollback() {
            IdentityVerification verification =
                    createVerification(10L, user, VerificationStatus.GOVERNMENT_VERIFIED);
            OneWonStartReq req = new OneWonStartReq("12345678901", "090");

            when(oneWonVerificationService.tryAcquireStartLock(1L)).thenReturn(true);
            when(identityVerificationRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(verification));
            when(oneWonVerificationService.generateAndStore(10L, 1L)).thenReturn("1234");
            doThrow(new BusinessException(UserErrorCode.ONE_WON_TRANSFER_FAILED))
                    .when(bankTransferService).sendOneWon(any(), any(), any());

            assertThatThrownBy(() -> service.startOneWonVerification(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.ONE_WON_TRANSFER_FAILED);

            verify(oneWonVerificationService).deleteCode(10L);
            verify(oneWonVerificationService).decrementDailyCount(1L);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // verifyOneWonCode
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("verifyOneWonCode")
    class VerifyOneWonCode {

        @Test
        @DisplayName("코드 일치 → COMPLETED + identityVerified=true")
        void success() {
            IdentityVerification verification =
                    createVerification(10L, user, VerificationStatus.ONE_WON_PENDING);
            OneWonVerifyReq req = new OneWonVerifyReq("1234");

            when(identityVerificationRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                    1L, VerificationStatus.ONE_WON_PENDING))
                    .thenReturn(Optional.of(verification));
            when(oneWonVerificationService.verify(10L, "1234")).thenReturn(VerifyResult.MATCHED);

            OneWonVerifyRes res = service.verifyOneWonCode(1L, req);

            assertThat(res.status()).isEqualTo(VerificationStatus.COMPLETED);
            assertThat(user.getIdentityVerified()).isTrue();
        }

        @Test
        @DisplayName("코드 만료 → ONE_WON_CODE_EXPIRED")
        void expired() {
            IdentityVerification verification =
                    createVerification(10L, user, VerificationStatus.ONE_WON_PENDING);
            OneWonVerifyReq req = new OneWonVerifyReq("1234");

            when(identityVerificationRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                    1L, VerificationStatus.ONE_WON_PENDING))
                    .thenReturn(Optional.of(verification));
            when(oneWonVerificationService.verify(10L, "1234")).thenReturn(VerifyResult.EXPIRED);

            assertThatThrownBy(() -> service.verifyOneWonCode(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.ONE_WON_CODE_EXPIRED);
        }

        @Test
        @DisplayName("시도 횟수 초과 → ONE_WON_ATTEMPT_EXCEEDED")
        void locked() {
            IdentityVerification verification =
                    createVerification(10L, user, VerificationStatus.ONE_WON_PENDING);
            OneWonVerifyReq req = new OneWonVerifyReq("9999");

            when(identityVerificationRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                    1L, VerificationStatus.ONE_WON_PENDING))
                    .thenReturn(Optional.of(verification));
            when(oneWonVerificationService.verify(10L, "9999")).thenReturn(VerifyResult.LOCKED);

            assertThatThrownBy(() -> service.verifyOneWonCode(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.ONE_WON_ATTEMPT_EXCEEDED);
        }

        @Test
        @DisplayName("코드 불일치 → ONE_WON_CODE_MISMATCH")
        void mismatch() {
            IdentityVerification verification =
                    createVerification(10L, user, VerificationStatus.ONE_WON_PENDING);
            OneWonVerifyReq req = new OneWonVerifyReq("9999");

            when(identityVerificationRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                    1L, VerificationStatus.ONE_WON_PENDING))
                    .thenReturn(Optional.of(verification));
            when(oneWonVerificationService.verify(10L, "9999")).thenReturn(VerifyResult.MISMATCH);

            assertThatThrownBy(() -> service.verifyOneWonCode(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.ONE_WON_CODE_MISMATCH);
        }

        @Test
        @DisplayName("진행 중인 세션 없음 → VERIFICATION_SESSION_NOT_FOUND")
        void sessionNotFound() {
            OneWonVerifyReq req = new OneWonVerifyReq("1234");

            when(identityVerificationRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(
                    1L, VerificationStatus.ONE_WON_PENDING))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.verifyOneWonCode(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.VERIFICATION_SESSION_NOT_FOUND);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────────────────

    private MockMultipartFile jpegFile(int size) {
        return new MockMultipartFile("image", "test.jpg", "image/jpeg", new byte[size]);
    }

    private User createUser(Long id, boolean identityVerified) {
        try {
            Constructor<User> c = User.class.getDeclaredConstructor();
            c.setAccessible(true);
            User u = c.newInstance();
            ReflectionTestUtils.setField(u, "id", id);
            ReflectionTestUtils.setField(u, "email", "user" + id + "@test.com");
            ReflectionTestUtils.setField(u, "password", "password");
            ReflectionTestUtils.setField(u, "name", "홍길동");
            ReflectionTestUtils.setField(u, "phoneNumber", "01012345678");
            ReflectionTestUtils.setField(u, "birthDate", LocalDate.of(1995, 1, 1));
            ReflectionTestUtils.setField(u, "identityVerified", identityVerified);
            return u;
        } catch (Exception e) {
            throw new IllegalStateException("User 생성 실패", e);
        }
    }

    private IdentityVerification createVerification(Long id, User owner, VerificationStatus status) {
        IdentityVerification v = IdentityVerification.startOcr(owner);
        ReflectionTestUtils.setField(v, "id", id);
        ReflectionTestUtils.setField(v, "status", status);
        return v;
    }
}
