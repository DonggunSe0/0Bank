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
import com.team10.backend.domain.user.verification.BankTransferService;
import com.team10.backend.domain.user.verification.OneWonVerificationService;
import com.team10.backend.domain.user.dto.req.ChangePasswordReq;
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.jwt.JwtProvider;
import com.team10.backend.global.jwt.RefreshTokenService;
import com.team10.backend.global.jwt.TokenBlocklistService;
import com.team10.backend.global.portone.PortOneClient;
import com.team10.backend.global.portone.PortOneIdentityVerification;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024L; // 10 MB

    private final UserRepository userRepository;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final OcrService ocrService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final BankTransferService bankTransferService;
    private final OneWonVerificationService oneWonVerificationService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserConsentService userConsentService;
    private final LoginAttemptService loginAttemptService;
    private final TokenBlocklistService tokenBlocklistService;
    private final PortOneClient portOneClient;

    @Transactional
    public UserRes signup(UserCreateReq request) {
        // 포트원 본인인증 검증 — 가입 전 신원 확인
        PortOneIdentityVerification verification =
                portOneClient.getIdentityVerification(request.identityVerificationId());

        if (!verification.isVerified()) {
            throw new BusinessException(UserErrorCode.IDENTITY_VERIFICATION_FAILED);
        }
        if (!request.name().equals(verification.verifiedCustomer().name())) {
            throw new BusinessException(UserErrorCode.IDENTITY_VERIFICATION_NAME_MISMATCH);
        }

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

        // 약관 동의 내역 저장
        userConsentService.saveAll(
                saved,
                request.agreedServiceTerms(),
                request.agreedPersonalInfo(),
                request.agreedFinancialInfo(),
                Boolean.TRUE.equals(request.agreedMarketing())
        );

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
     * 휴면/탈퇴 계정은 로그인 불가.
     */
    public LoginRes login(LoginReq request) {
        // 잠금 상태 확인 (5회 이상 실패 시 30분 차단)
        loginAttemptService.checkAndThrowIfLocked(request.email());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    loginAttemptService.recordFailure(request.email());
                    return new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
                });

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            loginAttemptService.recordFailure(request.email());
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }

        // 계정 상태 검증
        switch (user.getStatus()) {
            case DORMANT   -> throw new BusinessException(UserErrorCode.DORMANT_ACCOUNT);
            case WITHDRAWN -> throw new BusinessException(UserErrorCode.WITHDRAWN_ACCOUNT);
            default        -> { /* ACTIVE — 정상 진행 */ }
        }

        // 로그인 성공 시 실패 카운터 초기화
        loginAttemptService.clearFailures(request.email());

        String accessToken  = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = refreshTokenService.issue(user.getId());

        return new LoginRes(accessToken, refreshToken, toUserRes(user));
    }

    /**
     * 회원 탈퇴: 계정 상태를 WITHDRAWN으로 변경하고 Redis RT를 삭제한다.
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        user.withdraw();
        refreshTokenService.delete(userId);
    }

    /**
     * Access Token 재발급 (Refresh Token Rotation).
     *
     * <p>Refresh Token은 HttpOnly 쿠키에서 읽어 컨트롤러가 전달한다.
     * 만료된 AT에서 userId를 추출 → Redis의 RT와 비교 → 새 AT + 새 RT 발급
     *
     * @param request      만료된 Access Token을 담은 요청 DTO
     * @param refreshToken HttpOnly 쿠키에서 읽은 Refresh Token
     */
    public TokenRefreshRes refresh(TokenRefreshReq request, String refreshToken) {
        Long userId;
        try {
            userId = jwtProvider.parseUserIdIgnoreExpiry(request.accessToken());
        } catch (JwtException e) {
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        // validateAndConsume: Redis에서 토큰을 원자적으로 가져오면서 즉시 삭제
        // 동시 요청이 들어와도 하나만 성공 (RT Rotation race condition 방지)
        if (!refreshTokenService.validateAndConsume(userId, refreshToken)) {
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
     * 비밀번호 변경: 현재 비밀번호 검증 후 새 비밀번호로 변경하고 기존 RT를 무효화한다.
     *
     * <p>계정이 탈취된 경우를 대비해 비밀번호 변경 즉시 기존 Refresh Token을 삭제한다.
     * 변경 후 사용자는 재로그인이 필요하다.
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordReq request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException(UserErrorCode.INVALID_CURRENT_PASSWORD);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));

        // 비밀번호 변경 즉시 기존 RT 무효화 — 탈취된 세션 강제 종료
        refreshTokenService.delete(userId);
    }

    /**
     * 로그아웃: Redis의 Refresh Token을 삭제하고, 해당 Access Token을 블랙리스트에 등록한다.
     *
     * @param userId      로그아웃 사용자 PK
     * @param accessToken 현재 사용 중인 AT — 잔여 만료 시간 동안 blocklist에 보관
     */
    public void logout(Long userId, String accessToken) {
        refreshTokenService.delete(userId);

        // 로그아웃 후에도 AT 만료 전까지 재사용되는 것을 방지
        try {
            String jti = jwtProvider.extractJti(accessToken);
            long remainingSeconds = jwtProvider.getRemainingExpirySeconds(accessToken);
            tokenBlocklistService.block(jti, remainingSeconds);
        } catch (Exception e) {
            // AT 파싱 실패해도 RT는 이미 삭제됐으므로 로그아웃 자체는 성공 처리
            log.warn("[Logout] AT 블랙리스트 등록 실패 (무시) — userId={}, error={}", userId, e.getMessage());
        }
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
     */
    @Transactional
    public OneWonStartRes startOneWonVerification(Long userId, OneWonStartReq request) {
        IdentityVerification verification = identityVerificationRepository
                .findTopByUserIdOrderByCreatedAtDesc(userId)
                .filter(v -> v.getStatus() == VerificationStatus.GOVERNMENT_VERIFIED
                        || v.getStatus() == VerificationStatus.ONE_WON_PENDING)
                .orElseThrow(() -> new BusinessException(UserErrorCode.VERIFICATION_NOT_READY_FOR_ONE_WON));

        // 코드 생성 + Redis 저장 (TTL 10분) — userId 기준 하루 10회 한도 적용
        String code = oneWonVerificationService.generateAndStore(verification.getId(), userId);

        // Mock 송금
        bankTransferService.sendOneWon(request.organization(), request.accountNumber(), code);

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
