package com.semple.aigc.canvas.common.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MyBatis-Plus SQL 日志配置.
 *
 * @author feilong
 * @date 2026-06-01
 * @desc 控制 MyBatis-Plus 是否输出 SQL 日志
 */
@Data
@Component
@ConfigurationProperties(prefix = "semple-aigc-canvas.mybatis-plus")
public class MybatisPlusSqlLogProperties {

    /**
     * 是否开启 SQL 日志
     */
    private Boolean sqlLogEnabled = false;

    public boolean isSqlLogEnabled() {
        return Boolean.TRUE.equals(sqlLogEnabled);
    }
}
