package com.team10.backend.domain.user.controller;

import com.team10.backend.domain.user.dto.req.ChangePasswordReq;
import com.team10.backend.domain.user.dto.req.ConsentUpdateReq;
import com.team10.backend.domain.user.dto.req.OneWonStartReq;
import com.team10.backend.domain.user.dto.req.OneWonVerifyReq;
import com.team10.backend.domain.user.dto.res.ConsentRes;
import com.team10.backend.domain.user.dto.res.OcrAcceptedRes;
import com.team10.backend.domain.user.dto.res.OneWonStartRes;
import com.team10.backend.domain.user.dto.res.OneWonVerifyRes;
import com.team10.backend.domain.user.dto.res.UserRes;
import com.team10.backend.domain.user.service.IdentityVerificationService;
import com.team10.backend.domain.user.service.UserConsentService;
import com.team10.backend.domain.user.service.UserProfileService;
import com.team10.backend.domain.user.service.UserService;
import com.team10.backend.domain.user.dto.req.UserProfileReq;
import com.team10.backend.domain.user.dto.res.UserProfileRes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "사용자 및 본인인증 API")
public class UserController {

    private final UserService userService;
    private final IdentityVerificationService identityVerificationService;
    private final UserConsentService userConsentService;
    private final UserProfileService userProfileService;

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회")
    public ResponseEntity<UserRes> getMe(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(userService.getMe(userId));
    }

    @PatchMapping("/me/password")
    @Operation(
            summary = "비밀번호 변경",
            description = "현재 비밀번호 검증 후 변경합니다. 변경 즉시 기존 Refresh Token이 무효화되어 재로그인이 필요합니다."
    )
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChangePasswordReq request
    ) {
        userService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    @Operation(summary = "회원 탈퇴", description = "계정 상태를 WITHDRAWN으로 변경하고 Refresh Token을 삭제합니다.")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Long userId) {
        userService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }

    // ── 약관 동의 ────────────────────────────────────────────────────────────

    @GetMapping("/me/consents")
    @Operation(summary = "약관 동의 내역 조회")
    public ResponseEntity<List<ConsentRes>> getConsents(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(userConsentService.getConsents(userId));
    }

    @PatchMapping("/me/consents")
    @Operation(summary = "마케팅 수신 동의 변경", description = "선택 항목(마케팅)만 변경 가능합니다.")
    public ResponseEntity<ConsentRes> updateConsent(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ConsentUpdateReq request
    ) {
        return ResponseEntity.ok(userConsentService.updateMarketing(userId, request));
    }

    // ── 사용자 프로필 ─────────────────────────────────────────────────────────

    @PostMapping("/me/profile")
    @Operation(summary = "프로필 등록", description = "나이·지역·직업·관심 금융 분야를 등록합니다.")
    public ResponseEntity<UserProfileRes> createProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserProfileReq request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userProfileService.create(userId, request));
    }

    @GetMapping("/me/profile")
    @Operation(summary = "프로필 조회")
    public ResponseEntity<UserProfileRes> getProfile(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(userProfileService.get(userId));
    }

    @PatchMapping("/me/profile")
    @Operation(summary = "프로필 수정")
    public ResponseEntity<UserProfileRes> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserProfileReq request
    ) {
        return ResponseEntity.ok(userProfileService.update(userId, request));
    }

    // ── 본인인증 ──────────────────────────────────────────────────────────────

    @PostMapping(value = "/me/identity-verification/ocr",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "본인인증 1단계 — 신분증 OCR 업로드",
            description = "신분증(주민등록증/운전면허증) 이미지를 업로드합니다. " +
                    "요청 즉시 202 Accepted 를 반환하며, OCR은 백그라운드에서 처리됩니다."
    )
    public ResponseEntity<OcrAcceptedRes> uploadIdCardOcr(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "신분증 이미지 (jpg/png, 최대 10MB)")
            @RequestPart("idCardImage") MultipartFile idCardImage
    ) {
        OcrAcceptedRes response = identityVerificationService.submitIdCardOcr(userId, idCardImage);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/me/identity-verification/one-won")
    @Operation(
            summary = "본인인증 3단계 — 1원 송금 요청",
            description = "지정 계좌로 1원을 송금합니다. 입금 메모의 4자리 코드로 /verify를 호출하세요."
    )
    public ResponseEntity<OneWonStartRes> startOneWonVerification(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody OneWonStartReq request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(identityVerificationService.startOneWonVerification(userId, request));
    }

    @PostMapping("/me/identity-verification/one-won/verify")
    @Operation(
            summary = "본인인증 3단계 — 1원 인증코드 검증",
            description = "입금 메모에서 확인한 4자리 코드를 제출하여 본인인증을 완료합니다."
    )
    public ResponseEntity<OneWonVerifyRes> verifyOneWonCode(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody OneWonVerifyReq request
    ) {
        return ResponseEntity.ok(identityVerificationService.verifyOneWonCode(userId, request));
    }
}
