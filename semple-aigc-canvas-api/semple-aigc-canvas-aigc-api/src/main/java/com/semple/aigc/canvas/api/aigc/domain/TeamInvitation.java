package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/** 团队邀请链接实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("team_invitation")
public class TeamInvitation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 被邀请加入的团队工作空间 ID。 */
    private Long teamWorkspaceId;

    /** 邀请令牌 SHA-256 哈希值。 */
    private String inviteTokenHash;

    /** 邀请发起人用户 ID。 */
    private Long createdBy;

    /** 邀请链接过期时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date expiresAt;

    /** 邀请状态：ACTIVE、ACCEPTED、REVOKED、EXPIRED。 */
    private String invitationStatus;
}
