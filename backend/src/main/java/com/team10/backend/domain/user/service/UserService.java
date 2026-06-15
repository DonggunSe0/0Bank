package com.team10.backend.domain.user.service;

import com.team10.backend.domain.user.client.PortOneClient;
import com.team10.backend.domain.user.client.PortOneIdentityVerification;
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
import com.team10.backend.global.exception.BusinessException;
import com.team10.backend.global.jwt.JwtProvider;
import com.team10.backend.global.jwt.RefreshTokenService;
import com.team10.backend.global.jwt.TokenBlocklistService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
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

        if (!verification.isVerified() || verification.verifiedCustomer() == null) {
            throw new BusinessException(UserErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        PortOneIdentityVerification.VerifiedCustomer customer = verification.verifiedCustomer();

        // 이름 일치 확인
        if (!request.name().equals(customer.name())) {
            throw new BusinessException(UserErrorCode.IDENTITY_VERIFICATION_NAME_MISMATCH);
        }

        // 생년월일 일치 확인 (데이터 조작 방지)
        String verifiedBirthDate = customer.birthDate();
        if (verifiedBirthDate == null || !request.birthDate().toString().equals(verifiedBirthDate)) {
            throw new BusinessException(UserErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        // 휴대폰 번호 일치 확인 (하이픈 제거 후 비교)
        String verifiedPhone = customer.phoneNumber();
        if (verifiedPhone == null ||
                !request.phoneNumber().replaceAll("\\D", "").equals(verifiedPhone.replaceAll("\\D", ""))) {
            throw new BusinessException(UserErrorCode.IDENTITY_VERIFICATION_FAILED);
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

    /**
     * 로그인: 이메일/비밀번호 검증 후 Access Token + Refresh Token을 발급한다.
     * 휴면/탈퇴 계정은 로그인 불가.
     */
    public LoginRes login(LoginReq request) {
        loginAttemptService.checkAndThrowIfLocked(request.email());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    loginAttemptService.recordFailure(request.email());
                    return new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
                });

        // 계정 상태 검증 — 비밀번호 검증 전에 수행하여 휴면/탈퇴 계정에 실패 카운터가 쌓이지 않도록 함
        switch (user.getStatus()) {
            case DORMANT   -> throw new BusinessException(UserErrorCode.DORMANT_ACCOUNT);
            case WITHDRAWN -> throw new BusinessException(UserErrorCode.WITHDRAWN_ACCOUNT);
            default        -> { /* ACTIVE — 정상 진행 */ }
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            loginAttemptService.recordFailure(request.email());
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }

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

        String newAccessToken  = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = refreshTokenService.issue(user.getId());

        return new TokenRefreshRes(newAccessToken, newRefreshToken);
    }

    /**
     * 비밀번호 변경: 현재 비밀번호 검증 후 새 비밀번호로 변경하고 기존 RT를 무효화한다.
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
     * 로그아웃: Redis의 Refresh Token을 삭제하고, AT를 블랙리스트에 등록한다.
     */
    public void logout(Long userId, String accessToken) {
        refreshTokenService.delete(userId);

        try {
            String jti = jwtProvider.extractJti(accessToken);
            long remainingSeconds = jwtProvider.getRemainingExpirySeconds(accessToken);
            tokenBlocklistService.block(jti, remainingSeconds);
        } catch (Exception e) {
            // AT 파싱 실패해도 RT는 이미 삭제됐으므로 로그아웃 자체는 성공 처리
            log.warn("[Logout] AT 블랙리스트 등록 실패 (무시) — userId={}, error={}", userId, e.getMessage());
        }
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
}
