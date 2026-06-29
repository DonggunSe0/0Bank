package com.team10.backend.domain.user.service;

import com.team10.backend.domain.user.dto.req.OneWonStartReq;
import com.team10.backend.domain.user.dto.req.OneWonVerifyReq;
import com.team10.backend.domain.user.dto.res.IdentityVerificationStatusRes;
import com.team10.backend.domain.user.dto.res.OcrAcceptedRes;
import com.team10.backend.domain.user.dto.res.OneWonStartRes;
import com.team10.backend.domain.user.dto.res.OneWonVerifyRes;
import com.team10.backend.domain.user.entity.IdentityVerification;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.event.OcrSubmittedEvent;
import com.team10.backend.domain.user.event.OneWonTransferRequestedEvent;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.ocr.OcrService;
import com.team10.backend.domain.user.repository.IdentityVerificationRepository;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.domain.user.type.VerificationStatus;
import com.team10.backend.domain.user.verification.OneWonVerificationService;
import com.team10.backend.domain.user.verification.OneWonVerificationService.VerifyResult;
import com.team10.backend.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

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
    @Mock OneWonVerificationService oneWonVerificationService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock PlatformTransactionManager txManager;
    @Mock StringRedisTemplate redisTemplate;
    @Mock RedisScript<Long> incrWithExpireIfNewScript;

    @InjectMocks
    IdentityVerificationService service;

    private User user;
    private Path createdTempPath;

    @BeforeEach
    void setUp() {
        user = createUser(1L, false);
    }

    @AfterEach
    void cleanUpTempFile() throws IOException {
        if (createdTempPath != null) {
            Files.deleteIfExists(createdTempPath);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // submitIdCardOcr
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("submitIdCardOcr")
    class SubmitIdCardOcr {

        @Test
        @DisplayName("정상 접수 — OCR_PENDING 상태와 세션 ID 반환, OcrSubmittedEvent 발행, 락은 비동기 처리에 넘겨 유지된다")
        void success() {
            MockMultipartFile image = jpegFile(1024);

            when(ocrService.tryAcquireLock(1L)).thenReturn(true);
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                    .thenReturn(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(identityVerificationRepository.save(any())).thenAnswer(inv -> {
                IdentityVerification v = inv.getArgument(0);
                ReflectionTestUtils.setField(v, "id", 10L);
                return v;
            });

            OcrAcceptedRes res = service.submitIdCardOcr(1L, image);

            assertThat(res.verificationId()).isEqualTo(10L);
            assertThat(res.status()).isEqualTo(VerificationStatus.OCR_PENDING);

            ArgumentCaptor<OcrSubmittedEvent> eventCaptor = ArgumentCaptor.forClass(OcrSubmittedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            OcrSubmittedEvent event = eventCaptor.getValue();
            assertThat(event.verificationId()).isEqualTo(10L);
            assertThat(event.userId()).isEqualTo(1L);
            assertThat(event.tempImagePath()).exists();

            // 실제 OCR 처리는 비동기로 이뤄지므로, 중복 방지 락은 여기서 해제하지 않고 비동기 처리 쪽(OcrService.processAsync)에 넘긴다
            verify(ocrService, never()).releaseLock(1L);

            // 어설션 실패 여부와 무관하게 항상 정리되도록 outer cleanUpTempFile()에 위임
            createdTempPath = event.tempImagePath();
        }

        @Test
        @DisplayName("이미 처리 중(락 획득 실패) → OCR_REQUEST_IN_PROGRESS")
        void lockAlreadyHeld() {
            MockMultipartFile image = jpegFile(1024);

            when(ocrService.tryAcquireLock(1L)).thenReturn(false);

            assertThatThrownBy(() -> service.submitIdCardOcr(1L, image))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_REQUEST_IN_PROGRESS);

            // 락을 아예 얻지 못했으므로 해제도 시도하지 않는다 — 그 외 어떤 처리도 진행되지 않아야 한다
            verify(ocrService, never()).releaseLock(any());
            verifyNoInteractions(userRepository, identityVerificationRepository, eventPublisher);
        }

        @Test
        @DisplayName("이미지 없음 → OCR_IMAGE_REQUIRED, 락 해제")
        void noImage() {
            MockMultipartFile empty = new MockMultipartFile("image", new byte[0]);

            when(ocrService.tryAcquireLock(1L)).thenReturn(true);

            assertThatThrownBy(() -> service.submitIdCardOcr(1L, empty))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_IMAGE_REQUIRED);

            verify(ocrService).releaseLock(1L);
        }

        @Test
        @DisplayName("10MB 초과 → OCR_IMAGE_TOO_LARGE, 락 해제")
        void tooLarge() {
            MockMultipartFile large = new MockMultipartFile(
                    "image", "test.jpg", "image/jpeg", new byte[11 * 1024 * 1024]);

            when(ocrService.tryAcquireLock(1L)).thenReturn(true);

            assertThatThrownBy(() -> service.submitIdCardOcr(1L, large))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_IMAGE_TOO_LARGE);

            verify(ocrService).releaseLock(1L);
        }

        @Test
        @DisplayName("지원하지 않는 이미지 타입 → OCR_IMAGE_INVALID_TYPE, 락 해제")
        void invalidType() {
            MockMultipartFile gif = new MockMultipartFile(
                    "image", "test.gif", "image/gif", new byte[1024]);

            when(ocrService.tryAcquireLock(1L)).thenReturn(true);

            assertThatThrownBy(() -> service.submitIdCardOcr(1L, gif))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_IMAGE_INVALID_TYPE);

            verify(ocrService).releaseLock(1L);
        }

        @Test
        @DisplayName("일일 한도(5회) 초과 → OCR_DAILY_LIMIT_EXCEEDED, 락 해제")
        void dailyLimitExceeded() {
            MockMultipartFile image = jpegFile(1024);

            when(ocrService.tryAcquireLock(1L)).thenReturn(true);
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                    .thenReturn(6L);

            assertThatThrownBy(() -> service.submitIdCardOcr(1L, image))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.OCR_DAILY_LIMIT_EXCEEDED);

            verify(ocrService).releaseLock(1L);
        }

        @Test
        @DisplayName("사용자 없음 → USER_NOT_FOUND, 락 해제")
        void userNotFound() {
            MockMultipartFile image = jpegFile(1024);

            when(ocrService.tryAcquireLock(1L)).thenReturn(true);
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                    .thenReturn(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submitIdCardOcr(1L, image))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.USER_NOT_FOUND);

            verify(ocrService).releaseLock(1L);
        }

        @Test
        @DisplayName("이미 본인인증 완료 → IDENTITY_ALREADY_VERIFIED, 락 해제")
        void alreadyVerified() {
            MockMultipartFile image = jpegFile(1024);
            User alreadyVerified = createUser(1L, true);

            when(ocrService.tryAcquireLock(1L)).thenReturn(true);
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                    .thenReturn(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(alreadyVerified));

            assertThatThrownBy(() -> service.submitIdCardOcr(1L, image))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.IDENTITY_ALREADY_VERIFIED);

            verify(ocrService).releaseLock(1L);
        }

        @Test
        @DisplayName("본인인증 후 30일이 지나 만료 → 재인증(OCR 재접수) 허용")
        void expiredVerification_allowsResubmission() {
            MockMultipartFile image = jpegFile(1024);
            User expiredUser = createUser(1L, true);
            ReflectionTestUtils.setField(expiredUser, "identityVerifiedAt", LocalDateTime.now().minusDays(31));

            when(ocrService.tryAcquireLock(1L)).thenReturn(true);
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                    .thenReturn(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(expiredUser));
            when(identityVerificationRepository.save(any())).thenAnswer(inv -> {
                IdentityVerification v = inv.getArgument(0);
                ReflectionTestUtils.setField(v, "id", 11L);
                return v;
            });

            OcrAcceptedRes res = service.submitIdCardOcr(1L, image);

            assertThat(res.status()).isEqualTo(VerificationStatus.OCR_PENDING);
            verify(ocrService, never()).releaseLock(1L);

            ArgumentCaptor<OcrSubmittedEvent> eventCaptor = ArgumentCaptor.forClass(OcrSubmittedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            createdTempPath = eventCaptor.getValue().tempImagePath();
        }

    }

    // ─────────────────────────────────────────────────────────────────────────
    // startOneWonVerification
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("startOneWonVerification")
    class StartOneWonVerification {

        @Test
        @DisplayName("정상 접수 — ONE_WON_IN_PROGRESS 반환, 이벤트 발행, 락은 비동기 처리에 넘겨 유지된다")
        void success() {
            IdentityVerification verification =
                    createVerification(10L, user, VerificationStatus.GOVERNMENT_VERIFIED);
            OneWonStartReq req = new OneWonStartReq("12345678901", "090"); // 카카오뱅크 (점검 없음)

            when(oneWonVerificationService.tryAcquireStartLock(1L)).thenReturn(true);
            when(identityVerificationRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(verification));
            when(identityVerificationRepository.findById(10L)).thenReturn(Optional.of(verification));
            TransactionStatus txStatus = mock(TransactionStatus.class);
            when(txManager.getTransaction(any())).thenReturn(txStatus);

            OneWonStartRes res = service.startOneWonVerification(1L, req);

            assertThat(res.status()).isEqualTo(VerificationStatus.ONE_WON_IN_PROGRESS);

            ArgumentCaptor<OneWonTransferRequestedEvent> eventCaptor =
                    ArgumentCaptor.forClass(OneWonTransferRequestedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            OneWonTransferRequestedEvent event = eventCaptor.getValue();
            assertThat(event.verificationId()).isEqualTo(10L);
            assertThat(event.userId()).isEqualTo(1L);
            assertThat(event.organization()).isEqualTo("090");
            assertThat(event.accountNumber()).isEqualTo("12345678901");

            // 실제 송금은 비동기로 처리되므로, 중복 방지 락은 여기서 해제하지 않고 비동기 처리 쪽(OneWonTransferProcessor)에 넘긴다
            verify(oneWonVerificationService, never()).releaseStartLock(1L);
        }

        @Test
        @DisplayName("행안부 인증 미완료 상태 → VERIFICATION_NOT_READY_FOR_ONE_WON, 락 해제")
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

            verify(oneWonVerificationService).releaseStartLock(1L);
        }

        @Test
        @DisplayName("세션 없음 → VERIFICATION_NOT_READY_FOR_ONE_WON, 락 해제")
        void sessionNotFound() {
            OneWonStartReq req = new OneWonStartReq("12345678901", "004");

            when(oneWonVerificationService.tryAcquireStartLock(1L)).thenReturn(true);
            when(identityVerificationRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.startOneWonVerification(1L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.VERIFICATION_NOT_READY_FOR_ONE_WON);

            verify(oneWonVerificationService).releaseStartLock(1L);
        }

        @Test
        @DisplayName("지원하지 않는 기관코드 → UNSUPPORTED_BANK, 락 해제")
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

            verify(oneWonVerificationService).releaseStartLock(1L);
        }

        @Test
        @DisplayName("은행 점검 시간대 → BANK_MAINTENANCE, 락 해제")
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

            verify(oneWonVerificationService).releaseStartLock(1L);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getMyVerificationStatus
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyVerificationStatus")
    class GetMyVerificationStatus {

        @Test
        @DisplayName("세션 존재 → 상태와 실패 사유 반환")
        void found_returnsStatus() {
            IdentityVerification verification =
                    createVerification(10L, user, VerificationStatus.ONE_WON_IN_PROGRESS);

            when(identityVerificationRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.of(verification));

            IdentityVerificationStatusRes res = service.getMyVerificationStatus(1L);

            assertThat(res.verificationId()).isEqualTo(10L);
            assertThat(res.status()).isEqualTo(VerificationStatus.ONE_WON_IN_PROGRESS);
            assertThat(res.failureReason()).isNull();
        }

        @Test
        @DisplayName("세션 없음 → VERIFICATION_SESSION_NOT_FOUND")
        void notFound_throws() {
            when(identityVerificationRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMyVerificationStatus(1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(UserErrorCode.VERIFICATION_SESSION_NOT_FOUND);
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
        byte[] content = new byte[size];
        // 매직바이트 검증(hasValidImageSignature)을 통과하도록 JPEG 시그니처(FF D8 FF)를 선두에 채운다.
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;
        return new MockMultipartFile("image", "test.jpg", "image/jpeg", content);
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
            if (identityVerified) {
                // isIdentityVerificationValid()가 identityVerifiedAt도 함께 확인하므로 같이 세팅
                ReflectionTestUtils.setField(u, "identityVerifiedAt", LocalDateTime.now());
            }
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
