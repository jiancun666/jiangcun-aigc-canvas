package com.semple.aigc.canvas.modules.aigc.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 邮箱登录成功响应。 */
@Data
public class EmailLoginResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;
    private String tokenType;
    private LocalDateTime expireAt;
    private Long workspaceId;
    private String workspaceType;
    private String workspaceName;
}
