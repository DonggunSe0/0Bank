package com.team10.backend.global.idempotency.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

// OncePerRequestFilter: HTTP 요청마다 한 번씩 실행되는 필터
// Spring의 Filter Chain에 자동으로 들어가서 요청이 컨트롤러/AOP에 도달하기 전에 실행
// HttpServletRequest의 body input stream은 원래 한 번 읽으면 다시 읽기 어렵다.
// 그래서 멱등성 AOP에서 request body를 hash에 포함하려면, 요청 body를 캐싱할 수 있는 wrapper가 필요하다.
// ContentCachingRequestWrapper가 body를 “미리 읽어두는” 건 아니고, downstream에서 body가 읽히면 그 내용을 캐시에 저장하는 구조
@Component
public class RequestCachingFilter extends OncePerRequestFilter {

    // method가 POST / PUT / PATCH 이고 Content-Type이 application/json인 요청만 wrapping
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isJsonWriteRequest(request)) {
            ContentCachingRequestWrapper wrappedRequest =
                    new ContentCachingRequestWrapper(request, 1024 * 1024); // request body를 최대 1MB까지 캐싱

            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isJsonWriteRequest(HttpServletRequest request) {
        return isWriteMethod(request.getMethod()) && isJsonContentType(request.getContentType());
    }

    private boolean isWriteMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    private boolean isJsonContentType(String contentType) {
        return contentType != null
                && contentType.startsWith(MediaType.APPLICATION_JSON_VALUE); // 실제 요청 Ex. application/json;charset=UTF-8
    }

}
