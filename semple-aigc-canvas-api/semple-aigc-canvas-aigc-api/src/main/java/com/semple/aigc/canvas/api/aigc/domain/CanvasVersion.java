package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 指定版本的画布内容快照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_canvas_version")
public class CanvasVersion extends BaseEntity {
    private Long canvasId;
    private Integer revision;
    private String snapshotJson;
    private Long createdByUserId;
}
