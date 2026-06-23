package com.team10.backend.domain.codef.exAccount.dto.internal;

import java.util.List;

public record CodefExAccountConnectionPayload(
        List<Account> accountList
) {

    public CodefExAccountConnectionPayload {
        accountList = List.copyOf(accountList);
    }

    public record Account(
            String countryCode,
            String businessType,
            String clientType,
            String organization,
            String loginType,
            String id,
            String password,
            String birthDate
    ) {

        @Override
        public String toString() {
            return "Account[countryCode=" + countryCode
                    + ", businessType=" + businessType
                    + ", clientType=" + clientType
                    + ", organization=" + organization
                    + ", loginType=" + loginType
                    + ", id=<redacted>"
                    + ", password=<redacted>"
                    + ", birthDate=<redacted>]";
        }
    }
}
