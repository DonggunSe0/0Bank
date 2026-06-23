package com.team10.backend.global.validation.validator;

import com.team10.backend.global.validation.annotation.ValidDateRange;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DateRangeValidator implements ConstraintValidator<ValidDateRange, Object> {

    private String startFieldName;
    private String endFieldName;

    @Override
    public void initialize(ValidDateRange constraintAnnotation) {
        // ValidDateRange 애노테이션의 사용처에 명시된 start, end 필드명을 문자열로 가져온다
        this.startFieldName = constraintAnnotation.start();
        this.endFieldName = constraintAnnotation.end();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        // start, end와 동일한 이름을 가진 필드 객체를 가져온다
        Object startDate = PropertyValueReader.read(value, startFieldName);
        Object endDate = PropertyValueReader.read(value, endFieldName);

        if (startDate == null || endDate == null) {
            return true;
        }

        boolean valid = isNotAfter(startDate, endDate);
        if (!valid) {
            addViolation(context);
        }
        return valid;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean isNotAfter(Object startDate, Object endDate) {
        if (!(startDate instanceof Comparable comparable)) {
            return false;
        }

        try {
            // Comparable을 통한 비교 수행
            return comparable.compareTo(endDate) <= 0;
        } catch (ClassCastException e) {
            return false;
        }
    }

    // 검증 실패 시 실패 필드에 에러 메세지 연결 및 에러 등록
    private void addViolation(ConstraintValidatorContext context) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(startFieldName)
                .addConstraintViolation();
    }
}
