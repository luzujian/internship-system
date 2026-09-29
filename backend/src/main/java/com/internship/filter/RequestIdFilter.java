package com.internship.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求链路ID过滤器（P1 新增）
 * 每个请求生成唯一 requestId，写入 MDC（供 Logback 输出）与 request attribute（供审计日志读取）
 * 必须在 TokenFilter 之前执行，确保 401/403 等安全事件也能记录 requestId
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_ATTRIBUTE = "auditRequestId";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader("X-Request-Id", requestId);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    /**
     * 获取当前请求的 requestId（无请求上下文时返回 null）
     */
    public static String currentRequestId() {
        String fromMdc = MDC.get(REQUEST_ID_MDC_KEY);
        if (fromMdc != null) {
            return fromMdc;
        }
        return null;
    }
}
