package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目及文件夹操作的审计快照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_project_operation_log")
public class ProjectOperationLog extends BaseEntity {
    /**
     * 被操作的项目或文件夹 ID。
     */
    private Long projectItemId;

    /**
     * 所属个人或团队工作区 ID。
     */
    private Long workspaceId;

    /**
     * 条目类型：1-文件夹，2-项目。
     */
    private Integer itemType;

    /**
     * 操作人用户 ID。
     */
    private Long operatorUserId;

    /**
     * 条目创建者用户 ID。
     */
    private Long creatorUserId;

    /**
     * 操作类型：1-创建，2-重命名，3-移动，4-移入回收站，5-恢复，6-永久删除，7-复制，8-更新封面，9-其他。
     */
    private Integer operationType;

    /**
     * 操作前的数据快照（JSON）。
     */
    private String beforeData;

    /**
     * 操作后的数据快照（JSON）。
     */
    private String afterData;

    /**
     * 请求链路标识，用于幂等校验和问题追踪。
     */
    private String requestId;
}
