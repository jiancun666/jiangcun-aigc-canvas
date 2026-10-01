package com.semple.aigc.canvas.modules.aigc.provider.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;
import com.semple.aigc.canvas.modules.aigc.provider.CredentialResolver;
import com.semple.aigc.canvas.modules.aigc.provider.ModelProvider;
import com.volcengine.ark.runtime.model.images.generation.GenerateImagesRequest;
import com.volcengine.ark.runtime.model.images.generation.ImagesResponse;
import com.volcengine.ark.runtime.service.ArkService;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 火山方舟同步图片生成适配器。
 */
@Component
@RequiredArgsConstructor
public class ArkImageModelProvider implements ModelProvider {
    private final CredentialResolver credentialResolver;
    private final ObjectMapper objectMapper;

    /**
     * 返回火山方舟图片适配器编码。
     */
    @Override
    public String adapterCode() {
        return "ARK_IMAGE";
    }

    /**
     * 同步调用火山方舟图片生成接口并转换图片资产与用量。
     */
    @Override
    public ProviderResult submit(ModelDefinition model, GenerationRequest request) {
        ArkService.Builder builder = ArkService.builder()
                .apiKey(credentialResolver.resolve(model.getCredentialRef()))
                .timeout(Duration.ofSeconds(model.getTimeoutSeconds()));
        if (model.getEndpointUrl() != null && !model.getEndpointUrl().isBlank()) {
            builder.baseUrl(model.getEndpointUrl());
        }
        ArkService service = builder.build();
        try {
            JsonNode config = mergedConfig(model, request.config());
            String providerModelName = text(config, "providerModelName", null);
            if (providerModelName == null || providerModelName.isBlank()) {
                return failed("MODEL_ID_MISSING", "火山方舟原厂模型 ID 未配置", null);
            }
            String size = text(config, "size", "2048x2048");
            // 强制 URL 响应，使生成结果可直接进入现有资产存储链路。
            GenerateImagesRequest providerRequest = GenerateImagesRequest.builder()
                    .model(providerModelName)
                    .prompt(request.prompt())
                    .size(size)
                    .responseFormat("url")
                    .watermark(bool(config, "watermark", false))
                    .build();
            ImagesResponse response = service.generateImages(providerRequest);
            if (response.getError() != null) {
                return failed(response.getError().getCode(), response.getError().getMessage(), json(response));
            }
            List<ProviderAsset> assets = response.getData() == null ? List.of()
                    : response.getData().stream().filter(image -> image.getUrl() != null)
                    .map(image -> new ProviderAsset(image.getUrl(), "image/*", null, null)).toList();
            Map<String, Object> usage = new LinkedHashMap<>();
            int generated = response.getUsage() != null && response.getUsage().getGeneratedImages() != null
                    ? response.getUsage().getGeneratedImages() : assets.size();
            usage.put("imageCount", generated);
            return new ProviderResult(ProviderStatus.SUCCEEDED, "ark-sync-" + UUID.randomUUID(),
                    assets, usage, json(response), null, null);
        } catch (Exception e) {
            return failed(e.getClass().getSimpleName(), safeMessage(e), null);
        } finally {
            service.shutdownExecutor();
        }
    }

    /**
     * 同步图片接口没有可轮询的供应商任务。
     */
    @Override
    public ProviderResult poll(ModelDefinition model, String providerRequestId) {
        return failed("ARK_SYNC_TASK", "方舟图片适配器为同步调用，不支持轮询", null);
    }

    /**
     * 同步调用完成后无需向供应商发送取消请求。
     */
    @Override
    public void cancel(ModelDefinition model, String providerRequestId) {
        // 同步请求返回后即完成，取消请求只更新本地任务状态。
    }

    /**
     * 构造标准化失败结果。
     */
    private ProviderResult failed(String code, String message, String raw) {
        return new ProviderResult(ProviderStatus.FAILED, null, List.of(), Map.of(), raw, code, message);
    }

    /**
     * 合并模型默认配置和单次请求配置，后者优先。
     */
    private ObjectNode mergedConfig(ModelDefinition model, JsonNode requestConfig) throws JsonProcessingException {
        ObjectNode merged = objectMapper.createObjectNode();
        if (model.getDefaultRequestConfig() != null && !model.getDefaultRequestConfig().isBlank()) {
            JsonNode defaults = objectMapper.readTree(model.getDefaultRequestConfig());
            if (defaults.isObject()) {
                merged.setAll((ObjectNode) defaults);
            }
        }
        if (requestConfig != null && requestConfig.isObject()) {
            merged.setAll((ObjectNode) requestConfig);
        }
        return merged;
    }

    /**
     * 安全读取文本配置，不存在时返回默认值。
     */
    private String text(com.fasterxml.jackson.databind.JsonNode node, String name, String defaultValue) {
        return node != null && node.hasNonNull(name) ? node.get(name).asText() : defaultValue;
    }

    /**
     * 安全读取布尔配置，不存在时返回默认值。
     */
    private boolean bool(com.fasterxml.jackson.databind.JsonNode node, String name, boolean defaultValue) {
        return node != null && node.hasNonNull(name) ? node.get(name).asBoolean() : defaultValue;
    }

    /**
     * 序列化供应商响应用于审计，序列化失败时不影响主结果。
     */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 提取并截断适合写入任务错误字段的异常信息。
     */
    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? "方舟图片生成失败" : message.substring(0, Math.min(message.length(), 900));
    }
}
