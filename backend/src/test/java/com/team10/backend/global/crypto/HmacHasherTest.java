package com.team10.backend.global.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link HmacHasher}의 키 로테이션(버전별 키 관리) 동작을 검증한다.
 * Spring 컨텍스트 없이 {@link HmacProperties}를 직접 생성해 단위 테스트로 구성한다
 * (KisAuthClientTest가 KisProperties를 직접 생성하는 방식과 동일).
 */
class HmacHasherTest {

    private static String randomBase64Key() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static final String V1_KEY = randomBase64Key();
    private static final String V2_KEY = randomBase64Key();
    private static final String PLAIN_TEXT = "901201-1234567";

    @Nested
    @DisplayName("hash")
    class Hash {

        private final HmacHasher hasher = new HmacHasher(new HmacProperties("v1", Map.of("v1", V1_KEY)));

        @Test
        @DisplayName("null 입력 → null 반환")
        void nullInput_returnsNull() {
            assertThat(hasher.hash(null)).isNull();
        }

        @Test
        @DisplayName("active-version 접두사가 붙어서 반환된다")
        void prependsActiveVersion() {
            assertThat(hasher.hash(PLAIN_TEXT)).startsWith("v1:");
        }

        @Test
        @DisplayName("동일 입력은 항상 동일 출력 (deterministic)")
        void deterministic() {
            assertThat(hasher.hash(PLAIN_TEXT)).isEqualTo(hasher.hash(PLAIN_TEXT));
        }

        @Test
        @DisplayName("키가 다르면 출력도 다르다")
        void differentKey_producesDifferentHash() {
            HmacHasher v2Hasher = new HmacHasher(new HmacProperties("v2", Map.of("v2", V2_KEY)));
            assertThat(hasher.hash(PLAIN_TEXT)).isNotEqualTo(v2Hasher.hash(PLAIN_TEXT));
        }
    }

    @Nested
    @DisplayName("matches")
    class Matches {

        @Test
        @DisplayName("hash()로 만든 값은 matches()로 검증된다")
        void roundTrip() {
            HmacHasher hasher = new HmacHasher(new HmacProperties("v1", Map.of("v1", V1_KEY)));
            String stored = hasher.hash(PLAIN_TEXT);

            assertThat(hasher.matches(PLAIN_TEXT, stored)).isTrue();
        }

        @Test
        @DisplayName("평문이 다르면 일치하지 않는다")
        void mismatch_whenPlainTextDiffers() {
            HmacHasher hasher = new HmacHasher(new HmacProperties("v1", Map.of("v1", V1_KEY)));
            String stored = hasher.hash(PLAIN_TEXT);

            assertThat(hasher.matches("901201-7654321", stored)).isFalse();
        }

        @Test
        @DisplayName("버전 접두사가 없는 레거시 값은 v1 키로 검증된다")
        void legacyValueWithoutPrefix_verifiedAgainstV1() {
            HmacHasher v1Only = new HmacHasher(new HmacProperties("v1", Map.of("v1", V1_KEY)));
            // 로테이션 도입 이전에 저장된 데이터를 흉내 — 접두사 없이 digest만 저장됨
            String legacyStored = v1Only.hash(PLAIN_TEXT).substring("v1:".length());

            HmacHasher rotated = new HmacHasher(new HmacProperties("v2", Map.of("v1", V1_KEY, "v2", V2_KEY)));

            assertThat(rotated.matches(PLAIN_TEXT, legacyStored)).isTrue();
        }

        @Test
        @DisplayName("로테이션 후에도 옛 버전으로 저장된 값은 옛 키로 계속 검증되고, 새 해시는 새 버전으로 생성된다")
        void afterRotation_oldVersionedValueStillMatches_andNewHashUsesNewVersion() {
            HmacHasher beforeRotation = new HmacHasher(new HmacProperties("v1", Map.of("v1", V1_KEY)));
            String storedWithV1 = beforeRotation.hash(PLAIN_TEXT);

            HmacHasher afterRotation = new HmacHasher(
                    new HmacProperties("v2", Map.of("v1", V1_KEY, "v2", V2_KEY)));

            assertThat(afterRotation.matches(PLAIN_TEXT, storedWithV1)).isTrue();
            assertThat(afterRotation.hash(PLAIN_TEXT)).startsWith("v2:");
        }

        @Test
        @DisplayName("저장된 값의 키 버전이 폐기되어 더 이상 없으면 검증에 실패한다")
        void revokedVersion_returnsFalse() {
            HmacHasher beforeRotation = new HmacHasher(new HmacProperties("v1", Map.of("v1", V1_KEY)));
            String storedWithV1 = beforeRotation.hash(PLAIN_TEXT);

            HmacHasher afterRevoke = new HmacHasher(new HmacProperties("v2", Map.of("v2", V2_KEY)));

            assertThat(afterRevoke.matches(PLAIN_TEXT, storedWithV1)).isFalse();
        }

        @Test
        @DisplayName("plainText 또는 stored가 null이면 false")
        void nullInputs_returnFalse() {
            HmacHasher hasher = new HmacHasher(new HmacProperties("v1", Map.of("v1", V1_KEY)));

            assertThat(hasher.matches(null, "v1:abc")).isFalse();
            assertThat(hasher.matches(PLAIN_TEXT, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("생성 검증")
    class Construction {

        @Test
        @DisplayName("active-version에 해당하는 키가 keys에 없으면 생성 실패")
        void activeVersionMissingFromKeys_throws() {
            assertThatThrownBy(() -> new HmacHasher(new HmacProperties("v2", Map.of("v1", V1_KEY))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("active-version");
        }

        @Test
        @DisplayName("32바이트 미만 키는 생성 실패")
        void tooShortKey_throws() {
            String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

            assertThatThrownBy(() -> new HmacHasher(new HmacProperties("v1", Map.of("v1", shortKey))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("32바이트");
        }

        @Test
        @DisplayName("Base64 형식이 아닌 키는 생성 실패")
        void invalidBase64Key_throws() {
            assertThatThrownBy(() -> new HmacHasher(new HmacProperties("v1", Map.of("v1", "not-base64!!"))))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("keys가 비어있으면 생성 실패")
        void emptyKeys_throws() {
            assertThatThrownBy(() -> new HmacHasher(new HmacProperties("v1", Map.of())))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
