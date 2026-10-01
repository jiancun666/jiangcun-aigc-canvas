package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 不可变积分流水，记录每次余额变更前后的快照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_point_ledger")
public class PointLedger extends BaseEntity {
    /**
     * 关联积分账户 ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    /**
     * 积分所属个人或团队工作区 ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workspaceId;
    /**
     * 流水类型：1 发放，2 预占，3 结算，4 释放预占，5 清零，6 调整。
     */
    private Integer ledgerType;
    /**
     * 积分变动值；增加为正，扣减为负。
     */
    private Long changeAmount;
    /**
     * 变动前积分余额。
     */
    private Long balanceBefore;
    /**
     * 变动后积分余额。
     */
    private Long balanceAfter;
    /**
     * 预占积分变动值；预占为正，结算或释放为负。
     */
    private Long reservedChange;
    /**
     * 变动前预占积分。
     */
    private Long reservedBefore;
    /**
     * 变动后预占积分。
     */
    private Long reservedAfter;
    /**
     * 操作人用户 ID；系统操作时为空。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorUserId;
    /**
     * 团队积分的实际使用成员 ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long memberUserId;
    /**
     * 关联项目项 ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectItemId;
    /**
     * 关联工作流运行 ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workflowRunId;
    /**
     * 关联工作流步骤运行 ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workflowStepRunId;
    /**
     * 生成模型编码快照。
     */
    private String modelCode;
    /**
     * 关联生成任务 ID。
     */
    private String generationTaskId;
    /**
     * 关联积分业务单号。
     */
    private String bizOrderNo;
    /**
     * 关联原流水 ID，例如返还流水对应的消耗流水。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long relatedLedgerId;
    /**
     * 业务幂等键。
     */
    private String idempotencyKey;
    /**
     * 流水备注。
     */
    private String remark;
    /**
     * 扩展业务 JSON 数据。
     */
    private String extraData;
    /**
     * 业务实际发生时间。
     */
    private LocalDateTime occurredAt;
}
