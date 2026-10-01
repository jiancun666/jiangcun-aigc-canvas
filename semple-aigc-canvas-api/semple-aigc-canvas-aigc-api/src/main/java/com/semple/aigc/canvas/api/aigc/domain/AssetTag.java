package com.semple.aigc.canvas.api.aigc.domain;

import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 资产标签定义实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("asset_tag")
public class AssetTag extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 标签所属个人或团队工作空间 ID。 */
    private Long workspaceId;

    /** 标签名称。 */
    private String tagName;

    /** 标签类型：SYSTEM 固定标签，CUSTOM 自定义标签。 */
    private String tagType;

    /** 自定义标签创建人；固定标签为空。 */
    private Long createdBy;

    /** 显示排序号。 */
    private Integer sortNo;
}
