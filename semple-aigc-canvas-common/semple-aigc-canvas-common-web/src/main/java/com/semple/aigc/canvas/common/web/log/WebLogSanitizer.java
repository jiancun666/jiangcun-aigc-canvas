package com.semple.aigc.canvas.common.web.log;

import com.semple.aigc.canvas.common.core.utils.JsonUtils;
import com.semple.aigc.canvas.common.web.config.WebLogProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Web 日志脱敏工具.
 *
 * @author feilong
 * @date 2026-06-01
 * @desc 统一处理日志内容序列化、截断和敏感字段脱敏
 */
@Component
public class WebLogSanitizer {

    private static final String MASK = "******";

    private final WebLogProperties properties;

    public WebLogSanitizer(WebLogProperties properties) {
        this.properties = properties;
    }

    public String format(Object value) {
        if (value == null) {
            return null;
        }

        String text;
        if (value instanceof CharSequence) {
            text = value.toString();
        } else {
            try {
                text = JsonUtils.toJson(value);
            } catch (Exception ex) {
                text = String.valueOf(value);
            }
        }
        return truncate(maskSensitiveText(text));
    }

    public Map<String, Object> maskParameterMap(Map<String, String[]> parameterMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (parameterMap == null || parameterMap.isEmpty()) {
            return result;
        }
        parameterMap.forEach((key, values) -> {
            if (isSensitiveField(key)) {
                result.put(key, MASK);
            } else if (values == null || values.length == 0) {
                result.put(key, null);
            } else if (values.length == 1) {
                result.put(key, values[0]);
            } else {
                result.put(key, values);
            }
        });
        return result;
    }

    public String truncate(String text) {
        if (text == null) {
            return null;
        }
        int limit = properties.getPayloadLimit();
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "...(truncated)";
    }

    private String maskSensitiveText(String text) {
        if (text == null || properties.getSensitiveFields() == null) {
            return text;
        }

        String result = text;
        for (String field : properties.getSensitiveFields()) {
            if (field == null || field.isBlank()) {
                continue;
            }
            String jsonPattern = "(?i)(\"" + Pattern.quote(field)
                    + "\"\\s*:\\s*)(\"[^\"]*\"|\\d+|true|false|null)";
            result = result.replaceAll(jsonPattern, "$1\"" + MASK + "\"");

            String plainPattern = "(?i)(" + Pattern.quote(field) + "\\s*[=:]\\s*)([^,}&\\s]+)";
            result = result.replaceAll(plainPattern, "$1" + MASK);
        }
        return result;
    }

    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null || properties.getSensitiveFields() == null) {
            return false;
        }
        return properties.getSensitiveFields().stream()
                .filter(field -> field != null && !field.isBlank())
                .anyMatch(field -> field.equalsIgnoreCase(fieldName));
    }
}
