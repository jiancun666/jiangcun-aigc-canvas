package com.semple.aigc.canvas.modules.aigc.provider.libtv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * LibTV OpenAPI 的轻量 HTTP 客户端。
 */
@Component
@RequiredArgsConstructor
public class LibTvGateway {
    private static final String DEFAULT_ENDPOINT = "https://im.liblib.tv";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * 创建 LibTV 会话并返回初始任务响应。
     */
    public JsonNode createSession(String endpoint, String accessKey, String message, int timeoutSeconds)
            throws IOException, InterruptedException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("message", message);
        return exchange("POST", uri(endpoint, "/openapi/session"), accessKey,
                objectMapper.writeValueAsString(body), timeoutSeconds);
    }

    /**
     * 按会话 ID 查询 LibTV 的最新消息和任务结果。
     */
    public JsonNode querySession(String endpoint, String accessKey, String sessionId, int timeoutSeconds)
            throws IOException, InterruptedException {
        return exchange("GET", uri(endpoint, "/openapi/session/" + sessionId), accessKey,
                null, timeoutSeconds);
    }

    /**
     * 发送带 Bearer 凭证的 LibTV 请求并统一处理 HTTP 错误。
     */
    private JsonNode exchange(String method, URI uri, String accessKey, String body, int timeoutSeconds)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                .header("Authorization", "Bearer " + accessKey)
                .header("Accept", "application/json");
        if (body == null) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode payload = readJson(response.body());
        // 保留结构化响应供上层解析，同时把 HTTP 失败转换为有限长度异常。
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = payload.path("message").asText(response.body());
            throw new IOException("LibTV HTTP " + response.statusCode() + ": " + abbreviate(message));
        }
        return payload;
    }

    /**
     * 将空响应规范为空 JSON 对象，否则解析响应正文。
     */
    private JsonNode readJson(String body) throws IOException {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    /**
     * 使用配置端点或默认端点拼接 LibTV API 地址。
     */
    private URI uri(String endpoint, String path) {
        String base = endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint.trim();
        return URI.create(base.replaceAll("/+$", "") + path);
    }

    /**
     * 截断过长错误信息，避免异常日志膨胀。
     */
    private String abbreviate(String value) {
        if (value == null) {
            return "empty response";
        }
        return value.substring(0, Math.min(value.length(), 500));
    }
}
