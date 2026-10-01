package com.semple.aigc.canvas.common.security;

/**
 * JWT 校验状态.
 *
 * @author feilong
 * @date 2026-06-04
 * @desc 区分有效、过期和无效令牌
 */
public enum JwtValidateStatus {

    /**
     * 令牌有效
     */
    VALID,

    /**
     * 令牌已过期，且仍在过期识别窗口内
     */
    EXPIRED_WITHIN_WINDOW,

    /**
     * 令牌已过期，且超过过期识别窗口
     */
    EXPIRED_OUT_OF_WINDOW,

    /**
     * 令牌无效
     */
    INVALID
}
