package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 个人或团队的积分余额与累计统计。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_point_account")
public class PointAccount extends BaseEntity {
    /**
     * 积分所属个人或团队工作区 ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workspaceId;
    /**
     * 当前可用积分余额。
     */
    private Long availableBalance;
    /**
     * 已为运行中生成任务预占、尚未结算的积分。
     */
    private Long reservedBalance;
    /**
     * 累计获得积分。
     */
    private Long acquiredTotal;
    /**
     * 累计消耗积分。
     */
    private Long consumedTotal;
    /**
     * 累计返还积分。
     */
    private Long refundedTotal;
    /**
     * 累计清零积分。
     */
    private Long clearedTotal;
    /**
     * 账户状态：1 正常，2 冻结，3 已关闭。
     */
    private Integer accountStatus;
    /**
     * 账户余额乐观锁版本号。
     */
    private Integer lockVersion;
}
