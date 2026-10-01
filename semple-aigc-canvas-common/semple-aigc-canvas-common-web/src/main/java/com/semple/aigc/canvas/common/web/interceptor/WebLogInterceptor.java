package com.semple.aigc.canvas.common.web.interceptor;

import com.semple.aigc.canvas.common.web.config.WebLogProperties;
import com.semple.aigc.canvas.common.web.log.WebLogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web 请求日志拦截器.
 *
 * @author feilong
 * @date 2026-06-01
 * @desc 记录请求基本信息、查询参数和请求耗时
 */
@Slf4j
@Component
public class WebLogInterceptor implements HandlerInterceptor {

    public static final String START_TIME_ATTRIBUTE = "zhiHubWebLogStartTime";
    public static final String SKIP_LOG_ATTRIBUTE = "zhiHubWebLogSkip";

    private final WebLogProperties properties;
    private final WebLogSanitizer sanitizer;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public WebLogInterceptor(WebLogProperties properties, WebLogSanitizer sanitizer) {
        this.properties = properties;
        this.sanitizer = sanitizer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isLogEnabled() || isExcluded(request.getRequestURI())) {
            request.setAttribute(SKIP_LOG_ATTRIBUTE, true);
            return true;
        }

        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());

        if (properties.isRequestLogEnabled()) {
            Map<String, Object> params = sanitizer.maskParameterMap(request.getParameterMap());
            if (Boolean.TRUE.equals(properties.getIncludeHeaders())) {
                log.info("请求入参 method={}, uri={}, headers={}, params={}",
                        request.getMethod(), request.getRequestURI(), sanitizer.format(getHeaders(request)), sanitizer.format(params));
            } else {
                log.info("请求入参 method={}, uri={}, params={}",
                        request.getMethod(), request.getRequestURI(), sanitizer.format(params));
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (Boolean.TRUE.equals(request.getAttribute(SKIP_LOG_ATTRIBUTE))) {
            return;
        }

        Object startTime = request.getAttribute(START_TIME_ATTRIBUTE);
        long cost = startTime instanceof Long ? System.currentTimeMillis() - (Long) startTime : -1L;
        if (ex == null) {
            log.info("请求完成 method={}, uri={}, status={}, cost={}ms",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), cost);
        } else {
            log.info("请求异常 method={}, uri={}, status={}, cost={}ms, exception={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), cost, ex.getClass().getSimpleName());
        }
    }

    private boolean isExcluded(String uri) {
        if (properties.getExcludePaths() == null || properties.getExcludePaths().isEmpty()) {
            return false;
        }
        return properties.getExcludePaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }

    private Map<String, String> getHeaders(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return Collections.emptyMap();
        }

        Map<String, String> headers = new LinkedHashMap<>();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        return headers;
    }
}
