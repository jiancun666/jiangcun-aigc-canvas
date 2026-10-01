package com.semple.aigc.canvas.modules.aigc.provider.impl;

import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisOutput;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.semple.aigc.canvas.modules.aigc.provider.CredentialResolver;
import com.semple.aigc.canvas.modules.aigc.provider.ModelProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 阿里百炼 DashScope 异步图片生成适配器。
 */
@Component
@RequiredArgsConstructor
public class DashScopeImageModelProvider implements ModelProvider {
    private final CredentialResolver credentialResolver;
    private final ObjectMapper objectMapper;

    /**
     * 返回阿里百炼图片适配器编码。
     */
    @Override
    public String adapterCode() {
        return "DASHSCOPE_IMAGE";
    }

    /**
     * 创建百炼异步图片生成任务。
     */
    @Override
    public ProviderResult submit(ModelDefinition model, GenerationRequest request) {
        try {
            String apiKey = credentialResolver.resolve(model.getCredentialRef());
            ImageSynthesisParam param = ImageSynthesisParam.builder()
                    .apiKey(apiKey)
                    .model(model.getModelCode())
                    .prompt(request.prompt())
                    .n(request.outputCount())
                    .size(text(request.config(), "size", "1024*1024"))
                    .watermark(bool(request.config(), "watermark", false))
                    .build();
            ImageSynthesisResult result = client(model).asyncCall(param);
            return convert(result);
        } catch (Exception e) {
            return failed(e.getClass().getSimpleName(), safeMessage(e), null);
        }
    }

    /**
     * 查询百炼图片任务并转换状态、图片资产及用量。
     */
    @Override
    public ProviderResult poll(ModelDefinition model, String providerRequestId) {
        try {
            ImageSynthesisResult result = client(model).fetch(providerRequestId,
                    credentialResolver.resolve(model.getCredentialRef()));
            return convert(result);
        } catch (Exception e) {
            return failed(e.getClass().getSimpleName(), safeMessage(e), null);
        }
    }

    /**
     * 调用百炼 SDK 取消指定异步图片任务。
     */
    @Override
    public void cancel(ModelDefinition model, String providerRequestId) {
        try {
            client(model).cancel(providerRequestId, credentialResolver.resolve(model.getCredentialRef()));
        } catch (Exception e) {
            // 上层始终完成本地取消和积分释放，同时把供应商取消失败写入审计日志。
            throw new IllegalStateException("DashScope 取消任务失败", e);
        }
    }

    /**
     * 根据模型定义创建使用默认或自定义端点的图片客户端。
     */
    private ImageSynthesis client(ModelDefinition model) {
        return model.getEndpointUrl() == null || model.getEndpointUrl().isBlank()
                ? new ImageSynthesis() : new ImageSynthesis("text2image", model.getEndpointUrl());
    }

    /**
     * 将百炼 SDK 返回值转换为统一供应商结果。
     */
    private ProviderResult convert(ImageSynthesisResult result) {
        ImageSynthesisOutput output = result.getOutput();
        if (output == null) {
            return failed(result.getCode(), result.getMessage(), json(result));
        }
        String status = output.getTaskStatus() == null ? "" : output.getTaskStatus().toUpperCase(Locale.ROOT);
        ProviderStatus providerStatus = switch (status) {
            case "SUCCEEDED" -> ProviderStatus.SUCCEEDED;
            case "FAILED" -> ProviderStatus.FAILED;
            case "CANCELED", "CANCELLED" -> ProviderStatus.CANCELLED;
            default -> ProviderStatus.PROCESSING;
        };
        List<ProviderAsset> assets = output.getResults() == null ? List.of()
                : output.getResults().stream().map(map -> map.get("url")).filter(java.util.Objects::nonNull)
                .map(url -> new ProviderAsset(url, "image/*", null, null)).toList();
        Map<String, Object> usage = new LinkedHashMap<>();
        int imageCount = result.getUsage() != null && result.getUsage().getImageCount() != null
                ? result.getUsage().getImageCount() : assets.size();
        usage.put("imageCount", imageCount);
        String errorCode = providerStatus == ProviderStatus.FAILED ? first(output.getCode(), result.getCode()) : null;
        String errorMessage = providerStatus == ProviderStatus.FAILED ? first(output.getMessage(), result.getMessage()) : null;
        return new ProviderResult(providerStatus, output.getTaskId(), assets, usage,
                json(result), errorCode, errorMessage);
    }

    /**
     * 构造标准化失败结果。
     */
    private ProviderResult failed(String code, String message, String raw) {
        return new ProviderResult(ProviderStatus.FAILED, null, List.of(), Map.of(), raw, code, message);
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
     * 序列化 SDK 响应用于调用审计。
     */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 返回首个非空字符串，否则使用第二个值。
     */
    private String first(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    /**
     * 提取并截断适合审计的异常信息。
     */
    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? "DashScope 图片生成失败" : message.substring(0, Math.min(message.length(), 900));
    }
}
