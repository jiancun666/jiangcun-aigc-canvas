package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户账户实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_account")
public class UserAccount extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 登录邮箱。 */
    private String email;

    /** 用户名。 */
    private String username;

    /** 个人版账户头像地址。 */
    private String avatarUrl;

    /** 用户上次使用的工作空间 ID。 */
    private Long lastWorkspaceId;

    /** 账户状态：ACTIVE、DISABLED。 */
    private String status;
}
