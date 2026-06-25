package com.team10.backend.global.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 서버 비밀키를 사용해 입력값의 HMAC-SHA-256 해시를 생성한다.
 * 동일한 키와 입력값에 항상 같은 결과를 반환하므로 blind index 생성에 사용한다.
 */
@Component
public class HmacSha256Hasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec secretKey;

    public HmacSha256Hasher(
            @Value("${security.hmac.secret}") String base64EncodedSecret
    ) {
        this.secretKey = createSecretKey(base64EncodedSecret);
    }

    /** 입력값의 HMAC-SHA-256 결과를 64자리 16진수 문자열로 반환한다. */
    public String hash(String value) {
        Objects.requireNonNull(value, "해싱할 값은 null일 수 없습니다.");

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKey);

            byte[] hash = mac.doFinal(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "HMAC-SHA-256 해시 생성에 실패했습니다.",
                    e
            );
        }
    }

    private SecretKeySpec createSecretKey(String base64EncodedSecret) {
        if (base64EncodedSecret == null || base64EncodedSecret.isBlank()) {
            throw new IllegalArgumentException(
                    "HMAC 비밀키가 설정되지 않았습니다."
            );
        }

        byte[] decodedSecret;
        try {
            decodedSecret = Base64.getDecoder().decode(base64EncodedSecret);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "HMAC 비밀키는 유효한 Base64 형식이어야 합니다.",
                    e
            );
        }

        return new SecretKeySpec(decodedSecret, ALGORITHM);
    }
}
