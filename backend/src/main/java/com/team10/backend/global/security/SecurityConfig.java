package com.team10.backend.global.security;

import com.team10.backend.global.jwt.JwtProvider;
import com.team10.backend.global.jwt.TokenBlocklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final TokenBlocklistService tokenBlocklistService;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final Environment environment;

    /** 허용할 Origin 목록. 환경변수 CORS_ALLOWED_ORIGINS로 주입, 기본값은 로컬 개발 서버. */
    @Value("${cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtProvider, tokenBlocklistService);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // H2 Console은 dev/test 프로필에서만 인증 없이 열어준다 — prod 등 다른 프로필에 가드 없이 노출되는 것을 방지.
        boolean h2ConsoleEnabled = environment.acceptsProfiles(Profiles.of("dev", "test"));

        http
                // JWT Stateless — 세션, CSRF 불필요
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 인가 규칙
                .authorizeHttpRequests(auth -> {
                    // 인증 불필요
                    auth.requestMatchers(
                            "/api/v1/auth/signup",
                            "/api/v1/auth/login",
                            "/api/v1/auth/refresh"
                    ).permitAll();

                    // 주식 종목 조회
                    auth.requestMatchers(
                            HttpMethod.GET,
                            "/api/v1/investment/stocks/**"
                    ).permitAll();

                    // 공개 환전 조회 API
                    auth.requestMatchers(HttpMethod.GET,
                            "/api/v1/exchanges/rates",
                            "/api/v1/exchanges/currencies",
                            "/api/v1/exchanges/currencies/{currencyCode}"
                    ).permitAll();

                    // Swagger UI
                    auth.requestMatchers(
                            "/swagger-ui/**",
                            "/v3/api-docs/**",
                            "/swagger-ui.html"
                    ).permitAll();

                    // H2 Console (dev/test 전용)
                    if (h2ConsoleEnabled) {
                        auth.requestMatchers("/h2-console/**").permitAll();
                    }

                    // Spring 내부 에러 포워딩 — 막히면 실제 예외가 401/403으로 둔갑함
                    auth.requestMatchers("/error").permitAll();

                    // 로그아웃은 만료 토큰도 허용 → 필터에서 직접 처리, 여기선 authenticated
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated();

                    // 나머지 전부 인증 필요
                    auth.anyRequest().authenticated();
                })

                // H2 Console iframe 허용은 dev/test에서만 — 그 외 프로필은 기본값(DENY)을 유지해 클릭재킹 방어를 약화시키지 않음
                .headers(headers -> {
                    if (h2ConsoleEnabled) {
                        headers.frameOptions(frame -> frame.sameOrigin());
                    } else {
                        headers.frameOptions(frame -> frame.deny());
                    }
                })

                // 인증/인가 예외 처리 — 필터 체인에서 직접 JSON 응답 (GlobalExceptionHandler 미적용 영역)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)   // 401
                        .accessDeniedHandler(accessDeniedHandler)              // 403
                )

                // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 삽입
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
