package com.team10.backend.global.validation.annotation;

import com.team10.backend.global.validation.validator.NumberRangeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = NumberRangeValidator.class)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidNumberRange {

    String message() default "최소값은 최대값보다 클 수 없습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    // 애노테이션의 사용 위치에서 min, max에 해당하는 필드명을 명시한다
    // ex) @ValidDateRange(min = "minNumber", max = "maxNumber")
    String min();

    String max();
}
