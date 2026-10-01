package com.semple.aigc.canvas.common.web.context;

import lombok.Data;

import java.io.Serializable;

/** 当前请求已认证的用户和工作空间信息。 */
@Data
public class LoginContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String email;
    private Long workspaceId;
    private String workspaceType;

    public LoginContext() {
    }

    public LoginContext(Long userId, String username, Long workspaceId, String workspaceType) {
        this(userId, username, null, workspaceId, workspaceType);
    }

    public LoginContext(Long userId, String username, String email, Long workspaceId, String workspaceType) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.workspaceId = workspaceId;
        this.workspaceType = workspaceType;
    }
}
