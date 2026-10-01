package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 生成任务对应的积分扣减、结算与退款业务单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_point_biz_order")
public class PointBizOrder extends BaseEntity {
    /**
     * 积分业务单号。
     */
    private String bizOrderNo;
    /**
     * 生成任务唯一 ID，用于防止重复扣费。
     */
    private String generationTaskId;
    /**
     * 扣费积分账户 ID。
     */
    private Long accountId;
    /**
     * 积分所属个人或团队工作区 ID。
     */
    private Long workspaceId;
    /**
     * 发起生成任务的成员用户 ID。
     */
    private Long memberUserId;
    /**
     * 关联项目项 ID。
     */
    private Long projectItemId;
    /**
     * 关联工作流运行 ID。
     */
    private Long workflowRunId;
    /**
     * 关联工作流步骤运行 ID。
     */
    private Long workflowStepRunId;
    /**
     * 调用的模型定义 ID。
     */
    private Long modelDefinitionId;
    /**
     * 命中的服务端价格规则 ID。
     */
    private Long priceRuleId;
    /**
     * 扣费时的模型编码快照。
     */
    private String modelCode;
    /**
     * 本次任务应扣积分。
     */
    private Long costPoints;
    /**
     * 供应商完成任务后最终结算的积分。
     */
    private Long actualPoints;
    /**
     * 下单时冻结的价格规则及计费参数 JSON。
     */
    private String priceSnapshot;
    /**
     * 供应商实际用量 JSON。
     */
    private String usageSnapshot;
    /**
     * 业务单状态：1 已预占，2 运行中，3 已结算，4 已退款，5 结算不确定，6 关闭。
     */
    private Integer orderStatus;
    /**
     * 关联消耗流水 ID。
     */
    private Long consumeLedgerId;
    /**
     * 关联返还流水 ID。
     */
    private Long refundLedgerId;
    /**
     * 生成失败编码。
     */
    private String failureCode;
    /**
     * 生成失败说明。
     */
    private String failureMessage;
    /**
     * 积分扣减时间。
     */
    private LocalDateTime chargedAt;
    /**
     * 生成任务完成时间。
     */
    private LocalDateTime finishedAt;
    /**
     * 积分返还时间。
     */
    private LocalDateTime refundedAt;
    /**
     * 业务单乐观锁版本号。
     */
    private Integer lockVersion;
}
