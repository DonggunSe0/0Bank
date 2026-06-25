package com.team10.backend.global.validation.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValidDateRangeTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validWhenStartDateIsBeforeEndDate() {
        DateRangeFixture request = new DateRangeFixture(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 9)
        );

        Set<ConstraintViolation<DateRangeFixture>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void validWhenStartDateIsSameAsEndDate() {
        DateRangeFixture request = new DateRangeFixture(
                LocalDate.of(2026, 6, 9),
                LocalDate.of(2026, 6, 9)
        );

        Set<ConstraintViolation<DateRangeFixture>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void validWhenEitherDateIsNull() {
        assertThat(validator.validate(new DateRangeFixture(null, LocalDate.of(2026, 6, 9)))).isEmpty();
        assertThat(validator.validate(new DateRangeFixture(LocalDate.of(2026, 6, 1), null))).isEmpty();
        assertThat(validator.validate(new DateRangeFixture(null, null))).isEmpty();
    }

    @Test
    void invalidWhenStartDateIsAfterEndDate() {
        DateRangeFixture request = new DateRangeFixture(
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 9)
        );

        Set<ConstraintViolation<DateRangeFixture>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        ConstraintViolation<DateRangeFixture> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath()).hasToString("startDate");
        assertThat(violation.getMessage()).isEqualTo("시작 날짜는 종료 날짜보다 이후일 수 없습니다.");
    }

    @Test
    void supportsComparableDateTimeValues() {
        DateTimeRangeFixture request = new DateTimeRangeFixture(
                LocalDateTime.of(2026, 6, 9, 10, 0),
                LocalDateTime.of(2026, 6, 9, 9, 59)
        );

        Set<ConstraintViolation<DateTimeRangeFixture>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath()).hasToString("startAt");
    }

    @ValidDateRange(start = "startDate", end = "endDate")
    private record DateRangeFixture(
            LocalDate startDate,
            LocalDate endDate
    ) {
    }

    @ValidDateRange(start = "startAt", end = "endAt")
    private record DateTimeRangeFixture(
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
    }
}
