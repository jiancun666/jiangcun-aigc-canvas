package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 个人或团队空间中的文件夹/项目统一实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_project_item")
public class ProjectItem extends BaseEntity {
    /**
     * 所属个人或团队工作区 ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workspaceId;

    /**
     * 条目类型：1-文件夹，2-项目。
     */
    private Integer itemType;

    /**
     * 当前父文件夹 ID，为空表示位于工作区根目录。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    /**
     * 移入回收站前的父文件夹 ID，用于恢复原位置。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long originalParentId;

    /**
     * 创建者用户 ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long creatorUserId;

    /**
     * 文件夹或项目名称。
     */
    private String name;

    /**
     * 项目封面地址，文件夹不使用该字段。
     */
    private String coverUrl;

    /**
     * 项目默认画布 ID，文件夹不使用该字段。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long canvasId;

    /**
     * 条目状态：1-正常，2-回收站。
     */
    private Integer itemStatus;

    /**
     * 执行删除操作的用户 ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deletedBy;

    /**
     * 移入回收站时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deletedAt;

    /**
     * 计划永久清理时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime purgeAt;

    /**
     * 乐观锁版本号。
     */
    private Integer lockVersion;
}
