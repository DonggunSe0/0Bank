package com.team10.backend.support.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.security.test.context.support.WithSecurityContext;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockLongUserSecurityContextFactory.class)
public @interface WithMockLongUser {

    long userId() default 1L;
}
