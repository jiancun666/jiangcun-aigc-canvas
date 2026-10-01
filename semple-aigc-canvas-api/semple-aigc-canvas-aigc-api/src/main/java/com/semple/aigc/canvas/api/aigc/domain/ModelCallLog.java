package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 一次模型供应商提交、查询或取消调用的审计日志。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_model_call_log")
public class ModelCallLog extends BaseEntity {
    /**
     * 生成任务 ID。
     */
    private Long generationTaskId;
    /**
     * 调用尝试序号。
     */
    private Integer attemptNo;
    /**
     * 动作：1提交，2查询，3取消。
     */
    private Integer actionType;
    /**
     * 供应商编码。
     */
    private String providerCode;
    /**
     * 模型编码。
     */
    private String modelCode;
    /**
     * 供应商请求 ID。
     */
    private String providerRequestId;
    /**
     * 调用结果：1成功，2失败。
     */
    private Integer callStatus;
    /**
     * 脱敏请求 JSON。
     */
    private String requestPayload;
    /**
     * 脱敏响应 JSON。
     */
    private String responsePayload;
    /**
     * 实际用量 JSON。
     */
    private String usageSnapshot;
    /**
     * 错误码。
     */
    private String errorCode;
    /**
     * 错误说明。
     */
    private String errorMessage;
    /**
     * 调用开始时间。
     */
    private LocalDateTime startedAt;
    /**
     * 调用结束时间。
     */
    private LocalDateTime finishedAt;
    /**
     * 调用耗时毫秒。
     */
    private Long durationMs;
}
