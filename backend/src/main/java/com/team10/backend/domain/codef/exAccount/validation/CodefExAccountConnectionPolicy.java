package com.team10.backend.domain.codef.exAccount.validation;

import com.team10.backend.domain.codef.exAccount.dto.req.CodefExAccountConnectionCreateReq;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum CodefExAccountConnectionPolicy {

    KB_ID_PASSWORD("0004"),
    SHINHAN_ID_PASSWORD("0088"),
    NH_ID_PASSWORD("0011"),
    WOORI_ID_PASSWORD("0020"),
    HANA_ID_PASSWORD("0081");

    private static final String BUSINESS_TYPE = "BK";
    private static final String CLIENT_TYPE = "P";
    private static final String LOGIN_TYPE = "1";
    private static final DateTimeFormatter BIRTH_DATE_FORMATTER = new DateTimeFormatterBuilder()
            .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
            .appendPattern("MMdd")
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    private final String organization;

    CodefExAccountConnectionPolicy(String organization) {
        this.organization = organization;
    }

    public static Optional<CodefExAccountConnectionPolicy> findByOrganization(String organization) {
        return Arrays.stream(values())
                .filter(policy -> policy.organization.equals(organization))
                .findFirst();
    }

    public List<Violation> validate(CodefExAccountConnectionCreateReq request) {
        return java.util.stream.Stream.of(
                        fixedValueViolation("businessType", request.businessType(), BUSINESS_TYPE, "업무 구분은 BK만 지원합니다."),
                        fixedValueViolation("clientType", request.clientType(), CLIENT_TYPE, "개인 고객만 연결할 수 있습니다."),
                        fixedValueViolation("loginType", request.loginType(), LOGIN_TYPE, "ID/PW 로그인 방식만 지원합니다."),
                        requiredViolation("loginId", request.loginId(), "로그인 ID는 필수입니다."),
                        requiredViolation("password", request.password(), "비밀번호는 필수입니다."),
                        birthDateViolation(request.birthDate())
                )
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<Violation> fixedValueViolation(
            String field,
            String actual,
            String expected,
            String message
    ) {
        if (actual == null || actual.isBlank() || expected.equals(actual)) {
            return Optional.empty();
        }
        return Optional.of(new Violation(field, message));
    }

    private Optional<Violation> requiredViolation(String field, String value, String message) {
        if (value != null && !value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Violation(field, message));
    }

    private Optional<Violation> birthDateViolation(String birthDate) {
        if (birthDate == null || birthDate.isBlank()) {
            return Optional.of(new Violation("birthDate", "생년월일은 필수입니다."));
        }

        try {
            LocalDate.parse(birthDate, BIRTH_DATE_FORMATTER);
            return Optional.empty();
        } catch (DateTimeException exception) {
            return Optional.of(new Violation("birthDate", "생년월일은 YYMMDD 형식이어야 합니다."));
        }
    }

    public record Violation(String field, String message) {
    }
}
