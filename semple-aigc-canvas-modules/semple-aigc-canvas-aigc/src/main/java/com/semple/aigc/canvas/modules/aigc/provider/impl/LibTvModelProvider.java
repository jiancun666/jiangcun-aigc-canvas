package com.semple.aigc.canvas.modules.aigc.provider.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;
import com.semple.aigc.canvas.modules.aigc.provider.CredentialResolver;
import com.semple.aigc.canvas.modules.aigc.provider.ModelProvider;
import com.semple.aigc.canvas.modules.aigc.provider.libtv.LibTvGateway;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * LibTV 会话 OpenAPI 适配器。模型定义中的 modelName/providerModelName 必须使用 LibTV 实时目录返回的名称。
 */
@Component
@RequiredArgsConstructor
public class LibTvModelProvider implements ModelProvider {
    private static final Pattern MEDIA_URL = Pattern.compile(
            "https://libtv-res\\.liblib\\.art/[^\\s\"'<>]+\\.(?:png|jpe?g|webp|mp4|mov|webm|mp3|wav|m4a|aac|ogg)",
            Pattern.CASE_INSENSITIVE);

    private final CredentialResolver credentialResolver;
    private final ObjectMapper objectMapper;
    private final LibTvGateway gateway;

    /**
     * 返回 LibTV 会话适配器编码。
     */
    @Override
    public String adapterCode() {
        return "LIBTV";
    }

    /**
     * 创建 LibTV 会话并记录会话 ID，供后续轮询。
     */
    @Override
    public ProviderResult submit(ModelDefinition model, GenerationRequest request) {
        try {
            String accessKey = credentialResolver.resolve(model.getCredentialRef());
            JsonNode response = gateway.createSession(model.getEndpointUrl(), accessKey,
                    buildMessage(model, request), model.getTimeoutSeconds());
            String sessionId = response.path("data").path("sessionId").asText(null);
            if (sessionId == null || sessionId.isBlank()) {
                return failed("LIBTV_MISSING_SESSION", "LibTV 未返回 sessionId", json(response));
            }
            Map<String, Object> usage = new LinkedHashMap<>();
            String projectUuid = response.path("data").path("projectUuid").asText(null);
            if (projectUuid != null && !projectUuid.isBlank()) {
                usage.put("projectUuid", projectUuid);
            }
            return new ProviderResult(ProviderStatus.PROCESSING, sessionId, List.of(), usage,
                    json(response), null, null);
        } catch (Exception e) {
            return failed(e.getClass().getSimpleName(), safeMessage(e), null);
        }
    }

    /**
     * 查询会话消息，从工具结果中识别失败、文本或媒体资产。
     */
    @Override
    public ProviderResult poll(ModelDefinition model, String providerRequestId) {
        try {
            String accessKey = credentialResolver.resolve(model.getCredentialRef());
            JsonNode response = gateway.querySession(model.getEndpointUrl(), accessKey,
                    providerRequestId, model.getTimeoutSeconds());
            JsonNode messages = response.path("data").path("messages");
            if (!messages.isArray()) {
                return new ProviderResult(ProviderStatus.PROCESSING, providerRequestId,
                        List.of(), Map.of(), json(response), null, null);
            }

            String failure = findFailure(messages);
            if (failure != null) {
                return new ProviderResult(ProviderStatus.FAILED, providerRequestId,
                        List.of(), Map.of(), json(response), "LIBTV_TASK_FAILED", failure);
            }
            List<ProviderAsset> assets = model.getModelType() == 1
                    ? textAssets(messages) : mediaAssets(messages, model.getModelType());
            if (assets.isEmpty()) {
                return new ProviderResult(ProviderStatus.PROCESSING, providerRequestId,
                        List.of(), Map.of(), json(response), null, null);
            }
            Map<String, Object> usage = Map.of("outputCount", assets.size());
            return new ProviderResult(ProviderStatus.SUCCEEDED, providerRequestId,
                    assets, usage, json(response), null, null);
        } catch (Exception e) {
            return failed(e.getClass().getSimpleName(), safeMessage(e), null);
        }
    }

    /**
     * LibTV 无公开取消端点，本地任务取消后停止轮询即可。
     */
    @Override
    public void cancel(ModelDefinition model, String providerRequestId) {
        // LibTV 当前公开会话 OpenAPI 没有取消端点；本地任务仍可安全取消并停止轮询。
    }

    /**
     * 把统一生成请求转换为 LibTV 会话能够理解的自然语言消息。
     */
    private String buildMessage(ModelDefinition model, GenerationRequest request) throws JsonProcessingException {
        ObjectNode config = mergedConfig(model, request.config());
        String providerModelName = config.path("providerModelName").asText(model.getModelName());
        config.remove("providerModelName");
        String type = switch (model.getModelType()) {
            case 1 -> "文本";
            case 2 -> "图片";
            case 3 -> "视频";
            case 4 -> "音频";
            default -> "内容";
        };
        config.put("count", request.outputCount());
        return "使用模型「" + providerModelName + "」生成" + type + "。\n"
                + "提示词：" + request.prompt() + "\n"
                + "参数：" + objectMapper.writeValueAsString(config);
    }

    /**
     * 合并模型默认配置和单次请求配置。
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
     * 从最后一条有效助手消息中提取纯文本结果。
     */
    private List<ProviderAsset> textAssets(JsonNode messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonNode message = messages.get(i);
            if ("assistant".equals(message.path("role").asText())) {
                String content = message.path("content").asText("").trim();
                if (!content.isEmpty() && !containsMediaUrl(content)) {
                    return List.of(ProviderAsset.text(content));
                }
            }
        }
        return List.of();
    }

    /**
     * 从工具消息和普通消息中收集、去重并筛选目标类型的媒体 URL。
     */
    private List<ProviderAsset> mediaAssets(JsonNode messages, int modelType) {
        Map<String, Integer> urls = new LinkedHashMap<>();
        for (JsonNode message : messages) {
            String content = message.path("content").asText("");
            if ("tool".equals(message.path("role").asText())) {
                addToolResultUrls(content, urls);
            }
            Matcher matcher = MEDIA_URL.matcher(content);
            while (matcher.find()) {
                String url = matcher.group();
                urls.putIfAbsent(url, inferAssetType(url, modelType));
            }
        }
        List<ProviderAsset> assets = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : urls.entrySet()) {
            String url = entry.getKey();
            int assetType = entry.getValue();
            if (assetType == modelType) {
                assets.add(ProviderAsset.media(url, mimeType(url, assetType), assetType,
                        null, null, null));
            }
        }
        return assets;
    }

    /**
     * 解析结构化工具消息中的图片、视频和音频结果。
     */
    private void addToolResultUrls(String content, Map<String, Integer> urls) {
        try {
            JsonNode result = objectMapper.readTree(content).path("task_result");
            addUrls(result.path("images"), 2, urls);
            addUrls(result.path("videos"), 3, urls);
            addUrls(result.path("audios"), 4, urls);
        } catch (Exception ignored) {
            // 非 JSON tool 消息继续由 URL 正则处理。
        }
    }

    /**
     * 把结构化结果数组中的预览地址加入有序去重集合。
     */
    private void addUrls(JsonNode items, int assetType, Map<String, Integer> urls) {
        if (!items.isArray()) {
            return;
        }
        for (JsonNode item : items) {
            String url = item.path("previewPath").asText(item.path("url").asText(""));
            if (!url.isBlank()) {
                urls.putIfAbsent(url, assetType);
            }
        }
    }

    /**
     * 扫描工具消息并返回首个明确的任务失败原因。
     */
    private String findFailure(JsonNode messages) {
        for (JsonNode message : messages) {
            if (!"tool".equals(message.path("role").asText())) {
                continue;
            }
            try {
                JsonNode result = objectMapper.readTree(message.path("content").asText(""));
                JsonNode taskResult = result.path("task_result");
                String status = taskResult.path("status").asText("").toUpperCase(Locale.ROOT);
                if (status.equals("FAILED") || status.equals("ERROR")) {
                    return taskResult.path("error").asText(taskResult.path("message").asText("LibTV 任务失败"));
                }
            } catch (Exception ignored) {
                // 忽略非 JSON tool 消息。
            }
        }
        return null;
    }

    /**
     * 判断消息正文是否包含 LibTV 媒体资源地址。
     */
    private boolean containsMediaUrl(String content) {
        return MEDIA_URL.matcher(content).find();
    }

    /**
     * 根据 URL 文件扩展名推断统一资产类型。
     */
    private int inferAssetType(String url, int fallback) {
        String path;
        try {
            path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            path = url.toLowerCase(Locale.ROOT);
        }
        if (path.matches(".*\\.(png|jpg|jpeg|webp)$")) {
            return 2;
        }
        if (path.matches(".*\\.(mp4|mov|webm)$")) {
            return 3;
        }
        if (path.matches(".*\\.(mp3|wav|m4a|aac|ogg)$")) {
            return 4;
        }
        return fallback;
    }

    /**
     * 根据 URL 扩展名和资产类型推断 MIME 类型。
     */
    private String mimeType(String url, int assetType) {
        String path = url.toLowerCase(Locale.ROOT);
        if (path.contains(".png")) return "image/png";
        if (path.contains(".webp")) return "image/webp";
        if (path.contains(".jpg") || path.contains(".jpeg")) return "image/jpeg";
        if (path.contains(".webm")) return assetType == 4 ? "audio/webm" : "video/webm";
        if (path.contains(".mov")) return "video/quicktime";
        if (path.contains(".mp4")) return assetType == 4 ? "audio/mp4" : "video/mp4";
        if (path.contains(".wav")) return "audio/wav";
        if (path.contains(".m4a")) return "audio/mp4";
        if (path.contains(".aac")) return "audio/aac";
        if (path.contains(".ogg")) return "audio/ogg";
        if (path.contains(".mp3")) return "audio/mpeg";
        return switch (assetType) {
            case 2 -> "image/*";
            case 3 -> "video/*";
            case 4 -> "audio/*";
            default -> "application/octet-stream";
        };
    }

    /**
     * 构造标准化失败结果。
     */
    private ProviderResult failed(String code, String message, String raw) {
        return new ProviderResult(ProviderStatus.FAILED, null, List.of(), Map.of(), raw, code, message);
    }

    /**
     * 序列化供应商响应或配置用于调用审计。
     */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 提取并截断适合审计的异常信息。
     */
    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? "LibTV 模型调用失败" : message.substring(0, Math.min(message.length(), 900));
    }
}
