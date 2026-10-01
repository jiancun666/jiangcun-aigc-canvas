package com.semple.aigc.canvas.common.core.utils;

import com.semple.aigc.canvas.common.core.exception.BizException;

/**
 * Business assertions.
 *
 * @author aofaming
 */
public final class AssertUtils {

    private AssertUtils() {
    }

    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new BizException(message);
        }
    }

    public static void notNull(Object value, String message) {
        if (value == null) {
            throw new BizException(message);
        }
    }

    public static void notBlank(String value, String message) {
        if (StringUtils.isBlank(value)) {
            throw new BizException(message);
        }
    }
}
