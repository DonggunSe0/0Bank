package com.team10.backend.domain.codef.exAccount.mapper;

import com.team10.backend.domain.codef.exAccount.crypto.CodefExAccountPasswordEncryptor;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountConnectionPayload;
import com.team10.backend.domain.codef.exAccount.dto.req.CodefExAccountConnectionCreateReq;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodefExAccountConnectionPayloadMapperTest {

    @Test
    void encryptsPasswordWhenCreatingCodefRequestPayload() {
        CodefExAccountPasswordEncryptor encryptor = mock(CodefExAccountPasswordEncryptor.class);
        when(encryptor.encrypt("bank-password")).thenReturn("rsa-encrypted-password");
        CodefExAccountConnectionPayloadMapper mapper = new CodefExAccountConnectionPayloadMapper(encryptor);
        CodefExAccountConnectionCreateReq request = new CodefExAccountConnectionCreateReq(
                "0004", "BK", "P", "1", "internet-user", "bank-password", "990101"
        );

        CodefExAccountConnectionPayload payload = mapper.toPayload(request);

        assertThat(payload.accountList()).singleElement().satisfies(account -> {
            assertThat(account.countryCode()).isEqualTo("KR");
            assertThat(account.organization()).isEqualTo("0004");
            assertThat(account.id()).isEqualTo("internet-user");
            assertThat(account.password())
                    .isEqualTo("rsa-encrypted-password")
                    .isNotEqualTo(request.password());
        });
        assertThat(payload.toString())
                .doesNotContain("internet-user", "bank-password", "rsa-encrypted-password", "990101");
        verify(encryptor).encrypt("bank-password");
    }
}
