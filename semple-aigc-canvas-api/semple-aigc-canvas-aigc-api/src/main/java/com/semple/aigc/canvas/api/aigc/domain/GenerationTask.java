package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 可恢复、可轮询的异步模型生成任务。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_generation_task")
public class GenerationTask extends BaseEntity {
    /**
     * 对外任务编号。
     */
    private String taskNo;
    /**
     * 工作区范围内的客户端幂等请求号。
     */
    private String clientRequestId;
    /**
     * 扣费工作区 ID。
     */
    private Long workspaceId;
    /**
     * 创建用户 ID。
     */
    private Long creatorUserId;
    /**
     * 项目 ID。
     */
    private Long projectItemId;
    /**
     * 画布 ID。
     */
    private Long canvasId;
    /**
     * 稳定画布节点 ID。
     */
    private Long canvasNodeId;
    /**
     * 工作流运行 ID。
     */
    private Long workflowRunId;
    /**
     * 工作流步骤运行 ID。
     */
    private Long workflowStepRunId;
    /**
     * 模型定义 ID。
     */
    private Long modelDefinitionId;
    /**
     * 价格规则 ID。
     */
    private Long priceRuleId;
    /**
     * 积分业务单 ID。
     */
    private Long pointBizOrderId;
    /**
     * 重试来源任务 ID。
     */
    private Long retryOfTaskId;
    /**
     * 状态：1等待，2提交中，3供应商处理中，4成功，5失败，6取消。
     */
    private Integer taskStatus;
    /**
     * 进度百分比。
     */
    private Integer progress;
    /**
     * worker 尝试次数。
     */
    private Integer attemptNo;
    /**
     * 供应商请求 ID。
     */
    private String providerRequestId;
    /**
     * 最终提示词快照。
     */
    private String prompt;
    /**
     * 请求配置 JSON。
     */
    private String requestConfig;
    /**
     * 标准化结果 JSON。
     */
    private String resultPayload;
    /**
     * 标准化错误码。
     */
    private String errorCode;
    /**
     * 错误说明。
     */
    private String errorMessage;
    /**
     * 是否已请求取消：0否，1是。
     */
    private Integer cancelRequested;
    /**
     * 当前 worker 标识。
     */
    private String lockOwner;
    /**
     * worker 租约到期时间。
     */
    private LocalDateTime leaseUntil;
    /**
     * 下次轮询时间。
     */
    private LocalDateTime nextPollAt;
    /**
     * 任务超时时间。
     */
    private LocalDateTime expiresAt;
    /**
     * 开始时间。
     */
    private LocalDateTime startedAt;
    /**
     * 完成时间。
     */
    private LocalDateTime finishedAt;
}
