package com.semple.aigc.canvas.modules.aigc.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;
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

/** 火山方舟同步图片生成适配器。 */
@Component
@RequiredArgsConstructor
public class ArkImageModelProvider implements ModelProvider {
    private final CredentialResolver credentialResolver;
    private final ObjectMapper objectMapper;

    @Override
    public String adapterCode() {
        return "ARK_IMAGE";
    }

    @Override
    public ProviderResult submit(ModelDefinition model, ImageRequest request) {
        ArkService.Builder builder = ArkService.builder()
                .apiKey(credentialResolver.resolve(model.getCredentialRef()))
                .timeout(Duration.ofSeconds(model.getTimeoutSeconds()));
        if (model.getEndpointUrl() != null && !model.getEndpointUrl().isBlank()) {
            builder.baseUrl(model.getEndpointUrl());
        }
        ArkService service = builder.build();
        try {
            String size = text(request.config(), "size", "2048x2048");
            GenerateImagesRequest providerRequest = GenerateImagesRequest.builder()
                    .model(model.getModelCode())
                    .prompt(request.prompt())
                    .size(size)
                    .responseFormat("url")
                    .watermark(bool(request.config(), "watermark", false))
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

    @Override
    public ProviderResult poll(ModelDefinition model, String providerRequestId) {
        return failed("ARK_SYNC_TASK", "方舟图片适配器为同步调用，不支持轮询", null);
    }

    @Override
    public void cancel(ModelDefinition model, String providerRequestId) {
        // 同步请求返回后即完成，取消请求只更新本地任务状态。
    }

    private ProviderResult failed(String code, String message, String raw) {
        return new ProviderResult(ProviderStatus.FAILED, null, List.of(), Map.of(), raw, code, message);
    }

    private String text(com.fasterxml.jackson.databind.JsonNode node, String name, String defaultValue) {
        return node != null && node.hasNonNull(name) ? node.get(name).asText() : defaultValue;
    }

    private boolean bool(com.fasterxml.jackson.databind.JsonNode node, String name, boolean defaultValue) {
        return node != null && node.hasNonNull(name) ? node.get(name).asBoolean() : defaultValue;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? "方舟图片生成失败" : message.substring(0, Math.min(message.length(), 900));
    }
}
