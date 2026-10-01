package com.semple.aigc.canvas.common.core.utils;

import java.util.Arrays;
import java.util.List;

/**
 * String helpers.
 *
 * @author aofaming
 */
public final class StringUtils {

    private StringUtils() {
    }

    public static boolean isBlank(String value) {
        return !org.springframework.util.StringUtils.hasText(value);
    }

    public static boolean isNotBlank(String value) {
        return org.springframework.util.StringUtils.hasText(value);
    }

    public static String defaultString(String value) {
        return value == null ? "" : value;
    }

    public static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    public static List<String> splitToList(String value, String delimiter) {
        if (isBlank(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(delimiter))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .toList();
    }
}
