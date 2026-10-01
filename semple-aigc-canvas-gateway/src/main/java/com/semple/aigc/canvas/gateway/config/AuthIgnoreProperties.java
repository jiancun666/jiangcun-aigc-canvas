package com.semple.aigc.canvas.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 免认证URL配置属性.
 *
 * @author feilong
 * @date 2026-05-27
 * @desc 从Nacos动态读取不需要认证的URL白名单配置
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "security.ignore")
public class AuthIgnoreProperties {

    /**
     * 免认证URL列表
     */
    private List<String> urls = new ArrayList<>();
}
