package com.team10.backend.global.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * HMAC-SHA256 기반 단방향(one-way) 해시 유틸리티.
 *
 * <p>AES-256-GCM 같은 양방향 암호화는 복호화가 가능하지만, 이 클래스는 한 번 해시한 값을
 * 다시 원문으로 되돌릴 수 없다. 애플리케이션에서 평문을 다시 읽어야 할 일이 없고
 * "같은 값인지 동등 비교"만 필요한 민감정보(예: 주민등록번호)에 사용한다.
 *
 * <p>단순 SHA-256과 달리 비밀 키가 있어야 계산할 수 있는 MAC이므로, 키를 모르는 공격자는
 * 데이터가 유출되더라도 무차별 대입·레인보우 테이블 공격으로 원문을 복원할 수 없다
 * (주민등록번호처럼 생년월일+일련번호 구조라 엔트로피가 낮은 값일수록 이 차이가 중요하다).
 * 동일 입력은 항상 동일 출력을 내므로(deterministic) 중복 검사용 동등 비교에도 활용할 수 있다.
 *
 * <p>{@code @Component}로 등록해 생성자에 {@code app.hmac.key}를 주입받는다.
 */
@Component
public class HmacHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec keySpec;

    public HmacHasher(@Value("${app.hmac.key}") String base64Key) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.hmac.key는 유효한 Base64 문자열이어야 합니다.", e);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.hmac.key는 Base64로 인코딩된 32바이트 이상의 키여야 합니다. 현재 길이: " + keyBytes.length + "byte");
        }
        this.keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /** 평문을 HMAC-SHA256으로 해싱해 Base64 문자열로 반환한다. 동일 입력은 항상 동일 출력이며, 복호화는 불가능하다. */
    public String hash(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(keySpec);
            byte[] digest = mac.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("값 해싱에 실패했습니다.", e);
        }
    }
}
