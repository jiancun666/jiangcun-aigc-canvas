package com.semple.aigc.canvas.common.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Web 请求响应日志配置.
 *
 * @author feilong
 * @date 2026-06-01
 * @desc 控制全局入参和出参日志输出
 */
@Data
@Component
@ConfigurationProperties(prefix = "semple-aigc-canvas.web.log")
public class WebLogProperties {

    /**
     * 是否开启请求响应日志
     */
    private Boolean enabled = false;

    /**
     * 是否输出入参
     */
    private Boolean requestEnabled = true;

    /**
     * 是否输出出参
     */
    private Boolean responseEnabled = true;

    /**
     * 是否输出请求头
     */
    private Boolean includeHeaders = false;

    /**
     * 单条参数最大输出长度
     */
    private Integer maxPayloadLength = 2000;

    /**
     * 不输出日志的路径
     */
    private List<String> excludePaths = new ArrayList<>(List.of("/actuator/**"));

    /**
     * 需要脱敏的字段名
     */
    private List<String> sensitiveFields = new ArrayList<>(List.of(
            "password", "oldPassword", "newPassword", "confirmPassword",
            "token", "accessToken", "refreshToken", "authorization",
            "secret", "secretKey", "accessKey", "captcha"
    ));

    public boolean isLogEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean isRequestLogEnabled() {
        return isLogEnabled() && Boolean.TRUE.equals(requestEnabled);
    }

    public boolean isResponseLogEnabled() {
        return isLogEnabled() && Boolean.TRUE.equals(responseEnabled);
    }

    public int getPayloadLimit() {
        if (maxPayloadLength == null || maxPayloadLength <= 0) {
            return 2000;
        }
        return maxPayloadLength;
    }
}
