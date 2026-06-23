package com.team10.backend.domain.codef.exAccount.crypto;

import com.team10.backend.domain.codef.exAccount.config.CodefExAccountProperties;
import com.team10.backend.domain.codef.exAccount.exception.CodefExAccountCryptoException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodefExAccountPasswordEncryptorTest {

    private static KeyPair keyPair;
    private static CodefExAccountPasswordEncryptor encryptor;

    @BeforeAll
    static void setUpKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        encryptor = new CodefExAccountPasswordEncryptor(properties(publicKey));
    }

    @Test
    void encryptsPasswordWithCodefCompatibleRsaFormat() throws Exception {
        String plainPassword = "bank-password";

        String encryptedPassword = encryptor.encrypt(plainPassword);

        assertThat(encryptedPassword).isNotEqualTo(plainPassword);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        String decrypted = new String(
                cipher.doFinal(Base64.getDecoder().decode(encryptedPassword)),
                StandardCharsets.UTF_8
        );
        assertThat(decrypted).isEqualTo(plainPassword);
    }

    @Test
    void acceptsPemFormattedPublicKey() {
        String encodedKey = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(keyPair.getPublic().getEncoded());
        String pemKey = "-----BEGIN PUBLIC KEY-----\n" + encodedKey + "\n-----END PUBLIC KEY-----";
        CodefExAccountPasswordEncryptor pemEncryptor =
                new CodefExAccountPasswordEncryptor(properties(pemKey));

        assertThat(pemEncryptor.encrypt("bank-password")).isNotBlank();
    }

    @Test
    void rejectsInvalidPublicKeyWithoutExposingIt() {
        CodefExAccountPasswordEncryptor invalidEncryptor =
                new CodefExAccountPasswordEncryptor(properties("invalid-public-key"));
        assertThatThrownBy(() -> invalidEncryptor.encrypt("bank-password"))
                .isInstanceOf(CodefExAccountCryptoException.class)
                .hasMessage("CODEF 외부계좌 공개키가 올바르지 않습니다.")
                .hasMessageNotContaining("invalid-public-key");
    }

    @Test
    void rejectsBlankPasswordWithoutIncludingCredentialInMessage() {
        assertThatThrownBy(() -> encryptor.encrypt(" "))
                .isInstanceOf(CodefExAccountCryptoException.class)
                .hasMessage("암호화할 은행 비밀번호가 없습니다.");
    }

    private static CodefExAccountProperties properties(String publicKey) {
        return new CodefExAccountProperties(
                "DEMO",
                "account-client-id",
                "account-client-secret",
                publicKey,
                "https://development.codef.io",
                "/v1/account/create",
                "/account-list",
                "/transaction-list"
        );
    }
}
