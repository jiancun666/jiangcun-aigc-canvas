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
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 阿里云百炼 OpenAI 兼容 Chat Completions 适配器。
 */
@Component
@RequiredArgsConstructor
public class DashScopeChatModelProvider implements ModelProvider {
    private final CredentialResolver credentialResolver;
    private final ObjectMapper objectMapper;
    private final JsonHttpGateway gateway;

    /**
     * 返回阿里百炼对话适配器编码。
     */
    @Override
    public String adapterCode() {
        return "DASHSCOPE_CHAT";
    }

    /**
     * 同步调用 OpenAI 兼容对话接口并返回文本资产。
     */
    @Override
    public ProviderResult submit(ModelDefinition model, GenerationRequest request) {
        try {
            ObjectNode config = mergedConfig(model, request.config());
            String providerModelName = config.path("providerModelName").asText();
            if (providerModelName.isBlank()) {
                return failed("MODEL_ID_MISSING", "百炼原厂模型 ID 未配置", null);
            }
            ObjectNode body = config.deepCopy();
            // 移除仅供本地组装使用的字段，避免向兼容接口发送未知参数。
            body.remove(List.of("providerModelName", "imageCount", "count", "systemPrompt",
                    "referenceImages", "referenceVideos", "referenceVideo", "videoFps"));
            body.put("model", providerModelName);
            body.put("stream", false);
            ArrayNode messages = body.putArray("messages");
            if (config.hasNonNull("systemPrompt")) {
                messages.addObject().put("role", "system").put("content", config.get("systemPrompt").asText());
            }
            ObjectNode userMessage = messages.addObject().put("role", "user");
            addUserContent(userMessage, request.prompt(), config);

            JsonNode response = gateway.exchange("POST", model.getEndpointUrl(), "/chat/completions",
                    Map.of("Authorization", "Bearer " + credentialResolver.resolve(model.getCredentialRef())),
                    body, model.getTimeoutSeconds());
            String content = response.path("choices").path(0).path("message").path("content").asText();
            if (content.isBlank()) {
                return failed("EMPTY_PROVIDER_RESULT", "百炼响应中没有文本结果", response.toString());
            }
            Map<String, Object> usage = usage(response.path("usage"));
            return new ProviderResult(ProviderStatus.SUCCEEDED,
                    response.path("id").asText("dashscope-sync-" + UUID.randomUUID()),
                    List.of(ProviderAsset.text(content)), usage, response.toString(), null, null);
        } catch (Exception e) {
            return failed(e.getClass().getSimpleName(), safeMessage(e), null);
        }
    }

    /**
     * 同步对话接口没有可轮询的供应商任务。
     */
    @Override
    public ProviderResult poll(ModelDefinition model, String providerRequestId) {
        return failed("DASHSCOPE_SYNC_TASK", "百炼 Chat Completions 为同步调用，不支持轮询", null);
    }

    /**
     * 同步调用完成后无需向供应商发送取消请求。
     */
    @Override
    public void cancel(ModelDefinition model, String providerRequestId) {
        // 同步请求返回后已经结束，本地取消无需调用原厂接口。
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
     * 根据是否存在参考媒体构造纯文本或多模态用户消息。
     */
    private void addUserContent(ObjectNode message, String prompt, ObjectNode config) {
        JsonNode images = config.path("referenceImages");
        JsonNode videos = config.path("referenceVideos");
        boolean hasSingleVideo = config.hasNonNull("referenceVideo")
                && !config.get("referenceVideo").asText().isBlank();
        boolean hasMedia = images.isArray() && !images.isEmpty()
                || videos.isArray() && !videos.isEmpty() || hasSingleVideo;
        if (!hasMedia) {
            message.put("content", prompt);
            return;
        }
        ArrayNode content = message.putArray("content");
        content.addObject().put("type", "text").put("text", prompt);
        if (images.isArray()) {
            for (JsonNode image : images) {
                addMedia(content, "image_url", image.asText(), null);
            }
        }
        Double fps = config.hasNonNull("videoFps") ? config.get("videoFps").asDouble() : null;
        if (videos.isArray()) {
            for (JsonNode video : videos) {
                addMedia(content, "video_url", video.asText(), fps);
            }
        }
        if (hasSingleVideo) {
            addMedia(content, "video_url", config.get("referenceVideo").asText(), fps);
        }
    }

    /**
     * 添加一个非空媒体 URL，并为视频按需附加采样帧率。
     */
    private void addMedia(ArrayNode content, String type, String url, Double fps) {
        if (url == null || url.isBlank()) {
            return;
        }
        ObjectNode media = content.addObject().put("type", type).putObject(type).put("url", url);
        if (fps != null && type.equals("video_url")) {
            media.put("fps", fps);
        }
    }

    /**
     * 把供应商令牌统计转换为统一用量字段。
     */
    private Map<String, Object> usage(JsonNode node) {
        Map<String, Object> usage = new LinkedHashMap<>();
        if (node.has("prompt_tokens")) usage.put("promptTokens", node.get("prompt_tokens").asLong());
        if (node.has("completion_tokens")) usage.put("completionTokens", node.get("completion_tokens").asLong());
        if (node.has("total_tokens")) usage.put("totalTokens", node.get("total_tokens").asLong());
        usage.put("outputCount", 1);
        return usage;
    }

    /**
     * 构造标准化失败结果。
     */
    private ProviderResult failed(String code, String message, String raw) {
        return new ProviderResult(ProviderStatus.FAILED, null, List.of(), Map.of(), raw, code, message);
    }

    /**
     * 提取并截断适合写入任务错误字段的异常信息。
     */
    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? "百炼文本模型调用失败" : message.substring(0, Math.min(message.length(), 900));
    }
}
