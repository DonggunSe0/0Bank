package com.team10.backend.domain.codef.exAccount.dto.internal;

public record CodefExAccountListRequest(
        String organization,
        String connectedId,
        String birthDate,
        String withdrawAccountNo,
        String withdrawAccountPassword
) {

    public CodefExAccountListRequest {
        birthDate = defaultString(birthDate);
        withdrawAccountNo = defaultString(withdrawAccountNo);
        withdrawAccountPassword = defaultString(withdrawAccountPassword);
    }

    public static CodefExAccountListRequest of(
            String organization,
            String connectedId,
            String birthDate
    ) {
        return new CodefExAccountListRequest(organization, connectedId, birthDate, "", "");
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return "CodefExAccountListRequest[organization=" + organization
                + ", connectedId=<redacted>"
                + ", birthDate=<redacted>"
                + ", withdrawAccountNo=<redacted>"
                + ", withdrawAccountPassword=<redacted>]";
    }
}
