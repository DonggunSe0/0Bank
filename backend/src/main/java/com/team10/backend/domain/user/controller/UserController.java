package com.team10.backend.domain.user.controller;

import com.team10.backend.domain.user.dto.req.OneWonStartReq;
import com.team10.backend.domain.user.dto.req.OneWonVerifyReq;
import com.team10.backend.domain.user.dto.res.OcrAcceptedRes;
import com.team10.backend.domain.user.dto.res.OneWonStartRes;
import com.team10.backend.domain.user.dto.res.OneWonVerifyRes;
import com.team10.backend.domain.user.dto.res.UserRes;
import com.team10.backend.domain.user.service.UserService;
import com.team10.backend.global.jwt.JwtProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "사용자 및 본인인증 API")
public class UserController {

    private final UserService userService;
    private final JwtProvider jwtProvider;

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회")
    public ResponseEntity<UserRes> getMe(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(userService.getMe(userId));
    }

    /**
     * 본인인증 1단계: 신분증 OCR 업로드.
     *
     * <p>이미지를 접수한 뒤 즉시 202 Accepted 를 반환한다.
     * 실제 OCR 처리는 {@code ocrExecutor} 스레드 풀에서 비동기로 수행된다.
     */
    @PostMapping(value = "/me/identity-verification/ocr",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "본인인증 1단계 — 신분증 OCR 업로드",
            description = "신분증(주민등록증/운전면허증) 이미지를 업로드합니다. " +
                    "요청 즉시 202 Accepted 를 반환하며, OCR은 백그라운드에서 처리됩니다."
    )
    public ResponseEntity<OcrAcceptedRes> uploadIdCardOcr(
            @RequestHeader("Authorization") String authHeader,

            @Parameter(description = "신분증 이미지 (jpg/png, 최대 10MB)")
            @RequestPart("idCardImage") MultipartFile idCardImage
    ) {
        Long userId = extractUserId(authHeader);
        OcrAcceptedRes response = userService.submitIdCardOcr(userId, idCardImage);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/me/identity-verification/one-won")
    @Operation(
            summary = "본인인증 3단계 — 1원 송금 요청",
            description = "지정 계좌로 1원을 송금합니다. 입금 메모의 4자리 코드로 /verify를 호출하세요."
    )
    public ResponseEntity<OneWonStartRes> startOneWonVerification(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody OneWonStartReq request
    ) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(userService.startOneWonVerification(userId, request));
    }

    @PostMapping("/me/identity-verification/one-won/verify")
    @Operation(
            summary = "본인인증 3단계 — 1원 인증코드 검증",
            description = "입금 메모에서 확인한 4자리 코드를 제출하여 본인인증을 완료합니다."
    )
    public ResponseEntity<OneWonVerifyRes> verifyOneWonCode(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody OneWonVerifyReq request
    ) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(userService.verifyOneWonCode(userId, request));
    }

    /** Authorization: Bearer {token} 에서 userId 추출 */
    private Long extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization 헤더가 없거나 형식이 올바르지 않습니다.");
        }
        return jwtProvider.parseUserId(authHeader.substring(7));
    }
}
