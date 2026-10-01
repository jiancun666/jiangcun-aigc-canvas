package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 个人或团队工作空间实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("workspace")
public class Workspace extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 空间类型：PERSONAL、TEAM。 */
    private String workspaceType;

    /** 空间名称；团队空间名称即团队名称。 */
    private String name;

    /** 空间创建者用户 ID。 */
    private Long ownerUserId;

    /** 空间状态：ACTIVE、DISABLED、DISSOLVED。 */
    private String status;
}
