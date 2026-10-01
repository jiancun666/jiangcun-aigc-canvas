package com.semple.aigc.canvas.gateway.filter;

import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.constant.SecurityConstants;
import com.semple.aigc.canvas.common.core.constant.TraceConstants;
import com.semple.aigc.canvas.common.core.domain.R;
import com.semple.aigc.canvas.common.core.utils.JsonUtils;
import com.semple.aigc.canvas.common.security.JwtUtils;
import com.semple.aigc.canvas.common.security.JwtValidateStatus;
import com.semple.aigc.canvas.gateway.config.AuthIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

/**
 * 网关全局认证过滤器.
 *
 * @author feilong
 * @date 2026-05-27
 * @desc 对所有请求进行JWT令牌验证，白名单URL和内部Feign调用除外
 */
@Slf4j
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    private final AuthIgnoreProperties authIgnoreProperties;
    private final String internalToken;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthFilter(AuthIgnoreProperties authIgnoreProperties,
                      @Value("${security.internal-token:}") String internalToken) {
        this.authIgnoreProperties = authIgnoreProperties;
        this.internalToken = internalToken;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = getOrCreateTraceId(exchange.getRequest());
        MDC.put(TraceConstants.TRACE_ID_MDC_KEY, traceId);

        ServerHttpRequest tracedRequest = exchange.getRequest().mutate()
                .headers(headers -> headers.set(TraceConstants.TRACE_ID_HEADER, traceId))
                .build();
        ServerWebExchange tracedExchange = exchange.mutate().request(tracedRequest).build();
        tracedExchange.getResponse().getHeaders().set(TraceConstants.TRACE_ID_HEADER, traceId);

        try {
            return doFilter(tracedExchange, chain);
        } finally {
            MDC.remove(TraceConstants.TRACE_ID_MDC_KEY);
        }
    }

    private Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 检查路径是否在白名单中
        if (isWhiteListed(path)) {
            ServerHttpRequest publicRequest = request.mutate().headers(headers -> {
                headers.remove("userId");
                headers.remove("username");
                headers.remove("workspaceType");
                headers.remove(SecurityConstants.FROM_SOURCE);
                headers.remove(SecurityConstants.INTERNAL_TOKEN_HEADER);
            }).build();
            return chain.filter(exchange.mutate().request(publicRequest).build());
        }

        // 2. 检查是否为内部Feign调用
        String fromSource = request.getHeaders().getFirst(SecurityConstants.FROM_SOURCE);
        if (SecurityConstants.INNER.equals(fromSource) && isTrustedInternal(request)) {
            return chain.filter(exchange);
        }

        // 3. 提取并验证JWT令牌
        String token = extractToken(request);
        if (token == null) {
            return unauthorizedResponse(exchange, ErrorCode.UNAUTHORIZED);
        }
        JwtValidateStatus validateStatus = JwtUtils.validateTokenStatus(token);
        if (JwtValidateStatus.EXPIRED_WITHIN_WINDOW.equals(validateStatus)) {
            return unauthorizedResponse(exchange, ErrorCode.TOKEN_AUTH_EXPIRED);
        }
        if (!JwtValidateStatus.VALID.equals(validateStatus)) {
            return unauthorizedResponse(exchange, ErrorCode.UNAUTHORIZED);
        }

        // 4. 将用户信息添加到请求头，传递给下游服务
        try {
            Long userId = JwtUtils.getUserIdFromToken(token);
            String username = JwtUtils.getUsernameFromToken(token);
            String workspaceType = JwtUtils.getWorkspaceTypeFromToken(token);

            ServerHttpRequest mutatedRequest = request.mutate()
                    .headers(headers -> {
                        headers.set(TraceConstants.TRACE_ID_HEADER,
                                request.getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER));
                        headers.set("userId", userId.toString());
                        headers.set("username", username);
                        headers.remove(SecurityConstants.FROM_SOURCE);
                        headers.remove(SecurityConstants.INTERNAL_TOKEN_HEADER);
                        if (workspaceType != null) {
                            headers.set("workspaceType", workspaceType);
                        } else {
                            headers.remove("workspaceType");
                        }
                    })
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            log.error("从令牌中提取用户信息失败", e);
            return unauthorizedResponse(exchange, ErrorCode.UNAUTHORIZED);
        }
    }

    private String getOrCreateTraceId(ServerHttpRequest request) {
        String traceId = request.getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER);
        if (StringUtils.hasText(traceId)) {
            return traceId.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public int getOrder() {
        return -100;
    }

    /**
     * 检查路径是否在白名单中.
     *
     * @param path 请求路径
     * @return 是否免认证
     */
    private boolean isWhiteListed(String path) {
        List<String> urls = authIgnoreProperties.getUrls();
        if (urls == null || urls.isEmpty()) {
            return false;
        }
        return urls.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 从Authorization请求头中提取令牌.
     *
     * @param request HTTP请求
     * @return JWT令牌字符串
     */
    private String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(SecurityConstants.AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            return header.substring(SecurityConstants.TOKEN_PREFIX.length());
        }
        return null;
    }

    /**
     * 使用常量时间比较校验内部服务共享令牌。
     */
    private boolean isTrustedInternal(ServerHttpRequest request) {
        String supplied = request.getHeaders().getFirst(SecurityConstants.INTERNAL_TOKEN_HEADER);
        return StringUtils.hasText(internalToken) && StringUtils.hasText(supplied)
                && MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 返回未认证响应.
     *
     * @param exchange  服务器交换对象
     * @param errorCode 错误码
     * @return 响应Mono
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, ErrorCode errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        R<Void> result = R.fail(errorCode.getCode(), errorCode.getMessage());
        String json = JsonUtils.toJson(result);
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }
}
