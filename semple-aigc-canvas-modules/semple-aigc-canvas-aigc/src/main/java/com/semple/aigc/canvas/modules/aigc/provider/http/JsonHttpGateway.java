package com.semple.aigc.canvas.modules.aigc.provider.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 原厂模型 REST API 共用的 JSON HTTP 客户端。
 */
@Component
@RequiredArgsConstructor
public class JsonHttpGateway {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * 发送 JSON HTTP 请求，并把成功响应解析为 JSON 节点。
     */
    public JsonNode exchange(String method, String endpoint, String path, Map<String, String> headers,
                             JsonNode body, int timeoutSeconds) throws IOException, InterruptedException {
        URI uri = URI.create(trimSlash(endpoint) + normalizePath(path));
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                .header("Accept", "application/json");
        headers.forEach(request::header);
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        }
        HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode payload = response.body() == null || response.body().isBlank()
                ? objectMapper.createObjectNode() : objectMapper.readTree(response.body());
        // 非 2xx 响应转换为受控异常，且只保留有限长度的供应商错误信息。
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = payload.path("message").asText(payload.path("error").path("message")
                    .asText(response.body()));
            throw new IOException("HTTP " + response.statusCode() + ": " + abbreviate(message));
        }
        return payload;
    }

    /**
     * 校验基础端点并移除末尾斜杠，避免拼接出重复分隔符。
     */
    private String trimSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("模型原厂 endpoint_url 未配置");
        }
        return value.trim().replaceAll("/+$", "");
    }

    /**
     * 将相对 API 路径规范为以斜杠开头的形式。
     */
    private String normalizePath(String value) {
        return value == null || value.isBlank() ? "" : value.startsWith("/") ? value : "/" + value;
    }

    /**
     * 截断供应商错误正文，避免异常和审计日志过大。
     */
    private String abbreviate(String value) {
        if (value == null) {
            return "empty response";
        }
        return value.substring(0, Math.min(value.length(), 900));
    }
}
