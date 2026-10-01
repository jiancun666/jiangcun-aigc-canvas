package com.semple.aigc.canvas.common.web.advice;

import com.semple.aigc.canvas.common.web.config.WebLogProperties;
import com.semple.aigc.canvas.common.web.interceptor.WebLogInterceptor;
import com.semple.aigc.canvas.common.web.log.WebLogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

/**
 * 请求体日志增强.
 *
 * @author feilong
 * @date 2026-06-01
 * @desc 输出 JSON 请求体等由 HttpMessageConverter 解析后的入参
 */
@Slf4j
@Component
@ControllerAdvice
public class WebRequestBodyLogAdvice extends RequestBodyAdviceAdapter {

    private final WebLogProperties properties;
    private final WebLogSanitizer sanitizer;

    public WebRequestBodyLogAdvice(WebLogProperties properties, WebLogSanitizer sanitizer) {
        this.properties = properties;
        this.sanitizer = sanitizer;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return properties.isRequestLogEnabled();
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (inputMessage instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest request = servletRequest.getServletRequest();
            if (!Boolean.TRUE.equals(request.getAttribute(WebLogInterceptor.SKIP_LOG_ATTRIBUTE))) {
                log.info("请求体 method={}, uri={}, body={}",
                        request.getMethod(), request.getRequestURI(), sanitizer.format(body));
            }
        } else {
            log.info("请求体 body={}", sanitizer.format(body));
        }
        return body;
    }
}
