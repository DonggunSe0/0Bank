package com.team10.backend.domain.codef.exAccount.mapper;

import com.team10.backend.domain.codef.exAccount.crypto.CodefExAccountPasswordEncryptor;
import com.team10.backend.domain.codef.exAccount.dto.internal.CodefExAccountConnectionPayload;
import com.team10.backend.domain.codef.exAccount.dto.req.CodefExAccountConnectionCreateReq;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CodefExAccountConnectionPayloadMapper {

    private static final String COUNTRY_CODE = "KR";

    private final CodefExAccountPasswordEncryptor passwordEncryptor;

    public CodefExAccountConnectionPayloadMapper(CodefExAccountPasswordEncryptor passwordEncryptor) {
        this.passwordEncryptor = passwordEncryptor;
    }

    public CodefExAccountConnectionPayload toPayload(CodefExAccountConnectionCreateReq request) {
        String encryptedPassword = passwordEncryptor.encrypt(request.password());
        CodefExAccountConnectionPayload.Account account = new CodefExAccountConnectionPayload.Account(
                COUNTRY_CODE,
                request.businessType(),
                request.clientType(),
                request.organization(),
                request.loginType(),
                request.loginId(),
                encryptedPassword,
                request.birthDate()
        );
        return new CodefExAccountConnectionPayload(List.of(account));
    }
}
