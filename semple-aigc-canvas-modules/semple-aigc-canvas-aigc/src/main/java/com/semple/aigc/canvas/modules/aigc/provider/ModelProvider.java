package com.semple.aigc.canvas.modules.aigc.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;

import java.util.List;
import java.util.Map;

/**
 * 模型供应商统一适配接口。业务服务只依赖该协议，不直接依赖具体厂商 SDK。
 */
public interface ModelProvider {
    /**
     * 返回与模型定义 adapter_code 对应的唯一编码。
     */
    String adapterCode();

    /**
     * 提交生成任务；同步供应商可直接返回 SUCCEEDED。
     */
    ProviderResult submit(ModelDefinition model, GenerationRequest request);

    /**
     * 查询异步供应商任务状态。
     */
    ProviderResult poll(ModelDefinition model, String providerRequestId);

    /**
     * 尝试取消供应商任务；不支持取消的供应商可安全返回。
     */
    void cancel(ModelDefinition model, String providerRequestId);

    /**
     * 标准化模型生成输入。outputCount 对文本、图片、视频和音频统一表示期望结果数量。
     */
    record GenerationRequest(String prompt, int outputCount, JsonNode config) {
    }

    /**
     * 供应商输出的标准化资源。
     */
    record ProviderAsset(String url, String textContent, String mimeType, Integer assetType,
                         Integer width, Integer height, Long durationMs) {
        /**
         * 兼容现有图片适配器的简化构造方式。
         */
        public ProviderAsset(String url, String mimeType, Integer width, Integer height) {
            this(url, null, mimeType, 2, width, height, null);
        }

        /**
         * 创建文本类型的标准化资产。
         */
        public static ProviderAsset text(String content) {
            return new ProviderAsset(null, content, "text/plain", 1, null, null, null);
        }

        /**
         * 创建图片、视频或音频类型的标准化媒体资产。
         */
        public static ProviderAsset media(String url, String mimeType, int assetType,
                                          Integer width, Integer height, Long durationMs) {
            return new ProviderAsset(url, null, mimeType, assetType, width, height, durationMs);
        }
    }

    /**
     * 供应商任务状态。
     */
    enum ProviderStatus {
        PROCESSING, SUCCEEDED, FAILED, CANCELLED
    }

    /**
     * 标准化供应商响应，rawPayload 中不得包含密钥。
     */
    record ProviderResult(ProviderStatus status, String providerRequestId,
                          List<ProviderAsset> assets, Map<String, Object> usage,
                          String rawPayload, String errorCode, String errorMessage) {
        /**
         * 规范化空资产列表和空用量，避免上层状态处理出现空指针。
         */
        public ProviderResult {
            assets = assets == null ? List.of() : List.copyOf(assets);
            usage = usage == null ? Map.of() : Map.copyOf(usage);
        }
    }
}
