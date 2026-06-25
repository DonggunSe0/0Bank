package com.team10.backend.domain.codef.exAccount.dto.req;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CodefExAccountConnectionCreateReqTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsSupportedIdPasswordConnectionRequest() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    void acceptsBanksOfferedByImportAccountPage() {
        assertThat(validator.validate(requestWith("0004", "BK", "P", "1", "990101"))).isEmpty();
        assertThat(validator.validate(requestWith("0088", "BK", "P", "1", "990101"))).isEmpty();
        assertThat(validator.validate(requestWith("0011", "BK", "P", "1", "990101"))).isEmpty();
        assertThat(validator.validate(requestWith("0020", "BK", "P", "1", "990101"))).isEmpty();
        assertThat(validator.validate(requestWith("0081", "BK", "P", "1", "990101"))).isEmpty();
    }

    @Test
    void rejectsUnsupportedOrganization() {
        CodefExAccountConnectionCreateReq request = requestWith("9999", "BK", "P", "1", "990101");

        assertViolation(request, "organization", "지원하지 않는 기관코드입니다.");
    }

    @Test
    void rejectsUnsupportedLoginContract() {
        CodefExAccountConnectionCreateReq request = requestWith("0004", "ST", "B", "0", "990101");

        Set<ConstraintViolation<CodefExAccountConnectionCreateReq>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("businessType", "clientType", "loginType");
    }

    @Test
    void rejectsInvalidBirthDate() {
        CodefExAccountConnectionCreateReq request = requestWith("0004", "BK", "P", "1", "991332");

        assertViolation(request, "birthDate", "생년월일은 YYMMDD 형식이어야 합니다.");
    }

    @Test
    void requiresInstitutionCredentials() {
        CodefExAccountConnectionCreateReq request = new CodefExAccountConnectionCreateReq(
                "0004", "BK", "P", "1", " ", " ", "990101"
        );

        Set<ConstraintViolation<CodefExAccountConnectionCreateReq>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("loginId", "password");
    }

    @Test
    void redactsSensitiveValuesFromToString() {
        CodefExAccountConnectionCreateReq request = validRequest();

        assertThat(request.toString())
                .contains("organization=0004", "loginId=<redacted>", "password=<redacted>")
                .doesNotContain("internet-user", "bank-password", "990101");
    }

    private void assertViolation(
            CodefExAccountConnectionCreateReq request,
            String field,
            String message
    ) {
        assertThat(validator.validate(request))
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo(field);
                    assertThat(violation.getMessage()).isEqualTo(message);
                    assertThat(violation.getMessage())
                            .doesNotContain(request.loginId(), request.password());
                });
    }

    private CodefExAccountConnectionCreateReq validRequest() {
        return requestWith("0004", "BK", "P", "1", "990101");
    }

    private CodefExAccountConnectionCreateReq requestWith(
            String organization,
            String businessType,
            String clientType,
            String loginType,
            String birthDate
    ) {
        return new CodefExAccountConnectionCreateReq(
                organization,
                businessType,
                clientType,
                loginType,
                "internet-user",
                "bank-password",
                birthDate
        );
    }
}
