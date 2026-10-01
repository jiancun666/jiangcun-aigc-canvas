package com.semple.aigc.canvas.common.core.constant;

/**
 * 链路追踪常量.
 *
 * @author feilong
 * @date 2026-06-01
 * @desc 定义请求链路 ID 的请求头和日志上下文字段
 */
public final class TraceConstants {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    public static final String TRACE_ID_MDC_KEY = "traceId";

    private TraceConstants() {
    }
}
