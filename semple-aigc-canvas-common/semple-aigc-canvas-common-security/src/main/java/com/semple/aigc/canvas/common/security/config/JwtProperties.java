package com.semple.aigc.canvas.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT configuration properties.
 *
 * @author aofaming
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT secret key.
     */
    private String secret = "semple-aigc-canvas-jwt-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm";

    /**
     * Token 有效期，单位：分钟，默认 7 天。
     */
    private long expirationMinutes = 10080L;

    /**
     * Token 过期后返回 601 的识别窗口，单位：分钟，默认 30 天。
     */
    private long expiredCodeWindowMinutes = 43200L;

    /**
     * Token header name.
     */
    private String header = "Authorization";

    /**
     * Token prefix.
     */
    private String tokenPrefix = "Bearer ";
}
