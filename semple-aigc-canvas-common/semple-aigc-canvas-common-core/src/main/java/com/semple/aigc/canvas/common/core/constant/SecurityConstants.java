package com.semple.aigc.canvas.common.core.constant;

/**
 * Security related constants.
 *
 * @author aofaming
 */
public final class SecurityConstants {

    public static final String AUTHORIZATION_HEADER = "Authorization";

    public static final String TOKEN_PREFIX = "Bearer ";

    public static final String FROM_SOURCE = "from-source";

    public static final String INNER = "inner";

    /** 内部服务共享令牌请求头；必须通过部署环境配置。 */
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private SecurityConstants() {
    }
}
