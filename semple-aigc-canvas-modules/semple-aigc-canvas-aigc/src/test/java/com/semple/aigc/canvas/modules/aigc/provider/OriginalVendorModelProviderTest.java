package com.semple.aigc.canvas.modules.aigc.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;
import com.semple.aigc.canvas.modules.aigc.provider.ModelProvider.GenerationRequest;
import com.semple.aigc.canvas.modules.aigc.provider.ModelProvider.ProviderStatus;
import com.semple.aigc.canvas.modules.aigc.provider.http.JsonHttpGateway;
import com.semple.aigc.canvas.modules.aigc.provider.impl.ArkVideoModelProvider;
import com.semple.aigc.canvas.modules.aigc.provider.impl.DashScopeChatModelProvider;
import com.semple.aigc.canvas.modules.aigc.provider.impl.KlingVideoModelProvider;
import com.semple.aigc.canvas.modules.aigc.provider.impl.MiniMaxSpeechModelProvider;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OriginalVendorModelProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CredentialResolver credentialResolver = mock(CredentialResolver.class);
    private final JsonHttpGateway gateway = mock(JsonHttpGateway.class);

    @BeforeEach
    void setUp() {
        when(credentialResolver.resolve("env:DASHSCOPE_API_KEY")).thenReturn("dashscope-key");
        when(credentialResolver.resolve("env:ARK_API_KEY")).thenReturn("ark-key");
        when(credentialResolver.resolve("env:KLING_ACCESS_KEY")).thenReturn("kling-access");
        when(credentialResolver.resolve("env:KLING_SECRET_KEY")).thenReturn("kling-secret");
        when(credentialResolver.resolve("env:MINIMAX_API_KEY")).thenReturn("minimax-key");
    }

    @Test
    void qwenUsesAlibabaChatCompletionsWithOriginalModelId() throws Exception {
        DashScopeChatModelProvider provider = new DashScopeChatModelProvider(
                credentialResolver, objectMapper, gateway);
        ModelDefinition model = model("ALIBABA", "DASHSCOPE_CHAT", 1,
                "https://dashscope.aliyuncs.com/compatible-mode/v1", "env:DASHSCOPE_API_KEY",
                "{\"providerModelName\":\"qwen3-vl-flash\",\"enable_thinking\":false}");
        when(gateway.exchange(eq("POST"), eq(model.getEndpointUrl()), eq("/chat/completions"),
                anyMap(), any(JsonNode.class), anyInt())).thenReturn(json("""
                {"id":"chat-1","choices":[{"message":{"content":"识别结果"}}],
                 "usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}}
                """));

        ModelProvider.ProviderResult result = provider.submit(model,
                new GenerationRequest("看看这张图", 1, objectMapper.createObjectNode()));

        assertThat(result.status()).isEqualTo(ProviderStatus.SUCCEEDED);
        assertThat(result.assets()).singleElement()
                .extracting(ModelProvider.ProviderAsset::textContent).isEqualTo("识别结果");
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(gateway).exchange(eq("POST"), eq(model.getEndpointUrl()), eq("/chat/completions"),
                anyMap(), body.capture(), eq(300));
        assertThat(body.getValue().path("model").asText()).isEqualTo("qwen3-vl-flash");
        assertThat(body.getValue().path("messages").path(0).path("content").asText()).isEqualTo("看看这张图");
    }

    @Test
    void qwenBuildsMultimodalContentForImagesAndVideo() throws Exception {
        DashScopeChatModelProvider provider = new DashScopeChatModelProvider(
                credentialResolver, objectMapper, gateway);
        ModelDefinition model = model("ALIBABA", "DASHSCOPE_CHAT", 1,
                "https://dashscope.aliyuncs.com/compatible-mode/v1", "env:DASHSCOPE_API_KEY",
                "{\"providerModelName\":\"qwen3-vl-flash\"}");
        when(gateway.exchange(eq("POST"), eq(model.getEndpointUrl()), eq("/chat/completions"),
                anyMap(), any(JsonNode.class), anyInt())).thenReturn(json("""
                {"id":"chat-2","choices":[{"message":{"content":"画面描述"}}]}
                """));

        JsonNode config = json("""
                {"referenceImages":["https://example.com/a.png"],
                 "referenceVideo":"https://example.com/a.mp4","videoFps":1.5}
                """);
        provider.submit(model, new GenerationRequest("分析素材", 1, config));

        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(gateway).exchange(eq("POST"), eq(model.getEndpointUrl()), eq("/chat/completions"),
                anyMap(), body.capture(), eq(300));
        JsonNode content = body.getValue().path("messages").path(0).path("content");
        assertThat(content.path(0).path("type").asText()).isEqualTo("text");
        assertThat(content.path(1).path("image_url").path("url").asText())
                .isEqualTo("https://example.com/a.png");
        assertThat(content.path(2).path("video_url").path("url").asText())
                .isEqualTo("https://example.com/a.mp4");
        assertThat(content.path(2).path("video_url").path("fps").asDouble()).isEqualTo(1.5);
        assertThat(body.getValue().has("referenceImages")).isFalse();
    }

    @Test
    void seedanceUsesVolcanoArkTaskApiAndParsesNativeAudioVideo() throws Exception {
        ArkVideoModelProvider provider = new ArkVideoModelProvider(credentialResolver, objectMapper, gateway);
        ModelDefinition model = model("BYTEDANCE", "ARK_VIDEO", 4,
                "https://ark.cn-beijing.volces.com/api/v3", "env:ARK_API_KEY",
                "{\"providerModelName\":\"doubao-seedance-2-5-260628\",\"generate_audio\":true}");
        when(gateway.exchange(eq("POST"), eq(model.getEndpointUrl()),
                eq("/contents/generations/tasks"), anyMap(), any(JsonNode.class), anyInt()))
                .thenReturn(json("{\"id\":\"cgt-1\"}"));

        ModelProvider.ProviderResult submitted = provider.submit(model,
                new GenerationRequest("生成雨声短片", 1, objectMapper.createObjectNode()));
        assertThat(submitted.status()).isEqualTo(ProviderStatus.PROCESSING);
        assertThat(submitted.providerRequestId()).isEqualTo("cgt-1");

        when(gateway.exchange(eq("GET"), eq(model.getEndpointUrl()),
                eq("/contents/generations/tasks/cgt-1"), anyMap(), eq(null), anyInt()))
                .thenReturn(json("""
                {"id":"cgt-1","status":"succeeded","content":{"video_url":"https://example.com/result.mp4"},
                 "duration":5,"usage":{"completion_tokens":100,"total_tokens":100}}
                """));
        ModelProvider.ProviderResult completed = provider.poll(model, "cgt-1");

        assertThat(completed.status()).isEqualTo(ProviderStatus.SUCCEEDED);
        assertThat(completed.assets()).singleElement().satisfies(asset -> {
            assertThat(asset.assetType()).isEqualTo(4);
            assertThat(asset.mimeType()).isEqualTo("audio/mp4");
            assertThat(asset.durationMs()).isEqualTo(5000L);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void klingUsesOmniVideoEndpointAndSignedOriginalVendorToken() throws Exception {
        KlingVideoModelProvider provider = new KlingVideoModelProvider(credentialResolver, objectMapper, gateway);
        ModelDefinition model = model("KUAISHOU", "KLING_VIDEO", 3,
                "https://api.klingai.com", "env:KLING_ACCESS_KEY",
                "{\"providerModelName\":\"kling-v3-omni\",\"apiPath\":\"/v1/videos/omni-video\",\"sound\":\"on\"}");
        when(gateway.exchange(eq("POST"), eq(model.getEndpointUrl()), eq("/v1/videos/omni-video"),
                anyMap(), any(JsonNode.class), anyInt()))
                .thenReturn(json("{\"code\":0,\"data\":{\"task_id\":\"kling-1\"}}"));

        ModelProvider.ProviderResult submitted = provider.submit(model,
                new GenerationRequest("电影感城市夜景", 1, objectMapper.createObjectNode()));

        assertThat(submitted.status()).isEqualTo(ProviderStatus.PROCESSING);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(gateway).exchange(eq("POST"), eq(model.getEndpointUrl()), eq("/v1/videos/omni-video"),
                headers.capture(), body.capture(), eq(1800));
        assertThat(headers.getValue().get("Authorization")).startsWith("Bearer ")
                .satisfies(value -> assertThat(value.substring(7).split("\\.")).hasSize(3));
        assertThat(body.getValue().path("model_name").asText()).isEqualTo("kling-v3-omni");
        assertThat(body.getValue().path("sound").asText()).isEqualTo("on");

        when(gateway.exchange(eq("GET"), eq(model.getEndpointUrl()),
                eq("/v1/videos/omni-video/kling-1"), anyMap(), eq(null), anyInt()))
                .thenReturn(json("""
                {"code":0,"data":{"task_status":"succeed","task_result":{"videos":[
                  {"url":"https://example.com/kling.mp4","duration":5}]}}}
                """));
        assertThat(provider.poll(model, "kling-1").assets()).singleElement()
                .extracting(ModelProvider.ProviderAsset::assetType).isEqualTo(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void miniMaxSpeechUsesT2aEndpointAndReturnsAudioAsset() throws Exception {
        MiniMaxSpeechModelProvider provider = new MiniMaxSpeechModelProvider(
                credentialResolver, objectMapper, gateway);
        ModelDefinition model = model("MINIMAX", "MINIMAX_SPEECH", 4,
                "https://api.minimax.io", "env:MINIMAX_API_KEY", """
                {"providerModelName":"speech-2.8-hd","language_boost":"auto",
                 "voice_setting":{"voice_id":"Chinese (Mandarin)_Lyrical_Voice","speed":1.0,
                    "vol":1.0,"pitch":0},
                 "audio_setting":{"sample_rate":32000,"bitrate":128000,"format":"mp3","channel":1}}
                """);
        model.setTimeoutSeconds(300);
        when(gateway.exchange(eq("POST"), eq(model.getEndpointUrl()), eq("/v1/t2a_v2"),
                anyMap(), any(JsonNode.class), anyInt())).thenReturn(json("""
                {"data":{"audio":"https://example.com/speech.mp3","status":2},
                 "extra_info":{"audio_length":11124,"audio_size":179926,"usage_characters":163,
                    "word_count":163,"audio_format":"mp3"},
                 "trace_id":"minimax-trace-1","base_resp":{"status_code":0,"status_msg":"success"}}
                """));

        ModelProvider.ProviderResult result = provider.submit(model,
                new GenerationRequest("生成一段中文旁白", 1, objectMapper.createObjectNode()));

        assertThat(result.status()).isEqualTo(ProviderStatus.SUCCEEDED);
        assertThat(result.providerRequestId()).isEqualTo("minimax-trace-1");
        assertThat(result.assets()).singleElement().satisfies(asset -> {
            assertThat(asset.url()).isEqualTo("https://example.com/speech.mp3");
            assertThat(asset.mimeType()).isEqualTo("audio/mpeg");
            assertThat(asset.assetType()).isEqualTo(4);
            assertThat(asset.durationMs()).isEqualTo(11124L);
        });
        assertThat(result.usage()).containsEntry("characters", 163L)
                .containsEntry("outputCount", 1);

        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<JsonNode> body = ArgumentCaptor.forClass(JsonNode.class);
        verify(gateway).exchange(eq("POST"), eq(model.getEndpointUrl()), eq("/v1/t2a_v2"),
                headers.capture(), body.capture(), eq(300));
        assertThat(headers.getValue()).containsEntry("Authorization", "Bearer minimax-key");
        assertThat(body.getValue().path("model").asText()).isEqualTo("speech-2.8-hd");
        assertThat(body.getValue().path("text").asText()).isEqualTo("生成一段中文旁白");
        assertThat(body.getValue().path("stream").asBoolean()).isFalse();
        assertThat(body.getValue().path("output_format").asText()).isEqualTo("url");
        assertThat(body.getValue().has("providerModelName")).isFalse();
    }

    @Test
    void miniMaxSpeechSurfacesProviderErrorFromSuccessfulHttpResponse() throws Exception {
        MiniMaxSpeechModelProvider provider = new MiniMaxSpeechModelProvider(
                credentialResolver, objectMapper, gateway);
        ModelDefinition model = model("MINIMAX", "MINIMAX_SPEECH", 4,
                "https://api.minimax.io", "env:MINIMAX_API_KEY", """
                {"providerModelName":"speech-2.8-turbo",
                 "voice_setting":{"voice_id":"Chinese (Mandarin)_Lyrical_Voice"}}
                """);
        when(gateway.exchange(eq("POST"), eq(model.getEndpointUrl()), eq("/v1/t2a_v2"),
                anyMap(), any(JsonNode.class), anyInt())).thenReturn(json("""
                {"trace_id":"minimax-trace-2",
                 "base_resp":{"status_code":1004,"status_msg":"authentication failed"}}
                """));

        ModelProvider.ProviderResult result = provider.submit(model,
                new GenerationRequest("测试", 1, objectMapper.createObjectNode()));

        assertThat(result.status()).isEqualTo(ProviderStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("1004");
        assertThat(result.errorMessage()).isEqualTo("authentication failed");
    }

    private ModelDefinition model(String providerCode, String adapterCode, int type, String endpoint,
                                  String credentialRef, String defaultConfig) {
        ModelDefinition model = new ModelDefinition();
        model.setProviderCode(providerCode);
        model.setModelCode("M-test-" + type);
        model.setModelName("test");
        model.setModelType(type);
        model.setAdapterCode(adapterCode);
        model.setEndpointUrl(endpoint);
        model.setCredentialRef(credentialRef);
        model.setDefaultRequestConfig(defaultConfig);
        model.setTimeoutSeconds(type == 1 ? 300 : 1800);
        model.setEnabled(1);
        return model;
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }
}
