package com.team10.backend.global.validation.validator;

import com.team10.backend.global.validation.annotation.ValidNumberRange;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

public class NumberRangeValidator implements ConstraintValidator<ValidNumberRange, Object> {

    private String minFieldName;
    private String maxFieldName;

    @Override
    public void initialize(ValidNumberRange constraintAnnotation) {
        // ValidDateRange 애노테이션의 사용처에 명시된 min, max 필드명을 문자열로 가져온다
        this.minFieldName = constraintAnnotation.min();
        this.maxFieldName = constraintAnnotation.max();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        // min, max와 동일한 이름을 가진 필드 객체를 가져온다
        Number minValue = (Number) PropertyValueReader.read(value, minFieldName);
        Number maxValue = (Number) PropertyValueReader.read(value, maxFieldName);

        if (minValue == null || maxValue == null) {
            return true;
        }

        // Comparable을 통한 비교 수행
        boolean valid = toBigDecimal(minValue).compareTo(toBigDecimal(maxValue)) <= 0;
        if (!valid) {
            addViolation(context);
        }
        return valid;
    }

    private BigDecimal toBigDecimal(Number value) {
        return new BigDecimal(value.toString());
    }

    // 검증 실패 시 실패 필드에 에러 메세지 연결 및 에러 등록
    private void addViolation(ConstraintValidatorContext context) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(minFieldName)
                .addConstraintViolation();
    }
}
