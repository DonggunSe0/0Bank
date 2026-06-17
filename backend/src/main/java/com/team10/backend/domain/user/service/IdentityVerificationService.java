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
import com.team10.backend.domain.user.verification.BankCode;
import com.team10.backend.domain.user.verification.BankTransferService;
import com.team10.backend.domain.user.verification.OneWonVerificationService;
import com.team10.backend.domain.user.verification.VerificationSessionRecorder;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/** 신분증 본인인증 3단계(OCR → 행안부 → 1원 송금) 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IdentityVerificationService {

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024L; // 10 MB
    private static final String OCR_DAILY_KEY_PREFIX = "identity:ocr:daily:";
    private static final int MAX_OCR_DAILY = 5;
    private static final Duration DAILY_TTL = Duration.ofDays(1);

    // INCR 후 첫 생성 시에만 EXPIRE — TTL 덮어쓰기 방지
    private static final RedisScript<Long> INCR_WITH_EXPIRE_IF_NEW = RedisScript.of(
            "local v = redis.call('INCR', KEYS[1])\n" +
            "if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
            "return v",
            Long.class
    );

    private final UserRepository userRepository;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final OcrService ocrService;
    private final BankTransferService bankTransferService;
    private final OneWonVerificationService oneWonVerificationService;
    private final VerificationSessionRecorder verificationSessionRecorder;
    private final PlatformTransactionManager txManager;
    private final StringRedisTemplate redisTemplate;

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

        // afterCommit() 시점엔 Tomcat이 임시파일을 이미 삭제하므로 미리 바이트 읽기
        byte[] imageBytes;
        try {
            imageBytes = imageFile.getBytes();
        } catch (IOException e) {
            throw new BusinessException(UserErrorCode.OCR_IMAGE_REQUIRED);
        }

        Long verificationId = saved.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    ocrService.processAsync(imageBytes, verificationId);
                } catch (RejectedExecutionException e) {
                    // 스레드풀+큐 포화로 작업 자체가 시작 못 함 — OCR_PENDING에 영구히 멈추지 않도록 별도 트랜잭션으로 FAILED 기록
                    log.error("[OCR] 스레드풀 포화로 작업 거부 — verificationId={}", verificationId, e);
                    verificationSessionRecorder.markFailedInNewTransaction(
                            verificationId,
                            "서버 처리량이 많아 요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요."
                    );
                }
            }
        });

        return new OcrAcceptedRes(
                saved.getId(),
                saved.getStatus(),
                "신분증 OCR 접수가 완료되었습니다. 처리 결과는 잠시 후 확인하실 수 있습니다."
        );
    }

    // 외부 송금 API 포함 → 트랜잭션 밖에서 실행, DB 쓰기만 TransactionTemplate으로 분리
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OneWonStartRes startOneWonVerification(Long userId, OneWonStartReq request) {
        // 동시 요청 방지 — 같은 유저가 거의 동시에 두 번 호출하면 실제 송금이 중복 실행될 수 있음
        if (!oneWonVerificationService.tryAcquireStartLock(userId)) {
            throw new BusinessException(UserErrorCode.ONE_WON_REQUEST_IN_PROGRESS);
        }
        try {
            IdentityVerification verification = identityVerificationRepository
                    .findTopByUserIdOrderByCreatedAtDesc(userId)
                    .filter(v -> v.getStatus() == VerificationStatus.GOVERNMENT_VERIFIED
                            || v.getStatus() == VerificationStatus.ONE_WON_PENDING)
                    .orElseThrow(() -> new BusinessException(UserErrorCode.VERIFICATION_NOT_READY_FOR_ONE_WON));

            Long verificationId = verification.getId();

            validateBankAvailability(request.organization());

            String code = oneWonVerificationService.generateAndStore(verificationId, userId);
            try {
                bankTransferService.sendOneWon(request.organization(), request.accountNumber(), code);
            } catch (Exception e) {
                // 송금 실패 시 Redis 코드 + daily 카운터 보상 롤백
                oneWonVerificationService.deleteCode(verificationId);
                oneWonVerificationService.decrementDailyCount(userId);
                throw e;
            }

            return new TransactionTemplate(txManager).execute(status -> {
                IdentityVerification managed = identityVerificationRepository
                        .findById(verificationId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.VERIFICATION_SESSION_NOT_FOUND));
                managed.startOneWon();
                return new OneWonStartRes(
                        managed.getId(),
                        managed.getStatus(),
                        "1원이 송금되었습니다. 입금 메모의 4자리 코드를 입력해주세요. (유효시간 10분)"
                );
            });
        } finally {
            oneWonVerificationService.releaseStartLock(userId);
        }
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
        Long count = redisTemplate.execute(
                INCR_WITH_EXPIRE_IF_NEW,
                List.of(key),
                String.valueOf(DAILY_TTL.toSeconds())
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
    }
}
