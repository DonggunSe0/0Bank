package com.team10.backend.global.validation.validator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;


/**
 * Bean Validation 커스텀 Validator에서 필드 값을 읽기 위한 유틸 클래스.
 *
 * @ValidDateRange(start = "startDate", end = "endDate") 와 같이 애노테이션에 문자열로 전달된 필드명을 기반으로 실제 값을 조회한다.
 * <p>
 * 조회 순서: 1. 레코드 accessor 메서드 (startDate()) 2. Java Bean getter (getStartDate()) 3. boolean getter (isEnabled()) 4. 필드
 * 직접 접근
 */
final class PropertyValueReader {

    private PropertyValueReader() {
    }

    static Object read(Object target, String propertyName) {
        // 검증 대상 객체의 실제 타입을 가져온다.
        Class<?> targetType = target.getClass();

        // Record의 accessor 메서드(startDate())를 우선 탐색한다.
        Method accessor = findMethod(targetType, propertyName);
        // 일반 Java Bean getter(getStartDate())를 탐색한다.
        if (accessor == null) {
            accessor = findMethod(targetType, getterName("get", propertyName));
        }
        // boolean 프로퍼티의 getter(isEnabled())를 탐색한다.
        if (accessor == null) {
            accessor = findMethod(targetType, getterName("is", propertyName));
        }
        // 접근 가능한 메서드를 찾았다면 해당 메서드를 호출하여 값을 조회한다.
        if (accessor != null) {
            return invoke(target, accessor);
        }

        // 메서드를 찾지 못한 경우 필드에 직접 접근을 시도한다.
        Field field = findField(targetType, propertyName);
        if (field != null) {
            return get(target, field);
        }

        // 메서드와 필드 모두 찾지 못한 경우 예외를 발생시킨다.
        throw new IllegalArgumentException("검증 대상 필드를 찾을 수 없습니다: " + propertyName);
    }

    // 이름에 해당하는 public 메서드를 조회한다.
    private static Method findMethod(Class<?> targetType, String methodName) {
        try {
            Method method = targetType.getMethod(methodName);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    // 현재 클래스부터 부모 클래스까지 순회하며 필드를 조회한다.
    private static Field findField(Class<?> targetType, String fieldName) {
        Class<?> current = targetType;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    // 리플렉션을 통해 메서드를 실행하고 결과값을 반환한다.
    private static Object invoke(Object target, Method method) {
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("검증 대상 필드 값을 읽을 수 없습니다: " + method.getName(), e);
        }
    }

    // 리플렉션을 통해 필드 값을 직접 읽어온다.
    private static Object get(Object target, Field field) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("검증 대상 필드 값을 읽을 수 없습니다: " + field.getName(), e);
        }
    }

    // 프로퍼티명으로부터 getter 이름을 생성한다.
    // 예: startDate -> getStartDate
    private static String getterName(String prefix, String propertyName) {
        if (propertyName == null || propertyName.isBlank()) {
            return prefix;
        }
        return prefix + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }
}
