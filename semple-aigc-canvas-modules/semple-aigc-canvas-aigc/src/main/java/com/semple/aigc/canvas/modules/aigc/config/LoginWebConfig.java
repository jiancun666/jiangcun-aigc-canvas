package com.semple.aigc.canvas.modules.aigc.config;

import com.semple.aigc.canvas.modules.aigc.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册AIGC服务的JWT认证拦截器。 */
@Configuration
@Order(Ordered.LOWEST_PRECEDENCE)
public class LoginWebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    public LoginWebConfig(LoginInterceptor loginInterceptor) {
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/email/captcha",
                        "/auth/login/email",
                        "/actuator/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml",
                        "/error"
                );
    }
}
