package com.semple.aigc.canvas.common.web.config;

import com.semple.aigc.canvas.common.web.interceptor.UserContextInterceptor;
import com.semple.aigc.canvas.common.web.interceptor.TraceIdInterceptor;
import com.semple.aigc.canvas.common.web.interceptor.WebLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 公共配置。
 *
 * @author zengzhewen
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebMvcConfig implements WebMvcConfigurer {

    private final TraceIdInterceptor traceIdInterceptor;
    private final UserContextInterceptor userContextInterceptor;
    private final WebLogInterceptor webLogInterceptor;

    /**
     * 构造 Web MVC 公共配置。
     *
     * @param traceIdInterceptor     链路 ID 拦截器
     * @param userContextInterceptor 用户上下文拦截器
     * @param webLogInterceptor      Web 日志拦截器
     */
    public WebMvcConfig(TraceIdInterceptor traceIdInterceptor, UserContextInterceptor userContextInterceptor,
                        WebLogInterceptor webLogInterceptor) {
        this.traceIdInterceptor = traceIdInterceptor;
        this.userContextInterceptor = userContextInterceptor;
        this.webLogInterceptor = webLogInterceptor;
    }

    /**
     * 注册公共请求拦截器。
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(traceIdInterceptor).addPathPatterns("/**");
        registry.addInterceptor(webLogInterceptor).addPathPatterns("/**");
        registry.addInterceptor(userContextInterceptor).addPathPatterns("/**");
    }
}
