package com.semple.aigc.canvas.modules.aigc.provider;

import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 解析模型密钥引用，从 Spring Environment（环境变量、本地配置、Nacos）或 JVM 属性读取密钥。
 */
@Component
public class CredentialResolver {
    private final Environment environment;

    /**
     * 创建凭证解析器并注入 Spring 环境配置。
     */
    public CredentialResolver(Environment environment) {
        this.environment = environment;
    }

    /**
     * 解析 env:NAME 或 sys:NAME 引用，拒绝把明文密钥写入数据库。
     */
    public String resolve(String reference) {
        String value = null;
        if (reference != null && reference.startsWith("env:")) {
            value = environment.getProperty(reference.substring(4));
        } else if (reference != null && reference.startsWith("sys:")) {
            value = System.getProperty(reference.substring(4));
        }
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "模型密钥未配置或密钥引用格式无效");
        }
        return value;
    }

}
