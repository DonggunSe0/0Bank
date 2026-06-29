package com.team10.backend.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.team10.backend.domain.user.dto.req.ChangePasswordReq;
import com.team10.backend.domain.user.dto.req.ConsentUpdateReq;
import com.team10.backend.domain.user.dto.req.OneWonStartReq;
import com.team10.backend.domain.user.dto.req.OneWonVerifyReq;
import com.team10.backend.domain.user.dto.req.UserProfileReq;
import com.team10.backend.domain.user.dto.res.ConsentRes;
import com.team10.backend.domain.user.dto.res.OcrAcceptedRes;
import com.team10.backend.domain.user.dto.res.OneWonStartRes;
import com.team10.backend.domain.user.dto.res.OneWonVerifyRes;
import com.team10.backend.domain.user.dto.res.UserProfileRes;
import com.team10.backend.domain.user.dto.res.UserRes;
import com.team10.backend.domain.user.service.IdentityVerificationService;
import com.team10.backend.domain.user.service.UserConsentService;
import com.team10.backend.domain.user.service.UserProfileService;
import com.team10.backend.domain.user.service.UserService;
import com.team10.backend.domain.user.type.AgeGroup;
import com.team10.backend.domain.user.type.FinancialInterest;
import com.team10.backend.domain.user.type.OccupationStatus;
import com.team10.backend.domain.user.type.Region;
import com.team10.backend.domain.user.type.TermsType;
import com.team10.backend.domain.user.type.VerificationStatus;
import com.team10.backend.global.jwt.JwtProvider;
import com.team10.backend.global.jwt.TokenBlocklistService;
import com.team10.backend.global.security.JwtAccessDeniedHandler;
import com.team10.backend.global.security.JwtAuthenticationEntryPoint;
import com.team10.backend.global.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class UserControllerTest {

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @MockitoBean UserService userService;
    @MockitoBean IdentityVerificationService identityVerificationService;
    @MockitoBean UserConsentService userConsentService;
    @MockitoBean UserProfileService userProfileService;

    // SecurityConfig 의존 Bean
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean TokenBlocklistService tokenBlocklistService;

    /**
     * "fake-token"으로 userId=1L 인증이 통과하도록 JwtProvider/TokenBlocklistService 스텁.
     * @WebMvcTest 슬라이스는 STATELESS 세션이라 SecurityContext를 세션에서 읽지 않으므로
     * JwtAuthenticationFilter가 실제로 실행되도록 Bearer 헤더를 주입하는 방식을 사용한다.
     */
    @BeforeEach
    void setUpJwtAuth() {
        // @WebMvcTest 슬라이스는 Security filter chain을 자동 적용하지 않으므로 직접 빌드
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity())
                .build();
        when(jwtProvider.parseTokenClaims(any(), anyBoolean()))
                .thenReturn(new JwtProvider.TokenClaims(1L, "test-jti"));
        when(tokenBlocklistService.isBlocked(any())).thenReturn(false);
    }

    /** Authorization: Bearer fake-token 헤더를 추가하는 RequestPostProcessor */
    private static RequestPostProcessor auth() {
        return request -> {
            request.addHeader("Authorization", "Bearer fake-token");
            return request;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/users/me
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /me")
    class GetMe {

        @Test
        @DisplayName("정상 조회 — 200 + UserRes")
        void success() throws Exception {
            UserRes res = new UserRes(1L, "test@test.com", "홍길동", "01012345678",
                    LocalDate.of(1990, 1, 1), false, LocalDateTime.now());

            when(userService.getMe(1L)).thenReturn(res);

            mockMvc.perform(get("/api/v1/users/me").with(auth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1L))
                    .andExpect(jsonPath("$.email").value("test@test.com"))
                    .andExpect(jsonPath("$.name").value("홍길동"));
        }

        @Test
        @DisplayName("미인증 요청 — 401")
        void unauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PATCH /api/v1/users/me/password
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /me/password")
    class ChangePassword {

        @Test
        @DisplayName("정상 변경 — 204")
        void success() throws Exception {
            ChangePasswordReq req = new ChangePasswordReq("OldPass1!", "NewPass1!");
            doNothing().when(userService).changePassword(eq(1L), any(), any());

            mockMvc.perform(patch("/api/v1/users/me/password")
                            .with(auth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNoContent());

            verify(userService).changePassword(eq(1L), any(), any());
        }

        @Test
        @DisplayName("새 비밀번호 형식 오류 — 400")
        void invalidPassword() throws Exception {
            ChangePasswordReq req = new ChangePasswordReq("OldPass1!", "short");

            mockMvc.perform(patch("/api/v1/users/me/password")
                            .with(auth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/users/me
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /me")
    class Withdraw {

        @Test
        @DisplayName("정상 탈퇴 — 204")
        void success() throws Exception {
            doNothing().when(userService).withdraw(eq(1L), any());

            mockMvc.perform(delete("/api/v1/users/me").with(auth()))
                    .andExpect(status().isNoContent());

            verify(userService).withdraw(eq(1L), any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/users/me/consents
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /me/consents")
    class GetConsents {

        @Test
        @DisplayName("약관 동의 목록 조회 — 200")
        void success() throws Exception {
            ConsentRes res = new ConsentRes(TermsType.SERVICE_TERMS, true, LocalDateTime.now());
            when(userConsentService.getConsents(1L)).thenReturn(List.of(res));

            mockMvc.perform(get("/api/v1/users/me/consents").with(auth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].termsType").value("SERVICE_TERMS"))
                    .andExpect(jsonPath("$[0].agreed").value(true));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PATCH /api/v1/users/me/consents
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /me/consents")
    class UpdateConsent {

        @Test
        @DisplayName("마케팅 동의 변경 — 200")
        void success() throws Exception {
            ConsentUpdateReq req = new ConsentUpdateReq(true);
            ConsentRes res = new ConsentRes(TermsType.MARKETING, true, LocalDateTime.now());
            when(userConsentService.updateMarketing(eq(1L), any())).thenReturn(res);

            mockMvc.perform(patch("/api/v1/users/me/consents")
                            .with(auth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.termsType").value("MARKETING"))
                    .andExpect(jsonPath("$.agreed").value(true));
        }

        @Test
        @DisplayName("동의 여부 누락 — 400")
        void missingField() throws Exception {
            mockMvc.perform(patch("/api/v1/users/me/consents")
                            .with(auth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/users/me/profile
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /me/profile")
    class CreateProfile {

        @Test
        @DisplayName("프로필 등록 — 201")
        void success() throws Exception {
            UserProfileReq req = new UserProfileReq(
                    AgeGroup.TWENTIES, Region.SEOUL, OccupationStatus.EMPLOYED,
                    Set.of(FinancialInterest.SAVINGS));
            UserProfileRes res = new UserProfileRes(1L, AgeGroup.TWENTIES, Region.SEOUL,
                    OccupationStatus.EMPLOYED, Set.of(FinancialInterest.SAVINGS));
            when(userProfileService.create(eq(1L), any())).thenReturn(res);

            mockMvc.perform(post("/api/v1/users/me/profile")
                            .with(auth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.userId").value(1L))
                    .andExpect(jsonPath("$.ageGroup").value("TWENTIES"))
                    .andExpect(jsonPath("$.region").value("SEOUL"));
        }

        @Test
        @DisplayName("필수 필드(ageGroup) 누락 — 400")
        void missingRequiredField() throws Exception {
            UserProfileReq req = new UserProfileReq(
                    null, Region.SEOUL, OccupationStatus.EMPLOYED,
                    Set.of(FinancialInterest.SAVINGS));

            mockMvc.perform(post("/api/v1/users/me/profile")
                            .with(auth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(userProfileService);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/users/me/identity-verification/ocr
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /me/identity-verification/ocr")
    class UploadOcr {

        @Test
        @DisplayName("이미지 업로드 — 202 + OcrAcceptedRes")
        void success() throws Exception {
            MockMultipartFile image = new MockMultipartFile(
                    "idCardImage", "id.jpg", "image/jpeg", new byte[1024]);
            OcrAcceptedRes res = new OcrAcceptedRes(10L, VerificationStatus.OCR_PENDING, "접수 완료");
            when(identityVerificationService.submitIdCardOcr(eq(1L), any())).thenReturn(res);

            mockMvc.perform(multipart("/api/v1/users/me/identity-verification/ocr")
                            .file(image)
                            .with(auth()))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.verificationId").value(10L))
                    .andExpect(jsonPath("$.status").value("OCR_PENDING"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/users/me/identity-verification/one-won
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /me/identity-verification/one-won")
    class StartOneWon {

        @Test
        @DisplayName("1원 송금 요청 — 202 + OneWonStartRes")
        void success() throws Exception {
            OneWonStartReq req = new OneWonStartReq("12345678901", "004");
            OneWonStartRes res = new OneWonStartRes(10L, VerificationStatus.ONE_WON_PENDING, "1원 송금 완료");
            when(identityVerificationService.startOneWonVerification(eq(1L), any())).thenReturn(res);

            mockMvc.perform(post("/api/v1/users/me/identity-verification/one-won")
                            .with(auth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("ONE_WON_PENDING"));
        }

        @Test
        @DisplayName("계좌번호 형식 오류 — 400")
        void invalidAccountNumber() throws Exception {
            OneWonStartReq req = new OneWonStartReq("123", "004"); // 10자리 미만

            mockMvc.perform(post("/api/v1/users/me/identity-verification/one-won")
                            .with(auth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/users/me/identity-verification/one-won/verify
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /me/identity-verification/one-won/verify")
    class VerifyOneWon {

        @Test
        @DisplayName("코드 검증 — 200 + OneWonVerifyRes")
        void success() throws Exception {
            OneWonVerifyReq req = new OneWonVerifyReq("1234");
            OneWonVerifyRes res = new OneWonVerifyRes(10L, VerificationStatus.COMPLETED, "인증 완료");
            when(identityVerificationService.verifyOneWonCode(eq(1L), any())).thenReturn(res);

            mockMvc.perform(post("/api/v1/users/me/identity-verification/one-won/verify")
                            .with(auth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("코드 형식 오류 (4자리 아님) — 400")
        void invalidCode() throws Exception {
            OneWonVerifyReq req = new OneWonVerifyReq("12"); // 2자리

            mockMvc.perform(post("/api/v1/users/me/identity-verification/one-won/verify")
                            .with(auth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }
}
