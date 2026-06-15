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
import com.team10.backend.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 신분증 본인인증 3단계(OCR → 행안부 → 1원 송금)를 담당하는 서비스.
 *
 * <h2>단계별 흐름</h2>
 * <pre>
 * 1단계 — submitIdCardOcr()       : 신분증 이미지 접수 → 비동기 OCR + 행안부 검증
 * 3단계 — startOneWonVerification(): 1원 송금 요청 → 인증코드 Redis 저장
 * 3단계 — verifyOneWonCode()       : 코드 검증 → 완료 시 identityVerified=true
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IdentityVerificationService {

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024L; // 10 MB

    private final UserRepository userRepository;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final OcrService ocrService;
    private final BankTransferService bankTransferService;
    private final OneWonVerificationService oneWonVerificationService;

    /**
     * 본인인증 1단계: 신분증 이미지를 접수하고 즉시 202를 반환한다.
     * OCR과 행안부 검증은 백그라운드에서 비동기 처리된다.
     */
    @Transactional
    public OcrAcceptedRes submitIdCardOcr(Long userId, MultipartFile imageFile) {
        validateImage(imageFile);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (Boolean.TRUE.equals(user.getIdentityVerified())) {
            throw new BusinessException(UserErrorCode.IDENTITY_ALREADY_VERIFIED);
        }

        IdentityVerification verification = IdentityVerification.startOcr(user);
        IdentityVerification saved = identityVerificationRepository.save(verification);

        // 메인 스레드에서 바이트를 미리 읽음
        // MultipartFile은 요청 종료 시 Tomcat이 임시파일을 삭제하므로
        // afterCommit() 시점엔 이미 파일이 사라진다
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
                ocrService.processAsync(imageBytes, verificationId);
            }
        });

        return new OcrAcceptedRes(
                saved.getId(),
                saved.getStatus(),
                "신분증 OCR 접수가 완료되었습니다. 처리 결과는 잠시 후 확인하실 수 있습니다."
        );
    }

    /**
     * 본인인증 3단계: 인증코드를 생성하고 사용자 계좌로 1원을 송금한다.
     */
    @Transactional
    public OneWonStartRes startOneWonVerification(Long userId, OneWonStartReq request) {
        IdentityVerification verification = identityVerificationRepository
                .findTopByUserIdOrderByCreatedAtDesc(userId)
                .filter(v -> v.getStatus() == VerificationStatus.GOVERNMENT_VERIFIED
                        || v.getStatus() == VerificationStatus.ONE_WON_PENDING)
                .orElseThrow(() -> new BusinessException(UserErrorCode.VERIFICATION_NOT_READY_FOR_ONE_WON));

        Long verificationId = verification.getId();
        String code = oneWonVerificationService.generateAndStore(verificationId, userId);

        // 송금 실패 시 Redis 코드 정리 — 재시도 시 daily 카운터가 이중 소모되지 않도록
        try {
            bankTransferService.sendOneWon(request.organization(), request.accountNumber(), code);
        } catch (Exception e) {
            oneWonVerificationService.deleteCode(verificationId);
            throw e;
        }

        verification.startOneWon();

        return new OneWonStartRes(
                verification.getId(),
                verification.getStatus(),
                "1원이 송금되었습니다. 입금 메모의 4자리 코드를 입력해주세요. (유효시간 10분)"
        );
    }

    /**
     * 본인인증 3단계 코드 검증: 성공 시 identityVerified=true로 인증을 완료한다.
     */
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
