-- MySQL 8.0+ 账户、空间、项目、资产和积分表
-- 积分均使用整数；删除标记约定：1=正常，0=已删除。

CREATE TABLE user_account (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    email               VARCHAR(320) NOT NULL COMMENT '登录邮箱',
    username            VARCHAR(80) NOT NULL COMMENT '用户名',
    avatar_url          VARCHAR(1024) NULL COMMENT '个人版账户头像地址；NULL表示未设置',
    last_workspace_id   BIGINT UNSIGNED NULL COMMENT '用户上次切换并使用的个人或团队空间ID；登录后需重新校验访问权限',
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态：ACTIVE正常，DISABLED禁用',
    deleted             INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
    create_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '创建人',
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '更新人',
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_email (email),
    KEY idx_user_last_workspace (last_workspace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户账户表';

CREATE TABLE workspace (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '空间主键',
    workspace_type      VARCHAR(16) NOT NULL COMMENT '空间类型：PERSONAL个人，TEAM团队',
    name                VARCHAR(120) NOT NULL COMMENT '空间名称',
    owner_user_id       BIGINT UNSIGNED NOT NULL COMMENT '空间创建者用户ID；团队角色另由团队成员表管理',
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '空间状态：ACTIVE正常，DISABLED禁用，DISSOLVED已解散',
    deleted             INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
    create_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '创建人',
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '更新人',
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_workspace_owner_type (owner_user_id, workspace_type, status),
    CONSTRAINT fk_workspace_owner FOREIGN KEY (owner_user_id) REFERENCES user_account (id),
    CONSTRAINT chk_workspace_type CHECK (workspace_type IN ('PERSONAL', 'TEAM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个人或团队工作空间表；个人空间唯一性由应用创建逻辑保证';

ALTER TABLE user_account
    ADD CONSTRAINT fk_user_last_workspace FOREIGN KEY (last_workspace_id) REFERENCES workspace (id);

CREATE TABLE team_member (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '团队成员关系主键',
    team_workspace_id   BIGINT UNSIGNED NOT NULL COMMENT '团队空间ID',
    user_id             BIGINT UNSIGNED NOT NULL COMMENT '成员用户ID',
    avatar_url          VARCHAR(1024) NULL COMMENT '该用户在此团队下显示的头像；NULL时可回退显示个人头像',
    role                VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT '团队角色：OWNER负责人，MEMBER普通成员',
    member_status       VARCHAR(24) NOT NULL DEFAULT 'PENDING_APPROVAL' COMMENT '成员状态：PENDING_APPROVAL待审核，ACTIVE有效，REMOVED已移除',
    approved_by         BIGINT UNSIGNED NULL COMMENT '审核人用户ID',
    joined_at           DATETIME NULL COMMENT '正式加入时间',
    deleted             INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
    create_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '创建人',
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '更新人',
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_member (team_workspace_id, user_id),
    KEY idx_member_user_status (user_id, member_status),
    CONSTRAINT fk_tm_workspace FOREIGN KEY (team_workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_tm_user FOREIGN KEY (user_id) REFERENCES user_account (id),
    CONSTRAINT fk_tm_approver FOREIGN KEY (approved_by) REFERENCES user_account (id),
    CONSTRAINT chk_tm_role CHECK (role IN ('OWNER', 'MEMBER')),
    CONSTRAINT chk_tm_status CHECK (member_status IN ('PENDING_APPROVAL', 'ACTIVE', 'REMOVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='团队成员及权限关系表';

CREATE TABLE team_invitation (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '团队邀请主键',
    team_workspace_id   BIGINT UNSIGNED NOT NULL COMMENT '被邀请加入的团队空间ID',
    invite_token_hash   CHAR(64) NOT NULL COMMENT '邀请令牌SHA-256哈希值，不保存明文令牌',
    created_by          BIGINT UNSIGNED NOT NULL COMMENT '邀请发起人用户ID',
    expires_at          DATETIME NOT NULL COMMENT '邀请链接过期时间',
    invitation_status   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '邀请状态：ACTIVE有效，ACCEPTED已申请，REVOKED已撤销，EXPIRED已过期',
    deleted             INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
    create_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '创建人',
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '更新人',
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_invite_token_hash (invite_token_hash),
    KEY idx_invite_team_status (team_workspace_id, invitation_status, expires_at),
    CONSTRAINT fk_invite_workspace FOREIGN KEY (team_workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_invite_creator FOREIGN KEY (created_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='团队邀请链接表';

CREATE TABLE project_folder (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文件夹主键',
    workspace_id        BIGINT UNSIGNED NOT NULL COMMENT '所属个人或团队空间ID',
    parent_id           BIGINT UNSIGNED NULL COMMENT '父文件夹ID，NULL表示根目录',
    name                VARCHAR(160) NOT NULL COMMENT '文件夹名称',
    created_by          BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID',
    deleted             INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
    create_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '创建人',
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '更新人',
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_folder_workspace (workspace_id, parent_id, deleted),
    CONSTRAINT fk_folder_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_folder_creator FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT fk_folder_parent FOREIGN KEY (parent_id) REFERENCES project_folder (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目文件夹表';

CREATE TABLE canvas_project (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '画布项目主键',
    workspace_id        BIGINT UNSIGNED NOT NULL COMMENT '所属个人或团队空间ID',
    folder_id           BIGINT UNSIGNED NULL COMMENT '所属文件夹ID，NULL表示未放入文件夹',
    name                VARCHAR(160) NOT NULL COMMENT '项目名称',
    created_by          BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID',
    deleted             INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
    create_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '创建人',
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '更新人',
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_project_workspace_folder (workspace_id, folder_id, deleted, update_time),
    CONSTRAINT fk_project_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_project_creator FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT fk_project_folder FOREIGN KEY (folder_id) REFERENCES project_folder (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='画布项目表';

CREATE TABLE asset_folder (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '资产文件夹主键',
    workspace_id        BIGINT UNSIGNED NOT NULL COMMENT '所属个人或团队空间ID',
    parent_id           BIGINT UNSIGNED NULL COMMENT '父文件夹ID；NULL表示根目录',
    folder_name         VARCHAR(160) NOT NULL COMMENT '文件夹名称',
    created_by          BIGINT UNSIGNED NOT NULL COMMENT '文件夹创建人用户ID；团队内仅该成员可重命名或删除',
    deleted             INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
    create_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '创建人',
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '更新人',
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_asset_folder_workspace_parent (workspace_id, parent_id, deleted),
    KEY idx_asset_folder_creator (workspace_id, created_by, deleted),
    CONSTRAINT fk_asset_folder_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_asset_folder_creator FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT fk_asset_folder_parent FOREIGN KEY (parent_id) REFERENCES asset_folder (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户资产文件夹表';

CREATE TABLE asset (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '资产主键',
    workspace_id        BIGINT UNSIGNED NOT NULL COMMENT '资产所属个人或团队空间ID',
    folder_id           BIGINT UNSIGNED NULL COMMENT '所属资产文件夹ID；NULL表示未归入文件夹',
    uploaded_by         BIGINT UNSIGNED NOT NULL COMMENT '上传或生成该资产的用户ID',
    asset_type          VARCHAR(16) NOT NULL COMMENT '资产类型：IMAGE图片，AUDIO音频，VIDEO视频',
    asset_name          VARCHAR(255) NOT NULL COMMENT '资产名称',
    asset_url           VARCHAR(2048) NOT NULL COMMENT '资产上传后的访问URL',
    thumbnail_url       VARCHAR(2048) NULL COMMENT '视频截图或图片缩略图访问URL',
    mime_type           VARCHAR(160) NOT NULL COMMENT '文件MIME类型',
    size_bytes          BIGINT UNSIGNED NOT NULL COMMENT '文件大小，字节',
    width               INT UNSIGNED NULL COMMENT '图片或视频宽度，像素',
    height              INT UNSIGNED NULL COMMENT '图片或视频高度，像素',
    duration_ms         BIGINT UNSIGNED NULL COMMENT '音频或视频时长，毫秒',
    deleted             INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除',
    create_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '创建人',
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '更新人',
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_asset_workspace_folder (workspace_id, folder_id, deleted, create_time),
    KEY idx_asset_workspace_type (workspace_id, asset_type, deleted, create_time),
    KEY idx_asset_uploader (uploaded_by, create_time),
    CONSTRAINT fk_asset_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_asset_folder FOREIGN KEY (folder_id) REFERENCES asset_folder (id),
    CONSTRAINT fk_asset_uploader FOREIGN KEY (uploaded_by) REFERENCES user_account (id),
    CONSTRAINT chk_asset_type CHECK (asset_type IN ('IMAGE', 'AUDIO', 'VIDEO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户上传资产表';

CREATE TABLE asset_tag (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '资产标签主键',
    workspace_id        BIGINT UNSIGNED NOT NULL COMMENT '标签所属账户空间ID；个人标签归个人空间，团队标签归团队空间',
    tag_name            VARCHAR(64) NOT NULL COMMENT '标签名称',
    tag_type            VARCHAR(16) NOT NULL COMMENT '标签类型：SYSTEM固定标签，CUSTOM自定义标签',
    created_by          BIGINT UNSIGNED NULL COMMENT '自定义标签创建人；SYSTEM固定标签为空',
    sort_no             INT NOT NULL DEFAULT 0 COMMENT '显示排序号',
    deleted             INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0删除；固定标签不可删除',
    create_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '创建人',
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '更新人',
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_tag_workspace_name (workspace_id, tag_name),
    KEY idx_asset_tag_workspace_type (workspace_id, tag_type, deleted, sort_no),
    KEY idx_asset_tag_creator (workspace_id, created_by),
    CONSTRAINT fk_asset_tag_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_asset_tag_creator FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT chk_asset_tag_type CHECK (tag_type IN ('SYSTEM', 'CUSTOM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资产标签定义表';

CREATE TABLE asset_tag_relation (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '资产标签关联主键',
    asset_id            BIGINT UNSIGNED NOT NULL COMMENT '资产ID',
    tag_id              BIGINT UNSIGNED NOT NULL COMMENT '标签ID',
    workspace_id        BIGINT UNSIGNED NOT NULL COMMENT '冗余保存空间ID，便于按账户筛选及校验一致性',
    created_by          BIGINT UNSIGNED NOT NULL COMMENT '添加标签的用户ID',
    deleted             INT NOT NULL DEFAULT '1' COMMENT '删除标记：1正常，0已移除',
    create_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '创建人',
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '1' COMMENT '更新人',
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_tag_relation (asset_id, tag_id),
    KEY idx_asset_tag_relation_tag (workspace_id, tag_id, deleted),
    CONSTRAINT fk_asset_tag_relation_asset FOREIGN KEY (asset_id) REFERENCES asset (id),
    CONSTRAINT fk_asset_tag_relation_tag FOREIGN KEY (tag_id) REFERENCES asset_tag (id),
    CONSTRAINT fk_asset_tag_relation_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_asset_tag_relation_creator FOREIGN KEY (created_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资产与标签多对多关联表';



-- 个人积分查询：请求上下文提供currentWorkspaceId和currentUserId，服务端先确认空间归属。
-- SELECT ca.balance FROM credit_account ca
-- WHERE ca.workspace_id = :currentWorkspaceId AND ca.account_type = 'PERSONAL' AND ca.deleted = 1;
--
-- 团队积分及当前成员额度查询：服务端先确认该用户在此团队为ACTIVE成员。
-- SELECT account_type, balance FROM credit_account
-- WHERE workspace_id = :currentWorkspaceId AND deleted = 1 AND
--   ((account_type = 'TEAM' AND member_user_id IS NULL) OR
--    (account_type = 'TEAM_MEMBER' AND member_user_id = :currentUserId));
--
-- 项目及资产按当前workspace过滤，并加deleted=1；不可只信任客户端提交的workspace_id。
