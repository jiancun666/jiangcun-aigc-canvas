package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流中一个节点的一次执行记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_workflow_step_run")
public class WorkflowStepRun extends BaseEntity {
    /**
     * 对外步骤编号。
     */
    private String stepNo;
    /**
     * 工作流运行 ID。
     */
    private Long workflowRunId;
    /**
     * 稳定的画布节点 ID。
     */
    private Long canvasNodeId;
    /**
     * 节点键快照。
     */
    private String nodeKey;
    /**
     * 节点类型快照。
     */
    private Integer nodeType;
    /**
     * 工作流内执行序号。
     */
    private Integer sequenceNo;
    /**
     * 当前尝试次数。
     */
    private Integer attemptNo;
    /**
     * 状态：1等待，2运行中，3成功，4失败，5取消，6跳过。
     */
    private Integer stepStatus;
    /**
     * 模型定义 ID。
     */
    private Long modelDefinitionId;
    /**
     * 供应商编码快照。
     */
    private String providerCode;
    /**
     * 模型编码快照。
     */
    private String modelCode;
    /**
     * 模型名称快照。
     */
    private String modelName;
    /**
     * 供应商请求 ID。
     */
    private String providerRequestId;
    /**
     * 实际输入 JSON。
     */
    private String inputSnapshot;
    /**
     * 脱敏请求 JSON。
     */
    private String requestPayload;
    /**
     * 供应商响应 JSON。
     */
    private String responsePayload;
    /**
     * 标准化输出 JSON。
     */
    private String outputSnapshot;
    /**
     * 价格规则 ID。
     */
    private Long priceRuleId;
    /**
     * 价格快照 JSON。
     */
    private String priceSnapshot;
    /**
     * 预计积分。
     */
    private Long estimatedPoints;
    /**
     * 实际消耗积分。
     */
    private Long consumedPoints;
    /**
     * 返还积分。
     */
    private Long refundedPoints;
    /**
     * 积分业务单 ID。
     */
    private Long pointBizOrderId;
    /**
     * 标准化错误码。
     */
    private String errorCode;
    /**
     * 错误说明。
     */
    private String errorMessage;
    /**
     * 供应商错误 JSON。
     */
    private String providerErrorData;
    /**
     * 入队时间。
     */
    private LocalDateTime queuedAt;
    /**
     * 开始时间。
     */
    private LocalDateTime startedAt;
    /**
     * 完成时间。
     */
    private LocalDateTime finishedAt;
    /**
     * 执行耗时毫秒。
     */
    private Long durationMs;
}
