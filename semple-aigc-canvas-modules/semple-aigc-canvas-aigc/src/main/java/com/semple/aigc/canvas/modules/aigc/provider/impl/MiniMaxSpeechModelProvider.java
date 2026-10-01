package com.semple.aigc.canvas.modules.aigc.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;
import com.semple.aigc.canvas.modules.aigc.provider.CredentialResolver;
import com.semple.aigc.canvas.modules.aigc.provider.ModelProvider;
import com.semple.aigc.canvas.modules.aigc.provider.http.JsonHttpGateway;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * MiniMax 同步文本转语音适配器。
 *
 * <p>把项目统一的生成请求转换为 MiniMax T2A 请求，并将供应商响应还原为统一音频资产。</p>
 */
@Component
@RequiredArgsConstructor
public class MiniMaxSpeechModelProvider implements ModelProvider {
    private static final String API_PATH = "/v1/t2a_v2";

    private final CredentialResolver credentialResolver;
    private final ObjectMapper objectMapper;
    private final JsonHttpGateway gateway;

    /**
     * 返回模型目录中使用的适配器编码。
     */
    @Override
    public String adapterCode() {
        return "MINIMAX_SPEECH";
    }

    /**
     * 同步调用 MiniMax T2A 接口生成音频。
     *
     * @param model   数据库中的模型定义，提供端点、凭证引用和默认参数
     * @param request 本次生成请求，prompt 作为待合成文本
     * @return 标准化供应商结果；成功时包含一个音频资产
     */
    @Override
    public ProviderResult submit(ModelDefinition model, GenerationRequest request) {
        try {
            ObjectNode body = mergedConfig(model, request.config());
            String providerModelName = body.path("providerModelName").asText();
            if (providerModelName.isBlank()) {
                return failed("MODEL_ID_MISSING", "MiniMax 原厂模型 ID 未配置", null);
            }
            String voiceId = body.path("voice_setting").path("voice_id").asText();
            if (voiceId.isBlank()) {
                return failed("VOICE_ID_MISSING", "MiniMax voice_setting.voice_id 未配置", null);
            }

            // providerModelName 和计数字段是画布内部配置，不应透传给 MiniMax。
            body.remove(List.of("providerModelName", "imageCount", "count"));
            body.put("model", providerModelName);
            body.put("text", request.prompt());
            // 当前任务框架按一次调用接收完整结果，因此固定使用非流式 URL 输出。
            body.put("stream", false);
            body.put("output_format", "url");

            JsonNode response = gateway.exchange("POST", model.getEndpointUrl(), API_PATH,
                    Map.of("Authorization", "Bearer "
                            + credentialResolver.resolve(model.getCredentialRef())),
                    body, model.getTimeoutSeconds());
            // MiniMax 在 HTTP 200 时仍可能通过 base_resp 返回业务错误，必须单独判断。
            JsonNode baseResponse = response.path("base_resp");
            int statusCode = baseResponse.path("status_code").asInt(0);
            if (statusCode != 0) {
                return failed(String.valueOf(statusCode),
                        baseResponse.path("status_msg").asText("MiniMax 语音生成失败"), response.toString());
            }

            String audioUrl = response.path("data").path("audio").asText();
            if (audioUrl.isBlank()) {
                return failed("EMPTY_PROVIDER_RESULT", "MiniMax 响应中没有音频 URL", response.toString());
            }
            JsonNode extra = response.path("extra_info");
            // 优先采用响应中的真实格式；供应商未返回时再使用请求格式或 MP3 默认值。
            String format = extra.path("audio_format").asText(
                    body.path("audio_setting").path("format").asText("mp3"));
            long duration = extra.path("audio_length").asLong(0L);
            Map<String, Object> usage = usage(extra, duration);
            ProviderAsset asset = ProviderAsset.media(audioUrl, mimeType(format), 4,
                    null, null, duration > 0 ? duration : null);
            return new ProviderResult(ProviderStatus.SUCCEEDED,
                    response.path("trace_id").asText("minimax-sync-" + UUID.randomUUID()),
                    List.of(asset), usage, response.toString(), null, null);
        } catch (Exception e) {
            return failed(e.getClass().getSimpleName(), safeMessage(e), null);
        }
    }

    /**
     * MiniMax T2A 为同步接口，不存在可轮询的异步任务。
     */
    @Override
    public ProviderResult poll(ModelDefinition model, String providerRequestId) {
        return failed("MINIMAX_SYNC_TASK", "MiniMax T2A 为同步调用，不支持轮询", null);
    }

    /**
     * 同步请求返回时已经结束，供应商侧无需执行取消操作。
     */
    @Override
    public void cancel(ModelDefinition model, String providerRequestId) {
        // 无操作：本地任务取消仍由上层任务服务负责。
    }

    /**
     * 合并数据库默认配置和单次请求配置，单次请求中的同名字段优先。
     */
    private ObjectNode mergedConfig(ModelDefinition model, JsonNode requestConfig) throws Exception {
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
     * 提取计费审计和资产记录需要的字符数、音频大小及时长。
     */
    private Map<String, Object> usage(JsonNode extra, long duration) {
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("outputCount", 1);
        if (extra.has("usage_characters")) {
            usage.put("characters", extra.get("usage_characters").asLong());
        }
        if (extra.has("word_count")) {
            usage.put("wordCount", extra.get("word_count").asLong());
        }
        if (extra.has("audio_size")) {
            usage.put("audioSize", extra.get("audio_size").asLong());
        }
        if (duration > 0) {
            usage.put("durationMs", duration);
        }
        return usage;
    }

    /**
     * 将 MiniMax 音频格式转换为标准 MIME 类型。
     */
    private String mimeType(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "wav" -> "audio/wav";
            case "flac" -> "audio/flac";
            default -> "audio/mpeg";
        };
    }

    /**
     * 构造统一失败结果，避免在各失败分支重复组装对象。
     */
    private ProviderResult failed(String code, String message, String raw) {
        return new ProviderResult(ProviderStatus.FAILED, null, List.of(), Map.of(), raw, code, message);
    }

    /**
     * 限制外部异常信息长度，防止审计字段被超长响应占满。
     */
    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? "MiniMax 语音生成失败"
                : message.substring(0, Math.min(message.length(), 900));
    }
}
