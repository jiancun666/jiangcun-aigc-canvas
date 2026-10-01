package com.semple.aigc.canvas.modules.aigc.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;
import com.semple.aigc.canvas.modules.aigc.provider.CredentialResolver;
import com.semple.aigc.canvas.modules.aigc.provider.ModelProvider;
import com.semple.aigc.canvas.modules.aigc.provider.http.JsonHttpGateway;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 快手可灵 Omni Video 原厂适配器。
 */
@Component
@RequiredArgsConstructor
public class KlingVideoModelProvider implements ModelProvider {
    private static final String DEFAULT_PATH = "/v1/videos/omni-video";
    private static final List<String> PASSTHROUGH_FIELDS = List.of(
            "negative_prompt", "mode", "duration", "aspect_ratio", "sound", "multi_shot",
            "shot_type", "multi_prompt", "image_list", "video_list", "element_list",
            "external_task_id", "callback_url");

    private final CredentialResolver credentialResolver;
    private final ObjectMapper objectMapper;
    private final JsonHttpGateway gateway;

    /**
     * 返回可灵视频适配器编码。
     */
    @Override
    public String adapterCode() {
        return "KLING_VIDEO";
    }

    /**
     * 创建可灵异步视频生成任务并返回原厂任务 ID。
     */
    @Override
    public ProviderResult submit(ModelDefinition model, GenerationRequest request) {
        try {
            ObjectNode config = mergedConfig(model, request.config());
            String providerModelName = config.path("providerModelName").asText();
            if (providerModelName.isBlank()) {
                return failed("MODEL_ID_MISSING", "可灵原厂模型 ID 未配置", null, null);
            }
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model_name", providerModelName);
            body.put("prompt", request.prompt());
            // 仅发送可灵公开支持的白名单参数，隔离画布内部配置字段。
            for (String field : PASSTHROUGH_FIELDS) {
                if (config.has(field)) body.set(field, config.get(field));
            }
            JsonNode response = gateway.exchange("POST", model.getEndpointUrl(), apiPath(config),
                    headers(model), body, model.getTimeoutSeconds());
            if (response.path("code").asInt(0) != 0) {
                return failed("KLING_" + response.path("code").asText(),
                        response.path("message").asText("可灵任务提交失败"), response.toString(), null);
            }
            String taskId = response.path("data").path("task_id").asText();
            if (taskId.isBlank()) {
                return failed("KLING_MISSING_TASK_ID", "可灵未返回任务 ID", response.toString(), null);
            }
            return new ProviderResult(ProviderStatus.PROCESSING, taskId, List.of(), Map.of(),
                    response.toString(), null, null);
        } catch (Exception e) {
            return failed(e.getClass().getSimpleName(), safeMessage(e), null, null);
        }
    }

    /**
     * 查询可灵任务状态，并在成功时转换视频资产和时长。
     */
    @Override
    public ProviderResult poll(ModelDefinition model, String providerRequestId) {
        try {
            ObjectNode config = mergedConfig(model, null);
            JsonNode response = gateway.exchange("GET", model.getEndpointUrl(),
                    apiPath(config) + "/" + providerRequestId, headers(model), null, model.getTimeoutSeconds());
            if (response.path("code").asInt(0) != 0) {
                return failed("KLING_" + response.path("code").asText(),
                        response.path("message").asText("可灵任务查询失败"), response.toString(), providerRequestId);
            }
            JsonNode data = response.path("data");
            String status = data.path("task_status").asText("").toLowerCase(Locale.ROOT);
            if (status.equals("failed")) {
                return failed("KLING_TASK_FAILED", data.path("task_status_msg").asText("可灵任务失败"),
                        response.toString(), providerRequestId);
            }
            if (status.equals("cancelled") || status.equals("canceled")) {
                return new ProviderResult(ProviderStatus.CANCELLED, providerRequestId,
                        List.of(), Map.of(), response.toString(), null, null);
            }
            if (!status.equals("succeed") && !status.equals("succeeded")) {
                return new ProviderResult(ProviderStatus.PROCESSING, providerRequestId,
                        List.of(), Map.of(), response.toString(), null, null);
            }
            JsonNode video = data.path("task_result").path("videos").path(0);
            String url = video.path("url").asText();
            if (url.isBlank()) {
                return failed("EMPTY_PROVIDER_RESULT", "可灵成功响应中没有视频地址",
                        response.toString(), providerRequestId);
            }
            Long durationMs = video.has("duration") ? video.get("duration").asLong() * 1000L : null;
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("outputCount", 1);
            return new ProviderResult(ProviderStatus.SUCCEEDED, providerRequestId,
                    List.of(ProviderAsset.media(url, "video/mp4", 3, null, null, durationMs)),
                    usage, response.toString(), null, null);
        } catch (Exception e) {
            return failed(e.getClass().getSimpleName(), safeMessage(e), null, providerRequestId);
        }
    }

    /**
     * 当前公开接口不支持取消，任务服务仍会完成本地取消。
     */
    @Override
    public void cancel(ModelDefinition model, String providerRequestId) {
        // 可灵 Omni Video 公开接口未提供稳定的取消端点，本地任务停止轮询并释放积分。
    }

    /**
     * 解析访问密钥与签名密钥并构造 JWT Bearer 请求头。
     */
    private Map<String, String> headers(ModelDefinition model) throws Exception {
        String accessKey = credentialResolver.resolve(model.getCredentialRef());
        String secretKey = credentialResolver.resolve("env:KLING_SECRET_KEY");
        return Map.of("Authorization", "Bearer " + jwt(accessKey, secretKey));
    }

    /**
     * 按可灵要求使用 HS256 生成短时效接口访问令牌。
     */
    private String jwt(String accessKey, String secretKey) throws Exception {
        long now = Instant.now().getEpochSecond();
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        ObjectNode payloadNode = objectMapper.createObjectNode();
        payloadNode.put("iss", accessKey);
        payloadNode.put("exp", now + 1800);
        payloadNode.put("nbf", now - 5);
        String payload = base64Url(objectMapper.writeValueAsString(payloadNode));
        // JWT 签名覆盖 Base64URL 编码后的 header.payload，签名结果同样不带填充符。
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }

    /**
     * 对文本执行无填充 Base64URL 编码。
     */
    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 读取可覆盖的 API 路径，默认使用 Omni Video 接口。
     */
    private String apiPath(ObjectNode config) {
        return config.path("apiPath").asText(DEFAULT_PATH);
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
        return message == null ? "可灵原厂接口调用失败" : message.substring(0, Math.min(message.length(), 900));
    }
}
