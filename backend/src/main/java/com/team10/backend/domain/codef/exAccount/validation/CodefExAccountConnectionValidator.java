package com.team10.backend.domain.codef.exAccount.validation;

import com.team10.backend.domain.codef.exAccount.dto.req.CodefExAccountConnectionCreateReq;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

public class CodefExAccountConnectionValidator implements
        ConstraintValidator<ValidCodefExAccountConnection, CodefExAccountConnectionCreateReq> {

    @Override
    public boolean isValid(
            CodefExAccountConnectionCreateReq request,
            ConstraintValidatorContext context
    ) {
        if (request == null) {
            return true;
        }

        List<CodefExAccountConnectionPolicy.Violation> violations =
                CodefExAccountConnectionPolicy.findByOrganization(request.organization())
                        .map(policy -> policy.validate(request))
                        .orElseGet(() -> List.of(new CodefExAccountConnectionPolicy.Violation(
                                "organization",
                                "지원하지 않는 기관코드입니다."
                        )));

        if (violations.isEmpty()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        violations.forEach(violation -> context
                .buildConstraintViolationWithTemplate(violation.message())
                .addPropertyNode(violation.field())
                .addConstraintViolation());
        return false;
    }
}
