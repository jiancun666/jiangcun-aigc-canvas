package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/** 团队成员关系实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("team_member")
public class TeamMember extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 团队工作空间 ID。 */
    private Long teamWorkspaceId;

    /** 成员用户 ID。 */
    private Long userId;

    /** 用户在该团队下显示的头像。 */
    private String avatarUrl;

    /** 团队角色：OWNER、MEMBER。 */
    private String role;

    /** 成员状态：PENDING_APPROVAL、ACTIVE、REMOVED。 */
    private String memberStatus;

    /** 审核人用户 ID。 */
    private Long approvedBy;

    /** 正式加入团队时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date joinedAt;
}
