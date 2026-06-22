package com.team10.backend.global.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HMAC-SHA256 기반 단방향 해시 유틸리티 — 복호화 없이 동등 비교만 필요한 민감정보(예: 주민등록번호)에 사용한다.
 * 버전별 키로 로테이션을 지원: {@link #hash}는 {@code "{version}:"} 접두사를 붙여 저장하고, {@link #matches}는
 * 그 버전의 키로 검증한다({@link HmacProperties}). 로테이션 시 기존 키는 지우지 않아야 옛 값도 계속 검증된다.
 */
@Slf4j
@Component
public class HmacHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String VERSION_DELIMITER = ":";

    /** 버전 접두사가 없는 레거시 데이터(로테이션 도입 이전 저장값)를 검증할 때 사용할 키 버전. */
    static final String LEGACY_VERSION = "v1";

    private final Map<String, SecretKeySpec> keysByVersion;
    private final String activeVersion;

    public HmacHasher(HmacProperties properties) {
        Map<String, String> configuredKeys = properties.keys();
        if (configuredKeys == null || configuredKeys.isEmpty()) {
            throw new IllegalStateException("app.hmac.keys에 최소 1개 이상의 키가 필요합니다.");
        }
        this.keysByVersion = configuredKeys.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> toKeySpec(e.getKey(), e.getValue())));

        this.activeVersion = properties.activeVersion();
        if (!keysByVersion.containsKey(activeVersion)) {
            throw new IllegalStateException(
                    "app.hmac.active-version(" + activeVersion + ")에 해당하는 키가 app.hmac.keys에 없습니다.");
        }
    }

    private static SecretKeySpec toKeySpec(String version, String base64Key) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "app.hmac.keys." + version + "는 유효한 Base64 문자열이어야 합니다.", e);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.hmac.keys." + version + "는 Base64로 인코딩된 32바이트 이상의 키여야 합니다. 현재 길이: "
                            + keyBytes.length + "byte");
        }
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * 평문을 현재 활성 키({@code app.hmac.active-version})로 해싱해 {@code "{version}:{base64}"}
     * 형태로 반환한다. 복호화는 불가능하다.
     */
    public String hash(String plainText) {
        if (plainText == null) {
            return null;
        }
        return activeVersion + VERSION_DELIMITER + digest(activeVersion, plainText);
    }

    /**
     * 평문이 {@link #hash}로 저장해 둔 값과 일치하는지 확인한다. 평문을 직접 비교하지 않고
     * stored에 적힌 버전의 키로 다시 해싱해 비교하므로, 키가 로테이션된 뒤에도 옛 버전으로
     * 저장된 값을 올바르게 검증할 수 있다.
     *
     * @return 일치 여부. stored가 가리키는 키 버전이 더 이상 {@code app.hmac.keys}에 없으면(키 폐기) false.
     */
    public boolean matches(String plainText, String stored) {
        if (plainText == null || stored == null) {
            return false;
        }

        String version;
        String storedDigest;
        int idx = stored.indexOf(VERSION_DELIMITER);
        if (idx >= 0 && keysByVersion.containsKey(stored.substring(0, idx))) {
            version = stored.substring(0, idx);
            storedDigest = stored.substring(idx + 1);
        } else {
            version = LEGACY_VERSION;
            storedDigest = stored;
        }

        if (!keysByVersion.containsKey(version)) {
            log.warn("[HmacHasher] 알 수 없는 키 버전 — version={} (폐기된 키일 수 있음)", version);
            return false;
        }
        return digest(version, plainText).equals(storedDigest);
    }

    private String digest(String version, String plainText) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(keysByVersion.get(version));
            byte[] digestBytes = mac.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digestBytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("값 해싱에 실패했습니다.", e);
        }
    }
}
