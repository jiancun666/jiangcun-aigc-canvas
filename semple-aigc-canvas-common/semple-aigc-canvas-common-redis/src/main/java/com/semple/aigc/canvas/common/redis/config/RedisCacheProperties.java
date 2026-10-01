package com.semple.aigc.canvas.common.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis cache properties.
 *
 * @author aofaming
 */
@ConfigurationProperties(prefix = "semple-aigc-canvas.redis")
public class RedisCacheProperties {

    private String keyPrefix = "semple-aigc-canvas:";

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
}
