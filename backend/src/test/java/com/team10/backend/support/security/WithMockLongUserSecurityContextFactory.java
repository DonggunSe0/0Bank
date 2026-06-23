package com.team10.backend.support.security;

import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithMockLongUserSecurityContextFactory
        implements WithSecurityContextFactory<WithMockLongUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockLongUser annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(annotation.userId(), null, List.of());
        context.setAuthentication(authentication);
        return context;
    }
}
