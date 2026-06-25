package com.team10.backend.global.crypto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * {@code app.hmac.*} 설정 바인딩 — {@link HmacHasher} 키 로테이션용 버전별 키 목록.
 *
 * @param activeVersion 새로 해싱할 때 쓸 키 버전. {@code keys}에 존재해야 한다.
 * @param keys          버전 → Base64 32바이트 이상 키. 로테이션 시 기존 버전은 지우지 않는다.
 */
@Validated
@ConfigurationProperties(prefix = "app.hmac")
public record HmacProperties(
        @NotBlank String activeVersion,
        @NotEmpty Map<String, String> keys
) {
}
