package com.semple.aigc.canvas.modules.aigc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/** SMTP配置，从Nacos加载。 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "mail")
public class EmailProperties {
    private String host;
    private Integer port = 465;
    private String username;
    private String password;
    private String from;
    private String fromName = "semple-aigc-canvas";
    private Boolean sslEnabled = true;
}
