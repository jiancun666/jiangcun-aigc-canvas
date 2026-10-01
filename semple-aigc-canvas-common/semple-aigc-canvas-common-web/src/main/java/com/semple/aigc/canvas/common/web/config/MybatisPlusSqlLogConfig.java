package com.semple.aigc.canvas.common.web.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus SQL 日志开关配置.
 *
 * @author feilong
 * @date 2026-06-01
 * @desc 根据配置切换 MyBatis-Plus SQL 日志实现
 */
@Configuration
@ConditionalOnClass(ConfigurationCustomizer.class)
public class MybatisPlusSqlLogConfig {

    /**
     * 注册 MyBatis-Plus 分页拦截器，保证 Page 返回 total 和 pages 元数据。
     *
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 自定义 MyBatis 日志实现.
     *
     * @param properties SQL 日志配置
     * @return MyBatis-Plus 配置定制器
     */
    @Bean
    public ConfigurationCustomizer sqlLogConfigurationCustomizer(MybatisPlusSqlLogProperties properties) {
        return configuration -> {
            if (properties.isSqlLogEnabled()) {
                configuration.setLogImpl(Slf4jImpl.class);
            } else {
                configuration.setLogImpl(NoLoggingImpl.class);
            }
        };
    }
}
