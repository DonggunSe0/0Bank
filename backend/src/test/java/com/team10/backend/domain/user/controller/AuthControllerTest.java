package com.team10.backend.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.team10.backend.domain.user.dto.req.LoginReq;
import com.team10.backend.domain.user.dto.req.TokenRefreshReq;
import com.team10.backend.domain.user.dto.req.UserCreateReq;
import com.team10.backend.domain.user.dto.res.LoginRes;
import com.team10.backend.domain.user.dto.res.TokenRefreshRes;
import com.team10.backend.domain.user.dto.res.UserRes;
import com.team10.backend.domain.user.service.UserService;
import com.team10.backend.global.jwt.JwtProvider;
import com.team10.backend.global.jwt.TokenBlocklistService;
import com.team10.backend.global.security.JwtAccessDeniedHandler;
import com.team10.backend.global.security.JwtAuthenticationEntryPoint;
import com.team10.backend.global.security.SecurityConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "jwt.refresh-token-expiration-seconds=604800",
        "cookie.secure=false"
})
class AuthControllerTest {

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @MockitoBean UserService userService;

    // SecurityConfig 의존 Bean
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean TokenBlocklistService tokenBlocklistService;

    /**
     * "fake-token" Bearer 헤더로 userId=1L 인증이 통과하도록 스텁.
     * 로그아웃 엔드포인트는 JwtAuthenticationFilter가 isLogout=true로 파싱하므로
     * anyBoolean()으로 커버한다.
     */
    @BeforeEach
    void setUpJwtAuth() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity())
                .build();
        when(jwtProvider.parseTokenClaims(any(), anyBoolean()))
                .thenReturn(new JwtProvider.TokenClaims(1L, "test-jti"));
        when(tokenBlocklistService.isBlocked(any())).thenReturn(false);
    }

    private UserRes sampleUser() {
        return new UserRes(1L, "test@test.com", "홍길동", "01012345678",
                LocalDate.of(1990, 1, 1), false, LocalDateTime.now());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/auth/signup
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /signup")
    class Signup {

        private UserCreateReq validReq() {
            return new UserCreateReq(
                    "portone-id", "test@test.com", "Password1!", "홍길동", "01012345678",
                    LocalDate.of(1990, 1, 1), true, true, true, false);
        }

        @Test
        @DisplayName("회원가입 성공 — 201 + UserRes")
        void success() throws Exception {
            when(userService.signup(any())).thenReturn(sampleUser());

            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validReq())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1L))
                    .andExpect(jsonPath("$.email").value("test@test.com"));
        }

        @Test
        @DisplayName("이메일 형식 오류 — 400")
        void invalidEmail() throws Exception {
            UserCreateReq req = new UserCreateReq(
                    "portone-id", "not-an-email", "Password1!", "홍길동", "01012345678",
                    LocalDate.of(1990, 1, 1), true, true, true, false);

            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("비밀번호 형식 오류 (8자 미만) — 400")
        void invalidPassword() throws Exception {
            UserCreateReq req = new UserCreateReq(
                    "portone-id", "test@test.com", "short", "홍길동", "01012345678",
                    LocalDate.of(1990, 1, 1), true, true, true, false);

            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/auth/login
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /login")
    class Login {

        @Test
        @DisplayName("로그인 성공 — 200 + accessToken 바디 + refresh_token HttpOnly 쿠키")
        void success() throws Exception {
            LoginReq req = new LoginReq("test@test.com", "Password1!");
            LoginRes res = new LoginRes("access-token-value", "refresh-token-value", sampleUser());
            when(userService.login(any())).thenReturn(res);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token-value"))
                    .andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
                    .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
        }

        @Test
        @DisplayName("이메일 누락 — 400")
        void missingEmail() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"Password1!\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이메일 형식 오류 — 400")
        void invalidEmail() throws Exception {
            LoginReq req = new LoginReq("not-an-email", "Password1!");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/auth/refresh
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /refresh")
    class Refresh {

        @Test
        @DisplayName("토큰 재발급 성공 — 200 + 새 accessToken + 새 refresh_token 쿠키")
        void success() throws Exception {
            TokenRefreshReq req = new TokenRefreshReq("old-access-token");
            TokenRefreshRes res = new TokenRefreshRes("new-access-token", "new-refresh-token");
            when(userService.refresh(any(), any())).thenReturn(res);

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req))
                            .cookie(new Cookie("refresh_token", "old-refresh-token")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                    .andExpect(header().string("Set-Cookie", containsString("refresh_token=")));
        }

        @Test
        @DisplayName("refresh_token 쿠키 없음 — 401")
        void missingCookie() throws Exception {
            TokenRefreshReq req = new TokenRefreshReq("old-access-token");

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("accessToken 바디 누락 — 400")
        void missingAccessToken() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .cookie(new Cookie("refresh_token", "some-refresh-token")))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/auth/logout
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /logout")
    class Logout {

        @Test
        @DisplayName("로그아웃 성공 — 204 + refresh_token 만료 쿠키 (Max-Age=0)")
        void success() throws Exception {
            doNothing().when(userService).logout(any(), any());

            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer fake-token"))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
                    .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        }

        @Test
        @DisplayName("Authorization 헤더 없음 — 401")
        void missingAuthHeader() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
