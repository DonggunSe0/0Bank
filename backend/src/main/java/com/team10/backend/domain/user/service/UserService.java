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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final PlatformTransactionManager txManager;

    // PortOne 외부 API는 트랜잭션 밖에서 호출, DB 저장만 별도 쓰기 트랜잭션
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserRes signup(UserCreateReq request) {
        PortOneIdentityVerification verification =
                portOneClient.getIdentityVerification(request.identityVerificationId());
        validateIdentityVerification(verification, request);

        return new TransactionTemplate(txManager).execute(status -> {
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
            User saved;
            try {
                saved = userRepository.save(user);
            } catch (DataIntegrityViolationException e) {
                // 동시 요청으로 existsByEmail 체크를 통과한 경우 DB unique 제약조건에서 잡힘
                throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
            }
            userConsentService.saveAll(
                    saved,
                    request.agreedServiceTerms(),
                    request.agreedPersonalInfo(),
                    request.agreedFinancialInfo(),
                    Boolean.TRUE.equals(request.agreedMarketing())
            );
            return toUserRes(saved);
        });
    }

    private void validateIdentityVerification(PortOneIdentityVerification verification,
                                               UserCreateReq request) {
        if (!verification.isVerified() || verification.verifiedCustomer() == null) {
            throw new BusinessException(UserErrorCode.IDENTITY_VERIFICATION_FAILED);
        }
        PortOneIdentityVerification.VerifiedCustomer customer = verification.verifiedCustomer();

        if (!request.name().equals(customer.name())) {
            throw new BusinessException(UserErrorCode.IDENTITY_VERIFICATION_NAME_MISMATCH);
        }
        String verifiedBirthDate = customer.birthDate();
        if (verifiedBirthDate == null || !request.birthDate().toString().equals(verifiedBirthDate)) {
            throw new BusinessException(UserErrorCode.IDENTITY_VERIFICATION_FAILED);
        }
        String verifiedPhone = customer.phoneNumber();
        if (verifiedPhone == null ||
                !request.phoneNumber().replaceAll("\\D", "").equals(verifiedPhone.replaceAll("\\D", ""))) {
            throw new BusinessException(UserErrorCode.IDENTITY_VERIFICATION_FAILED);
        }
    }

    public UserRes getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        return toUserRes(user);
    }

    public LoginRes login(LoginReq request) {
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

        // 비밀번호 검증 후에 상태 체크 — 계정 존재 여부 노출 방지
        switch (user.getStatus()) {
            case DORMANT   -> throw new BusinessException(UserErrorCode.DORMANT_ACCOUNT);
            case WITHDRAWN -> throw new BusinessException(UserErrorCode.WITHDRAWN_ACCOUNT);
            default        -> { /* ACTIVE — 정상 진행 */ }
        }

        loginAttemptService.clearFailures(request.email());

        String accessToken  = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = refreshTokenService.issue(user.getId());

        return new LoginRes(accessToken, refreshToken, toUserRes(user));
    }

    @Transactional
    public void withdraw(Long userId, String authHeader) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        user.withdraw();
        refreshTokenService.delete(userId);

        // AT 블랙리스트 등록 — 탈퇴 즉시 기존 토큰 무효화
        String accessToken = JwtProvider.resolveBearerToken(authHeader);
        if (accessToken != null) {
            try {
                String jti = jwtProvider.extractJti(accessToken);
                long remainingSeconds = jwtProvider.getRemainingExpirySeconds(accessToken);
                tokenBlocklistService.block(jti, remainingSeconds);
            } catch (Exception e) {
                log.warn("[Withdraw] AT 블랙리스트 등록 실패 (무시) — userId={}, error={}", userId, e.getMessage());
            }
        }
    }

    public TokenRefreshRes refresh(TokenRefreshReq request, String refreshToken) {
        Long userId;
        try {
            userId = jwtProvider.parseUserIdIgnoreExpiry(request.accessToken());
        } catch (JwtException e) {
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 원자적 get+delete — 동시 요청 중 하나만 성공 (RT Rotation race condition 방지)
        if (!refreshTokenService.validateAndConsume(userId, refreshToken)) {
            throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        String newAccessToken  = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = refreshTokenService.issue(user.getId());

        return new TokenRefreshRes(newAccessToken, newRefreshToken);
    }

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
