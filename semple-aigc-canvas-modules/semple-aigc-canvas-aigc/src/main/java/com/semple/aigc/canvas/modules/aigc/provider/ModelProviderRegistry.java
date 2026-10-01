package com.semple.aigc.canvas.modules.aigc.provider;

import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 按 adapter_code 路由到具体模型供应商实现。
 */
@Component
public class ModelProviderRegistry {
    private final Map<String, ModelProvider> providers = new HashMap<>();

    /**
     * 收集 Spring 容器中的全部适配器，并按标准化编码建立路由表。
     */
    public ModelProviderRegistry(List<ModelProvider> providerList) {
        providerList.forEach(provider -> providers.put(normalize(provider.adapterCode()), provider));
    }

    /**
     * 获取模型适配器，不存在时返回明确的配置错误。
     */
    public ModelProvider require(String adapterCode) {
        ModelProvider provider = providers.get(normalize(adapterCode));
        if (provider == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "未注册模型适配器：" + adapterCode);
        }
        return provider;
    }

    /**
     * 忽略适配器编码首尾空白和大小写差异。
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
