package com.team10.backend.domain.codef.exAccount.crypto;

import com.team10.backend.domain.codef.exAccount.config.CodefExAccountProperties;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountCryptoException;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class CodefExAccountPasswordEncryptor {

    private static final String KEY_ALGORITHM = "RSA";
    private static final String CIPHER_TRANSFORMATION = "RSA/ECB/PKCS1Padding";

    private final PublicKey publicKey;
    private final boolean isValid;

    public CodefExAccountPasswordEncryptor(CodefExAccountProperties properties) {
        PublicKey parsedKey = null;
        boolean valid = false;
        try {
            parsedKey = parsePublicKey(properties.publicKey());
            valid = true;
        } catch (Exception exception) {
            // JVM/Application initialization should not crash on invalid/mock keys in test/dev envs.
            // Functionality will fail gracefully during encryption.
        }
        this.publicKey = parsedKey;
        this.isValid = valid;
    }

    public String encrypt(String password) {
        if (password == null || password.isBlank()) {
            throw new CodefExAccountCryptoException("암호화할 은행 비밀번호가 없습니다.");
        }
        if (!isValid || publicKey == null) {
            throw new CodefExAccountCryptoException("CODEF 외부계좌 공개키가 올바르지 않습니다.");
        }

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new CodefExAccountCryptoException("은행 비밀번호 암호화에 실패했습니다.", exception);
        }
    }

    private PublicKey parsePublicKey(String encodedPublicKey) {
        try {
            String normalizedKey = encodedPublicKey
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(normalizedKey);
            return KeyFactory.getInstance(KEY_ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new CodefExAccountCryptoException("CODEF 외부계좌 공개키가 올바르지 않습니다.", exception);
        }
    }
}
