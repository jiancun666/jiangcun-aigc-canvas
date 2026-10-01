package com.semple.aigc.canvas.common.feign.config;

import com.semple.aigc.canvas.common.feign.interceptor.InnerAuthRequestInterceptor;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign support configuration.
 *
 * @author aofaming
 */
@Configuration
public class FeignSupportConfig {

    @Bean
    @ConditionalOnMissingBean(name = "innerAuthRequestInterceptor")
    public RequestInterceptor innerAuthRequestInterceptor() {
        return new InnerAuthRequestInterceptor();
    }
}
