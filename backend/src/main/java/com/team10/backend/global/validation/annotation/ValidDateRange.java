package com.team10.backend.global.validation.annotation;

import com.team10.backend.global.validation.validator.DateRangeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = DateRangeValidator.class)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDateRange {

    String message() default "시작 날짜는 종료 날짜보다 이후일 수 없습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    // 애노테이션의 사용 위치에서 start, end에 해당하는 필드명을 명시한다
    // ex) @ValidDateRange(start = "startDate", end = "endDate")
    String start();

    String end();
}
