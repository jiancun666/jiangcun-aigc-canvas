package com.semple.aigc.canvas.common.web.advice;

import com.semple.aigc.canvas.common.web.config.WebLogProperties;
import com.semple.aigc.canvas.common.web.interceptor.WebLogInterceptor;
import com.semple.aigc.canvas.common.web.log.WebLogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 响应体日志增强.
 *
 * @author feilong
 * @date 2026-06-01
 * @desc 输出 Controller 返回的出参
 */
@Slf4j
@Component
@ControllerAdvice
public class WebResponseBodyLogAdvice implements ResponseBodyAdvice<Object> {

    private final WebLogProperties properties;
    private final WebLogSanitizer sanitizer;

    public WebResponseBodyLogAdvice(WebLogProperties properties, WebLogSanitizer sanitizer) {
        this.properties = properties;
        this.sanitizer = sanitizer;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return properties.isResponseLogEnabled();
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (shouldSkipBody(body)) {
            return body;
        }

        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
            if (!Boolean.TRUE.equals(httpServletRequest.getAttribute(WebLogInterceptor.SKIP_LOG_ATTRIBUTE))) {
                log.info("响应出参 method={}, uri={}, body={}",
                        httpServletRequest.getMethod(), httpServletRequest.getRequestURI(), sanitizer.format(body));
            }
        } else {
            log.info("响应出参 body={}", sanitizer.format(body));
        }
        return body;
    }

    private boolean shouldSkipBody(Object body) {
        return body == null
                || body instanceof byte[]
                || body instanceof Resource
                || body instanceof StreamingResponseBody
                || body instanceof SseEmitter;
    }
}
