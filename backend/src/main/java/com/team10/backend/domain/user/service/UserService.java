package com.team10.backend.domain.user.service;

import com.team10.backend.domain.user.dto.req.LoginReq;
import com.team10.backend.domain.user.dto.req.OneWonStartReq;
import com.team10.backend.domain.user.dto.req.OneWonVerifyReq;
import com.team10.backend.domain.user.dto.req.TokenRefreshReq;
import com.team10.backend.domain.user.dto.req.UserCreateReq;
import com.team10.backend.domain.user.dto.res.LoginRes;
import com.team10.backend.domain.user.dto.res.OcrAcceptedRes;
import com.team10.backend.domain.user.dto.res.OneWonStartRes;
import com.team10.backend.domain.user.dto.res.OneWonVerifyRes;
import com.team10.backend.domain.user.dto.res.TokenRefreshRes;
import com.team10.backend.domain.user.dto.res.UserRes;
import com.team10.backend.domain.user.entity.IdentityVerification;
import com.team10.backend.domain.user.entity.User;
import com.team10.backend.domain.user.exception.UserErrorCode;
import com.team10.backend.domain.user.ocr.OcrService;
import com.team10.backend.domain.user.repository.IdentityVerificationRepository;
import com.team10.backend.domain.user.repository.UserRepository;
import com.team10.backend.domain.user.type.VerificationStatus;
import com.team10.backend.domain.user.verification.MockBankTransferService;
import com.team10.backend.domain.user.verification.OneWonVerificationService;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.jwt.JwtProvider;
import com.team10.backend.global.jwt.RefreshTokenService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import java.io.IOException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024L; // 10 MB

    private final UserRepository userRepository;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final OcrService ocrService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final MockBankTransferService mockBankTransferService;
    private final OneWonVerificationService oneWonVerificationService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public UserRes signup(UserCreateReq request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.phoneNumber(),
                request.birthDate()
        );

        User saved = userRepository.save(user);
        return toUserRes(saved);
    }

    public UserRes getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        return toUserRes(user);
    }

    private UserRes toUserRes(User user) {
        return new UserRes(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhoneNumber(),
                user.getBirthDate(),
                user.getIdentityVerified(),
                user.getCreatedAt()
        );
    }

    /**
     * 로그인: 이메일/비밀번호 검증 후 Access Token + Refresh Token을 발급한다.
     */
    public LoginRes login(LoginReq request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken  = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = refreshTokenService.issue(user.getId());

        return new LoginRes(accessToken, refreshToken, toUserRes(user));
    }

    /**
     * Access Token 재발급 (Refresh Token Rotation).
     *
     * <p>만료된 AT에서 userId를 추출 → Redis의 RT와 비교 → 새 AT + 새 RT 발급
     */
    public TokenRefreshRes refresh(TokenRefreshReq request) {
        Long userId;
        try {
            userId = jwtProvider.parseUserIdIgnoreExpiry(request.accessToken());
        } catch (JwtException e) {
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (!refreshTokenService.validate(userId, request.refreshToken())) {
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        // Refresh Token Rotation: 기존 RT 폐기 후 새 RT 발급
        String newAccessToken  = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = refreshTokenService.issue(user.getId());

        return new TokenRefreshRes(newAccessToken, newRefreshToken);
    }

    /**
     * 로그아웃: Redis의 Refresh Token을 삭제한다.
     */
    public void logout(Long userId) {
        refreshTokenService.delete(userId);
    }

    /**
     * 신분증 OCR 1단계: 이미지를 접수하고 즉시 202 응답을 반환한다.
     *
     * <p>처리 흐름:
     * <ol>
     *   <li>이미지 유효성 검증 (존재 여부, 크기 제한)</li>
     *   <li>인증 세션 생성 ({@link VerificationStatus#OCR_PENDING})</li>
     *   <li>{@link OcrService#processAsync} 비동기 위임 — 메인 스레드 즉시 반환</li>
     * </ol>
     *
     * @param userId    요청 사용자 ID (TODO: 인증 도메인 연동 후 Principal로 교체)
     * @param imageFile 신분증 이미지 파일
     * @return 인증 세션 ID와 현재 상태를 담은 응답
     */
    @Transactional
    public OcrAcceptedRes submitIdCardOcr(Long userId, MultipartFile imageFile) {
        validateImage(imageFile);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (Boolean.TRUE.equals(user.getIdentityVerified())) {
            throw new BusinessException(UserErrorCode.IDENTITY_ALREADY_VERIFIED);
        }

        // 인증 세션 생성 및 저장
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

        // 트랜잭션 커밋 완료 후 async 실행
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
     * 3단계 1원 송금 인증 시작: 인증코드를 생성하고 사용자 계좌로 1원을 송금한다.
     *
     * @param userId  요청 사용자 ID
     * @param request 수신 계좌번호
     * @return 인증 세션 ID와 상태
     */
    @Transactional
    public OneWonStartRes startOneWonVerification(Long userId, OneWonStartReq request) {
        // GOVERNMENT_VERIFIED: 최초 요청
        // ONE_WON_PENDING: 코드를 못 받았거나 만료된 경우 재시도 허용
        IdentityVerification verification = identityVerificationRepository
                .findTopByUserIdOrderByCreatedAtDesc(userId)
                .filter(v -> v.getStatus() == VerificationStatus.GOVERNMENT_VERIFIED
                        || v.getStatus() == VerificationStatus.ONE_WON_PENDING)
                .orElseThrow(() -> new BusinessException(UserErrorCode.VERIFICATION_NOT_READY_FOR_ONE_WON));

        // 코드 생성 + Redis 저장 (TTL 10분)
        String code = oneWonVerificationService.generateAndStore(verification.getId());

        // Mock 송금
        mockBankTransferService.sendOneWon(request.accountNumber(), code);

        // 상태 전환
        verification.startOneWon();

        return new OneWonStartRes(
                verification.getId(),
                verification.getStatus(),
                "1원이 송금되었습니다. 입금 메모의 4자리 코드를 입력해주세요. (유효시간 10분)"
        );
    }

    /**
     * 3단계 코드 검증: 사용자가 입력한 코드를 Redis와 비교하고, 성공 시 인증을 완료한다.
     *
     * @param userId  요청 사용자 ID
     * @param request 사용자가 입력한 4자리 코드
     * @return 인증 완료 결과
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

        // 사용자 본인인증 완료 처리
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
