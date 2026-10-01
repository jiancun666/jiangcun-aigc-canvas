package com.semple.aigc.canvas.modules.aigc.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;
import com.semple.aigc.canvas.modules.aigc.provider.ModelProvider.GenerationRequest;
import com.semple.aigc.canvas.modules.aigc.provider.ModelProvider.ProviderStatus;
import com.semple.aigc.canvas.modules.aigc.provider.impl.LibTvModelProvider;
import com.semple.aigc.canvas.modules.aigc.provider.libtv.LibTvGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LibTvModelProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CredentialResolver credentialResolver = mock(CredentialResolver.class);
    private final LibTvGateway gateway = mock(LibTvGateway.class);
    private LibTvModelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LibTvModelProvider(credentialResolver, objectMapper, gateway);
        when(credentialResolver.resolve("env:LIBTV_ACCESS_KEY")).thenReturn("test-key");
    }

    @Test
    void submitUsesExactCatalogModelNameAndReturnsSession() throws Exception {
        ModelDefinition model = model(2, "Lib Image 2.5 Pro");
        when(gateway.createSession(eq("https://im.liblib.tv"), eq("test-key"),
                org.mockito.ArgumentMatchers.anyString(), anyInt()))
                .thenReturn(json("""
                        {"data":{"projectUuid":"project-1","sessionId":"session-1"}}
                        """));

        ModelProvider.ProviderResult result = provider.submit(model,
                new GenerationRequest("一只橘猫", 2, json("{\"ratio\":\"1:1\"}")));

        assertThat(result.status()).isEqualTo(ProviderStatus.PROCESSING);
        assertThat(result.providerRequestId()).isEqualTo("session-1");
        assertThat(result.usage()).containsEntry("projectUuid", "project-1");
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(gateway).createSession(eq("https://im.liblib.tv"), eq("test-key"),
                message.capture(), eq(900));
        assertThat(message.getValue()).contains("模型「Lib Image 2.5 Pro」", "一只橘猫", "\"count\":2");
    }

    @Test
    void pollConvertsToolImageResult() throws Exception {
        ModelDefinition model = model(2, "General image V2");
        when(gateway.querySession(eq("https://im.liblib.tv"), eq("test-key"), eq("session-1"), anyInt()))
                .thenReturn(json("""
                        {"data":{"messages":[
                          {"role":"tool","content":"{\\"task_result\\":{\\"images\\":[{\\"previewPath\\":\\"https://libtv-res.liblib.art/a/result.webp\\"}]}}"}
                        ]}}
                        """));

        ModelProvider.ProviderResult result = provider.poll(model, "session-1");

        assertThat(result.status()).isEqualTo(ProviderStatus.SUCCEEDED);
        assertThat(result.assets()).singleElement().satisfies(asset -> {
            assertThat(asset.assetType()).isEqualTo(2);
            assertThat(asset.mimeType()).isEqualTo("image/webp");
            assertThat(asset.url()).endsWith("result.webp");
        });
    }

    @Test
    void pollSupportsVideoAudioAndTextOutputs() throws Exception {
        when(gateway.querySession(eq("https://im.liblib.tv"), eq("test-key"),
                org.mockito.ArgumentMatchers.anyString(), anyInt()))
                .thenReturn(json("""
                        {"data":{"messages":[
                          {"role":"assistant","content":"文本回答"},
                          {"role":"tool","content":"{\\"task_result\\":{\\"videos\\":[{\\"url\\":\\"https://libtv-res.liblib.art/a/clip.mp4\\"}],\\"audios\\":[{\\"url\\":\\"https://libtv-res.liblib.art/a/sound.mp3\\"}]}}"}
                        ]}}
                        """));

        assertThat(provider.poll(model(1, "Qwen 3 VL Flash"), "text").assets())
                .singleElement().extracting(ModelProvider.ProviderAsset::textContent).isEqualTo("文本回答");
        assertThat(provider.poll(model(3, "Seedance 2.5"), "video").assets())
                .singleElement().extracting(ModelProvider.ProviderAsset::assetType).isEqualTo(3);
        assertThat(provider.poll(model(4, "Seedance 2.0 VIP"), "audio").assets())
                .singleElement().extracting(ModelProvider.ProviderAsset::assetType).isEqualTo(4);
    }

    @Test
    void pollReturnsProviderFailure() throws Exception {
        when(gateway.querySession(eq("https://im.liblib.tv"), eq("test-key"), eq("failed"), anyInt()))
                .thenReturn(json("""
                        {"data":{"messages":[
                          {"role":"tool","content":"{\\"task_result\\":{\\"status\\":\\"failed\\",\\"error\\":\\"content rejected\\"}}"}
                        ]}}
                        """));

        ModelProvider.ProviderResult result = provider.poll(model(3, "Kling O3"), "failed");

        assertThat(result.status()).isEqualTo(ProviderStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("LIBTV_TASK_FAILED");
        assertThat(result.errorMessage()).isEqualTo("content rejected");
    }

    private ModelDefinition model(int type, String name) {
        ModelDefinition model = new ModelDefinition();
        model.setProviderCode("LIBTV");
        model.setModelCode("M-test-" + type);
        model.setModelName(name);
        model.setModelType(type);
        model.setAdapterCode("LIBTV");
        model.setEndpointUrl("https://im.liblib.tv");
        model.setCredentialRef("env:LIBTV_ACCESS_KEY");
        model.setDefaultRequestConfig("{\"providerModelName\":\"" + name + "\"}");
        model.setTimeoutSeconds(type >= 3 ? 1800 : 900);
        model.setEnabled(1);
        return model;
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }
}
