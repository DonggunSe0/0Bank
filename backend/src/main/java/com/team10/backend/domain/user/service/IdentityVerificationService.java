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
import com.team10.backend.domain.user.util.DailyResetClock;
import com.team10.backend.domain.user.verification.BankCode;
import com.team10.backend.domain.user.verification.OneWonVerificationService;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/** 신분증 본인인증 3단계(OCR → 행안부 → 1원 송금) 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IdentityVerificationService {

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024L; // 10 MB
    // Content-Type 헤더는 클라이언트가 임의로 지정 가능하므로, 실제 파일 시그니처(매직바이트)로 한 번 더 검증한다.
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final String OCR_DAILY_KEY_PREFIX = "identity:ocr:daily:";
    private static final int MAX_OCR_DAILY = 5; // 매일 00:00 KST 리셋

    private final UserRepository userRepository;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final OcrService ocrService;
    private final OneWonVerificationService oneWonVerificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager txManager;
    private final StringRedisTemplate redisTemplate;
    // RedisScriptConfig에서 공용으로 정의 — OneWonVerificationService의 daily 카운터와 동일한 스크립트
    private final RedisScript<Long> incrWithExpireIfNewScript;

    @Transactional
    public OcrAcceptedRes submitIdCardOcr(Long userId, MultipartFile imageFile) {
        validateImage(imageFile);
        checkOcrDailyLimit(userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (Boolean.TRUE.equals(user.getIdentityVerified())) {
            throw new BusinessException(UserErrorCode.IDENTITY_ALREADY_VERIFIED);
        }

        IdentityVerification verification = IdentityVerification.startOcr(user);
        IdentityVerification saved = identityVerificationRepository.save(verification);

        // 커밋 후(OcrSubmittedEventListener)에는 Tomcat이 멀티파트 임시파일을 이미 삭제하므로,
        // 앱이 직접 관리하는 임시파일로 복사해 비동기 처리가 끝날 때까지 보존한다.
        Path tempImagePath;
        try {
            tempImagePath = Files.createTempFile("ocr-", ".tmp");
            imageFile.transferTo(tempImagePath);
        } catch (IOException e) {
            throw new BusinessException(UserErrorCode.OCR_IMAGE_REQUIRED);
        }

        // afterCommit() 콜백 직접 등록 대신 이벤트 발행 — 실제 처리는 OcrSubmittedEventListener(AFTER_COMMIT)가 담당
        eventPublisher.publishEvent(new OcrSubmittedEvent(tempImagePath, saved.getId()));

        return new OcrAcceptedRes(
                saved.getId(),
                saved.getStatus(),
                "신분증 OCR 접수가 완료되었습니다. 처리 결과는 잠시 후 확인하실 수 있습니다."
        );
    }

    /** 1원 송금 요청 접수(비동기 처리, kickedOff 락 해제 위임). */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OneWonStartRes startOneWonVerification(Long userId, OneWonStartReq request) {
        // 동시 요청 방지 — 같은 유저가 거의 동시에 두 번 호출하면 실제 송금이 중복 실행될 수 있음
        if (!oneWonVerificationService.tryAcquireStartLock(userId)) {
            throw new BusinessException(UserErrorCode.ONE_WON_REQUEST_IN_PROGRESS);
        }

        boolean kickedOff = false;
        try {
            IdentityVerification verification = identityVerificationRepository
                    .findTopByUserIdOrderByCreatedAtDesc(userId)
                    .filter(v -> v.getStatus() == VerificationStatus.GOVERNMENT_VERIFIED
                            || v.getStatus() == VerificationStatus.ONE_WON_PENDING)
                    .orElseThrow(() -> new BusinessException(UserErrorCode.VERIFICATION_NOT_READY_FOR_ONE_WON));

            Long verificationId = verification.getId();

            validateBankAvailability(request.organization());

            OneWonStartRes response = new TransactionTemplate(txManager).execute(status -> {
                IdentityVerification managed = identityVerificationRepository
                        .findById(verificationId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.VERIFICATION_SESSION_NOT_FOUND));
                managed.requestOneWonTransfer();

                // afterCommit() 이후 OneWonTransferRequestedEventListener가 실제 송금을 비동기로 처리한다.
                eventPublisher.publishEvent(new OneWonTransferRequestedEvent(
                        verificationId, userId, request.organization(), request.accountNumber()
                ));

                return new OneWonStartRes(
                        managed.getId(),
                        managed.getStatus(),
                        "1원 송금 요청이 접수되었습니다. 처리 결과는 상태 조회 API로 확인해주세요."
                );
            });

            kickedOff = true;
            return response;
        } finally {
            // 비동기 처리에 정상적으로 넘긴 경우엔 락 해제를 OneWonTransferProcessor의 finally로 위임한다.
            if (!kickedOff) {
                oneWonVerificationService.releaseStartLock(userId);
            }
        }
    }

    /** 가장 최근 본인인증 세션의 진행 상태를 조회한다. OCR/1원송금이 비동기로 처리되므로 폴링용으로 사용한다. */
    public IdentityVerificationStatusRes getMyVerificationStatus(Long userId) {
        IdentityVerification verification = identityVerificationRepository
                .findTopByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.VERIFICATION_SESSION_NOT_FOUND));

        return new IdentityVerificationStatusRes(
                verification.getId(),
                verification.getStatus(),
                verification.getFailureReason()
        );
    }

    @Transactional
    public OneWonVerifyRes verifyOneWonCode(Long userId, OneWonVerifyReq request) {
        IdentityVerification verification = identityVerificationRepository
                .findTopByUserIdAndStatusOrderByCreatedAtDesc(userId, VerificationStatus.ONE_WON_PENDING)
                .orElseThrow(() -> new BusinessException(UserErrorCode.VERIFICATION_SESSION_NOT_FOUND));

        OneWonVerificationService.VerifyResult result =
                oneWonVerificationService.verify(verification.getId(), request.code());

        if (result == OneWonVerificationService.VerifyResult.EXPIRED) {
            throw new BusinessException(UserErrorCode.ONE_WON_CODE_EXPIRED);
        }
        if (result == OneWonVerificationService.VerifyResult.LOCKED) {
            throw new BusinessException(UserErrorCode.ONE_WON_ATTEMPT_EXCEEDED);
        }
        if (result == OneWonVerificationService.VerifyResult.MISMATCH) {
            throw new BusinessException(UserErrorCode.ONE_WON_CODE_MISMATCH);
        }

        verification.completeOneWon();

        User user = verification.getUser();
        user.completeIdentityVerification();

        return new OneWonVerifyRes(
                verification.getId(),
                verification.getStatus(),
                "본인인증이 완료되었습니다."
        );
    }

    private void validateBankAvailability(String organizationCode) {
        BankCode bank = BankCode.fromCode(organizationCode)
                .orElseThrow(() -> new BusinessException(UserErrorCode.UNSUPPORTED_BANK));

        LocalTime now = LocalTime.now(ZoneId.of("Asia/Seoul"));
        if (bank.isMaintenance(now)) {
            log.warn("[1원 인증] 은행 점검 시간 — bank={}, code={}, time={}", bank.getDisplayName(), organizationCode, now);
            throw new BusinessException(UserErrorCode.BANK_MAINTENANCE);
        }
    }

    private void checkOcrDailyLimit(Long userId) {
        String key = OCR_DAILY_KEY_PREFIX + userId;
        // 매번 호출 시점부터 다음 자정(KST)까지 남은 초를 TTL로 넘긴다 — 키가 그날 처음 만들어질 때만
        // EXPIRE가 적용되므로(incrWithExpireIfNewScript), 결과적으로 모든 사용자가 자정에 리셋된다.
        Long count = redisTemplate.execute(
                incrWithExpireIfNewScript,
                List.of(key),
                String.valueOf(DailyResetClock.secondsUntilNextMidnight())
        );
        if (count == null) {
            throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (count > MAX_OCR_DAILY) {
            log.warn("[OCR] 하루 요청 한도 초과 — userId={}, count={}", userId, count);
            throw new BusinessException(UserErrorCode.OCR_DAILY_LIMIT_EXCEEDED);
        }
        log.debug("[OCR] 일일 요청 카운트 — userId={}, count={}/{}", userId, count, MAX_OCR_DAILY);
    }

    private void validateImage(MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            throw new BusinessException(UserErrorCode.OCR_IMAGE_REQUIRED);
        }
        if (imageFile.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException(UserErrorCode.OCR_IMAGE_TOO_LARGE);
        }
        String contentType = imageFile.getContentType();
        if (contentType == null
                || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
            throw new BusinessException(UserErrorCode.OCR_IMAGE_INVALID_TYPE);
        }
        if (!hasValidImageSignature(imageFile)) {
            throw new BusinessException(UserErrorCode.OCR_IMAGE_INVALID_TYPE);
        }
    }

    /** Content-Type 헤더 조작 우회 방지 — 파일 앞부분의 실제 매직바이트가 JPEG/PNG 시그니처와 일치하는지 확인한다. */
    private boolean hasValidImageSignature(MultipartFile imageFile) {
        byte[] header = new byte[8];
        int read;
        try (InputStream in = imageFile.getInputStream()) {
            read = in.read(header);
        } catch (IOException e) {
            return false;
        }
        if (read >= JPEG_SIGNATURE.length && matchesSignature(header, JPEG_SIGNATURE)) {
            return true;
        }
        return read >= PNG_SIGNATURE.length && matchesSignature(header, PNG_SIGNATURE);
    }

    private boolean matchesSignature(byte[] header, byte[] signature) {
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
