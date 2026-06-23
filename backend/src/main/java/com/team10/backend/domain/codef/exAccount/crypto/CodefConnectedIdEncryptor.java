package com.team10.backend.domain.codef.exAccount.crypto;

import com.team10.backend.domain.codef.exAccount.config.CodefConnectedIdCryptoProperties;
import com.team10.backend.domain.exAccount.entity.value.EncryptedConnectedId;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountCryptoException;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class CodefConnectedIdEncryptor {

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKey;
    private final String keyVersion;
    private final SecureRandom secureRandom;

    public CodefConnectedIdEncryptor(CodefConnectedIdCryptoProperties properties) {
        this.secretKey = parseSecretKey(properties.secretKey());
        this.keyVersion = properties.keyVersion();
        this.secureRandom = new SecureRandom();
    }

    public EncryptedConnectedId encrypt(String connectedId) {
        if (connectedId == null || connectedId.isBlank()) {
            throw new CodefExAccountCryptoException("암호화할 connectedId가 없습니다.");
        }

        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = createCipher(Cipher.ENCRYPT_MODE, iv);
            byte[] ciphertext = cipher.doFinal(connectedId.getBytes(StandardCharsets.UTF_8));
            return new EncryptedConnectedId(
                    Base64.getEncoder().encodeToString(ciphertext),
                    Base64.getEncoder().encodeToString(iv),
                    keyVersion
            );
        } catch (GeneralSecurityException exception) {
            throw new CodefExAccountCryptoException("connectedId 암호화에 실패했습니다.", exception);
        }
    }

    public String decrypt(EncryptedConnectedId encryptedConnectedId) {
        validateEncryptedValue(encryptedConnectedId);

        try {
            byte[] iv = Base64.getDecoder().decode(encryptedConnectedId.iv());
            byte[] ciphertext = Base64.getDecoder().decode(encryptedConnectedId.ciphertext());
            if (iv.length != IV_LENGTH_BYTES) {
                throw new CodefExAccountCryptoException("암호화된 connectedId 형식이 올바르지 않습니다.");
            }
            Cipher cipher = createCipher(Cipher.DECRYPT_MODE, iv);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (AEADBadTagException exception) {
            throw new CodefExAccountCryptoException("암호화된 connectedId 검증에 실패했습니다.", exception);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new CodefExAccountCryptoException("connectedId 복호화에 실패했습니다.", exception);
        }
    }

    private Cipher createCipher(int mode, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(mode, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        cipher.updateAAD(aad());
        return cipher;
    }

    private byte[] aad() {
        return ("codef-connected-id:" + keyVersion).getBytes(StandardCharsets.UTF_8);
    }

    private SecretKeySpec parseSecretKey(String encodedKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
                throw new CodefExAccountCryptoException("connectedId 암호화 키 길이가 올바르지 않습니다.");
            }
            return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
        } catch (IllegalArgumentException exception) {
            throw new CodefExAccountCryptoException("connectedId 암호화 키 형식이 올바르지 않습니다.", exception);
        }
    }

    private void validateEncryptedValue(EncryptedConnectedId encryptedConnectedId) {
        if (encryptedConnectedId == null
                || encryptedConnectedId.ciphertext() == null
                || encryptedConnectedId.ciphertext().isBlank()
                || encryptedConnectedId.iv() == null
                || encryptedConnectedId.iv().isBlank()) {
            throw new CodefExAccountCryptoException("암호화된 connectedId가 없습니다.");
        }
        if (!keyVersion.equals(encryptedConnectedId.keyVersion())) {
            throw new CodefExAccountCryptoException("지원하지 않는 connectedId 암호화 키 버전입니다.");
        }
    }
}
