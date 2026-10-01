package com.semple.aigc.canvas.api.aigc.domain;

import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户资产文件夹实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("asset_folder")
public class AssetFolder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属个人或团队工作空间 ID。 */
    private Long workspaceId;

    /** 父文件夹 ID；NULL 表示根目录。 */
    private Long parentId;

    /** 文件夹名称。 */
    private String folderName;

    /** 文件夹创建人用户 ID。 */
    private Long createdBy;
}
