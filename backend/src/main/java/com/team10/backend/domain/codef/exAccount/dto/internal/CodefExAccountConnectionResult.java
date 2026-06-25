package com.team10.backend.domain.codef.exAccount.dto.internal;

public record CodefExAccountConnectionResult(
        String connectedId
) {

    @Override
    public String toString() {
        return "CodefExAccountConnectionResult[connectedId=<redacted>]";
    }
}
