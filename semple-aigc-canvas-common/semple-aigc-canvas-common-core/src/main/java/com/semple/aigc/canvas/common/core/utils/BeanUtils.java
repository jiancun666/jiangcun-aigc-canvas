package com.semple.aigc.canvas.common.core.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Bean copy helpers.
 *
 * @author aofaming
 */
@Slf4j
public final class BeanUtils {

    private BeanUtils() {
    }

    public static void copyProperties(Object source, Object target) {
        if (source == null || target == null) {
            return;
        }
        org.springframework.beans.BeanUtils.copyProperties(source, target);
    }

    public static <T> T copyProperties(Object source, Class<T> targetType) {
        if (source == null) {
            return null;
        }
        try {
            T target = targetType.getDeclaredConstructor().newInstance();
            copyProperties(source, target);
            return target;
        } catch (ReflectiveOperationException ex) {
            log.error("Create target bean failed, targetType={}, exception={}", targetType.getName(), ExceptionUtils.getStackTrace(ex));
            throw new IllegalStateException("Create target bean failed: " + targetType.getName(), ex);
        }
    }
}
