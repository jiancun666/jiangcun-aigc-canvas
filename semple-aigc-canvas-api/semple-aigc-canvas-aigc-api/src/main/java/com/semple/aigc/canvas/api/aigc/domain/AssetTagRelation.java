package com.semple.aigc.canvas.api.aigc.domain;

import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 资产与标签关联实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("asset_tag_relation")
public class AssetTagRelation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 资产 ID。 */
    private Long assetId;

    /** 标签 ID。 */
    private Long tagId;

    /** 所属工作空间 ID。 */
    private Long workspaceId;

    /** 添加标签的用户 ID。 */
    private Long createdBy;
}
