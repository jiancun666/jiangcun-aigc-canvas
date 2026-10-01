package com.semple.aigc.canvas.common.core.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.List;
import java.util.Map;

/**
 * JSON helpers backed by Jackson.
 *
 * @author aofaming
 */
@Slf4j
public final class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonUtils() {
    }

    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.error("Serialize JSON failed, exception={}", ExceptionUtils.getStackTrace(ex));
            throw new IllegalArgumentException("Serialize JSON failed", ex);
        }
    }

    public static <T> T parseObject(String json, Class<T> type) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JsonProcessingException ex) {
            log.error("Parse JSON object failed, exception={}", ExceptionUtils.getStackTrace(ex));
            throw new IllegalArgumentException("Parse JSON object failed", ex);
        }
    }

    public static <T> List<T> parseList(String json, Class<T> elementType) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(
                    json,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (JsonProcessingException ex) {
            log.error("Parse JSON list failed, exception={}", ExceptionUtils.getStackTrace(ex));
            throw new IllegalArgumentException("Parse JSON list failed", ex);
        }
    }

    public static Map<String, Object> parseMap(String json) {
        if (StringUtils.isBlank(json)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            log.error("Parse JSON map failed, exception={}", ExceptionUtils.getStackTrace(ex));
            throw new IllegalArgumentException("Parse JSON map failed", ex);
        }
    }
}
