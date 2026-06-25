package com.team10.backend.global.idempotency.annotation;

import com.team10.backend.global.idempotency.type.IdempotencyOperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    IdempotencyOperationType operationType();
}
