package com.team10.backend.domain.codef.exAccount.dto.internal;

public record CodefExAccountTransactionListRequest(
        String organization,
        String connectedId,
        String birthDate,
        String account,
        String startDate,
        String endDate,
        String orderBy
) {

    public CodefExAccountTransactionListRequest {
        birthDate = defaultString(birthDate);
        account = defaultString(account);
        startDate = defaultString(startDate);
        endDate = defaultString(endDate);
        orderBy = defaultString(orderBy);
    }

    public static CodefExAccountTransactionListRequest of(
            String organization,
            String connectedId,
            String birthDate,
            String account,
            String startDate,
            String endDate
    ) {
        return new CodefExAccountTransactionListRequest(
                organization,
                connectedId,
                birthDate,
                account,
                startDate,
                endDate,
                "1"
        );
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return "CodefExAccountTransactionListRequest[organization=" + organization
                + ", connectedId=<redacted>"
                + ", birthDate=<redacted>"
                + ", account=<redacted>"
                + ", startDate=" + startDate
                + ", endDate=" + endDate
                + ", orderBy=" + orderBy
                + "]";
    }
}
