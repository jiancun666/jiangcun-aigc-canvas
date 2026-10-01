package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 一次画布或单节点运行的聚合记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_workflow_run")
public class WorkflowRun extends BaseEntity {
    /**
     * 对外运行编号。
     */
    private String runNo;
    /**
     * 所属项目 ID。
     */
    private Long projectItemId;
    /**
     * 所属画布 ID。
     */
    private Long canvasId;
    /**
     * 触发用户 ID。
     */
    private Long triggerUserId;
    /**
     * 扣费积分账户 ID。
     */
    private Long pointAccountId;
    /**
     * 触发方式：1手动，2重试，3接口。
     */
    private Integer triggerType;
    /**
     * 状态：1等待，2运行中，3成功，4部分成功，5失败，6取消。
     */
    private Integer runStatus;
    /**
     * 本次运行冻结的节点、连线和配置 JSON。
     */
    private String workflowSnapshot;
    /**
     * 预计积分。
     */
    private Long estimatedPoints;
    /**
     * 实际消耗积分。
     */
    private Long consumedPoints;
    /**
     * 实际返还积分。
     */
    private Long refundedPoints;
    /**
     * 总步骤数。
     */
    private Integer totalSteps;
    /**
     * 成功步骤数。
     */
    private Integer successSteps;
    /**
     * 失败步骤数。
     */
    private Integer failedSteps;
    /**
     * 开始时间。
     */
    private LocalDateTime startedAt;
    /**
     * 结束时间。
     */
    private LocalDateTime finishedAt;
}
