package com.team10.backend.global.validation.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValidNumberRangeTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validWhenMinAmountIsLessThanMaxAmount() {
        NumberRangeFixture request = new NumberRangeFixture(1_000L, 10_000L);

        Set<ConstraintViolation<NumberRangeFixture>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void validWhenMinAmountIsSameAsMaxAmount() {
        NumberRangeFixture request = new NumberRangeFixture(10_000L, 10_000L);

        Set<ConstraintViolation<NumberRangeFixture>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void validWhenEitherAmountIsNull() {
        assertThat(validator.validate(new NumberRangeFixture(null, 10_000L))).isEmpty();
        assertThat(validator.validate(new NumberRangeFixture(1_000L, null))).isEmpty();
        assertThat(validator.validate(new NumberRangeFixture(null, null))).isEmpty();
    }

    @Test
    void invalidWhenMinAmountIsGreaterThanMaxAmount() {
        NumberRangeFixture request = new NumberRangeFixture(10_000L, 1_000L);

        Set<ConstraintViolation<NumberRangeFixture>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        ConstraintViolation<NumberRangeFixture> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath()).hasToString("minAmount");
        assertThat(violation.getMessage()).isEqualTo("최소값은 최대값보다 클 수 없습니다.");
    }

    @ValidNumberRange(min = "minAmount", max = "maxAmount")
    private record NumberRangeFixture(
            Long minAmount,
            Long maxAmount
    ) {
    }
}
