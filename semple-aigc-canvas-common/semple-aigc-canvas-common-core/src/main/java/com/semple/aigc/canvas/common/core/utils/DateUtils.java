package com.semple.aigc.canvas.common.core.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Date and time helpers.
 *
 * @author aofaming
 */
public final class DateUtils {

    public static final String DATE_PATTERN = "yyyy-MM-dd";

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";


    public static final String DATE_DEFAULT_LONG_FORMAT = "yyyyMMddHHmmssSSS";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    private static final DateTimeFormatter DATE_TIME_LONG_FORMATTER = DateTimeFormatter.ofPattern(DATE_DEFAULT_LONG_FORMAT);

    private DateUtils() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    public static String format(LocalDate date) {
        return Optional.ofNullable(date)
                .map(DATE_FORMATTER::format)
                .orElse(null);
    }

    public static String format(LocalDateTime dateTime) {
        return Optional.ofNullable(dateTime)
                .map(DATE_TIME_FORMATTER::format)
                .orElse(null);
    }

    public static String format(LocalDateTime dateTime,String  format) {
        DateTimeFormatter pattern = Optional.ofNullable(format)
                .filter(f -> !StringUtils.isBlank(f))
                .map(DateTimeFormatter::ofPattern)
                .orElse(DATE_TIME_FORMATTER);
        return Optional.ofNullable(dateTime)
                .map(pattern::format)
                .orElse(null);
    }

    public static LocalDate parseDate(String value) {
        return StringUtils.isBlank(value) ? null : LocalDate.parse(value, DATE_FORMATTER);
    }

    public static LocalDateTime parseDateTime(String value) {
        return StringUtils.isBlank(value) ? null : LocalDateTime.parse(value, DATE_TIME_FORMATTER);
    }
}
