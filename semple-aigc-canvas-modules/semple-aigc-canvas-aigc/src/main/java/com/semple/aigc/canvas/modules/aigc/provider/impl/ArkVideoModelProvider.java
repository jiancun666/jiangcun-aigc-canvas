package com.semple.aigc.canvas.modules.aigc.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;
import com.semple.aigc.canvas.modules.aigc.provider.CredentialResolver;
import com.semple.aigc.canvas.modules.aigc.provider.ModelProvider;
import com.semple.aigc.canvas.modules.aigc.provider.http.JsonHttpGateway;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 火山方舟 Seedance 原厂视频生成适配器。
 */
@Component
@RequiredArgsConstructor
public class ArkVideoModelProvider implements ModelProvider {
    private static final String TASK_PATH = "/contents/generations/tasks";
    private static final List<String> PASSTHROUGH_FIELDS = List.of(
            "ratio", "resolution", "duration", "frames", "seed", "camera_fixed", "watermark",
            "service_tier", "draft", "priority", "return_last_frame", "output_format", "generate_audio");

    private final CredentialResolver credentialResolver;
    private final ObjectMapper objectMapper;
    private final JsonHttpGateway gateway;

    /**
     * 返回火山方舟视频适配器编码。
     */
    @Override
    public String adapterCode() {
        return "ARK_VIDEO";
    }

    /**
     * 创建 Seedance 异步生成任务并返回供应商任务 ID。
     */
    @Override
    public ProviderResult submit(ModelDefinition model, GenerationRequest request) {
        try {
            ObjectNode config = mergedConfig(model, request.config());
            String providerModelName = config.path("providerModelName").asText();
            if (providerModelName.isBlank()) {
                return failed("MODEL_ID_MISSING", "Seedance 原厂模型 ID 未配置", null, null);
            }
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", providerModelName);
            ArrayNode content = body.putArray("content");
            content.addObject().put("type", "text").put("text", request.prompt());
            addReferences(content, config);
            // 只透传白名单参数，避免把画布内部配置或未知字段发送给原厂接口。
            for (String field : PASSTHROUGH_FIELDS) {
                if (config.has(field)) body.set(field, config.get(field));
            }
            if (!body.has("generate_audio")) {
                body.put("generate_audio", model.getModelType() == 4);
            }
            JsonNode response = gateway.exchange("POST", model.getEndpointUrl(), TASK_PATH,
                    headers(model), body, model.getTimeoutSeconds());
            String taskId = response.path("id").asText();
            if (taskId.isBlank()) {
                return failed("ARK_MISSING_TASK_ID", "火山方舟未返回视频任务 ID", response.toString(), null);
            }
            return new ProviderResult(ProviderStatus.PROCESSING, taskId, List.of(), Map.of(),
                    response.toString(), null, null);
        } catch (Exception e) {
            return failed(e.getClass().getSimpleName(), safeMessage(e), null, null);
        }
    }

    /**
     * 查询 Seedance 任务状态，并在成功时转换视频或内嵌音频资产。
     */
    @Override
    public ProviderResult poll(ModelDefinition model, String providerRequestId) {
        try {
            JsonNode response = gateway.exchange("GET", model.getEndpointUrl(),
                    TASK_PATH + "/" + providerRequestId, headers(model), null, model.getTimeoutSeconds());
            String status = response.path("status").asText("").toLowerCase(Locale.ROOT);
            if (status.equals("failed")) {
                String message = response.path("error").path("message")
                        .asText(response.path("message").asText("Seedance 任务失败"));
                return failed("ARK_VIDEO_TASK_FAILED", message, response.toString(), providerRequestId);
            }
            if (status.equals("cancelled") || status.equals("canceled")) {
                return new ProviderResult(ProviderStatus.CANCELLED, providerRequestId,
                        List.of(), usage(response), response.toString(), null, null);
            }
            if (!status.equals("succeeded")) {
                return new ProviderResult(ProviderStatus.PROCESSING, providerRequestId,
                        List.of(), usage(response), response.toString(), null, null);
            }
            String url = response.path("content").path("video_url").asText();
            if (url.isBlank()) {
                return failed("EMPTY_PROVIDER_RESULT", "Seedance 成功响应中没有 video_url",
                        response.toString(), providerRequestId);
            }
            int assetType = model.getModelType() == 4 ? 4 : 3;
            String mimeType = model.getModelType() == 4 ? "audio/mp4" : "video/mp4";
            Long durationMs = response.has("duration") ? response.get("duration").asLong() * 1000L : null;
            ProviderAsset asset = ProviderAsset.media(url, mimeType, assetType, null, null, durationMs);
            return new ProviderResult(ProviderStatus.SUCCEEDED, providerRequestId,
                    List.of(asset), usage(response), response.toString(), null, null);
        } catch (Exception e) {
            return failed(e.getClass().getSimpleName(), safeMessage(e), null, providerRequestId);
        }
    }

    /**
     * 调用火山方舟删除任务接口尝试取消异步生成。
     */
    @Override
    public void cancel(ModelDefinition model, String providerRequestId) {
        try {
            gateway.exchange("DELETE", model.getEndpointUrl(), TASK_PATH + "/" + providerRequestId,
                    headers(model), null, model.getTimeoutSeconds());
        } catch (Exception e) {
            throw new IllegalStateException("火山方舟取消视频任务失败", e);
        }
    }

    /**
     * 把图片、视频和音频参考素材添加到 Seedance 多模态内容数组。
     */
    private void addReferences(ArrayNode content, ObjectNode config) {
        JsonNode images = config.path("referenceImages");
        if (images.isArray()) {
            for (JsonNode image : images) {
                content.addObject().put("type", "image_url")
                        .putObject("image_url").put("url", image.asText());
                ((ObjectNode) content.get(content.size() - 1)).put("role", "reference_image");
            }
        }
        addReference(content, config, "referenceVideo", "video_url", "reference_video");
        addReference(content, config, "referenceAudio", "audio_url", "reference_audio");
    }

    /**
     * 添加单个非空参考媒体，并标记其原厂角色。
     */
    private void addReference(ArrayNode content, ObjectNode config, String key, String type, String role) {
        if (!config.hasNonNull(key) || config.get(key).asText().isBlank()) return;
        ObjectNode item = content.addObject();
        item.put("type", type).putObject(type).put("url", config.get(key).asText());
        item.put("role", role);
    }

    /**
     * 合并模型默认配置和单次请求配置。
     */
    private ObjectNode mergedConfig(ModelDefinition model, JsonNode requestConfig) throws Exception {
        ObjectNode merged = objectMapper.createObjectNode();
        if (model.getDefaultRequestConfig() != null && !model.getDefaultRequestConfig().isBlank()) {
            JsonNode defaults = objectMapper.readTree(model.getDefaultRequestConfig());
            if (defaults.isObject()) merged.setAll((ObjectNode) defaults);
        }
        if (requestConfig != null && requestConfig.isObject()) merged.setAll((ObjectNode) requestConfig);
        return merged;
    }

    /**
     * 使用模型凭证引用构造方舟 Bearer 认证头。
     */
    private Map<String, String> headers(ModelDefinition model) {
        return Map.of("Authorization", "Bearer " + credentialResolver.resolve(model.getCredentialRef()));
    }

    /**
     * 提取令牌用量，并在成功时记录一个实际输出。
     */
    private Map<String, Object> usage(JsonNode response) {
        Map<String, Object> usage = new LinkedHashMap<>();
        JsonNode node = response.path("usage");
        if (node.has("completion_tokens")) usage.put("completionTokens", node.get("completion_tokens").asLong());
        if (node.has("total_tokens")) usage.put("totalTokens", node.get("total_tokens").asLong());
        if (response.path("status").asText().equalsIgnoreCase("succeeded")) usage.put("outputCount", 1);
        return usage;
    }

    /**
     * 构造保留供应商任务 ID 的标准化失败结果。
     */
    private ProviderResult failed(String code, String message, String raw, String taskId) {
        return new ProviderResult(ProviderStatus.FAILED, taskId, List.of(), Map.of(), raw, code, message);
    }

    /**
     * 提取并截断适合审计的异常信息。
     */
    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? "Seedance 原厂接口调用失败" : message.substring(0, Math.min(message.length(), 900));
    }
}
