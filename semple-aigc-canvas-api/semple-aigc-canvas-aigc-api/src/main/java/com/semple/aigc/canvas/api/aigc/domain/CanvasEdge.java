package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 画布节点之间的有向连线。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_canvas_edge")
public class CanvasEdge extends BaseEntity {
    /**
     * 所属画布 ID。
     */
    private Long canvasId;
    /**
     * 前端生成的画布内连线唯一键。
     */
    private String edgeKey;
    /**
     * 来源节点数据库 ID。
     */
    private Long sourceNodeId;
    /**
     * 来源节点输出端口标识。
     */
    private String sourceHandle;
    /**
     * 目标节点数据库 ID。
     */
    private Long targetNodeId;
    /**
     * 目标节点输入端口标识。
     */
    private String targetHandle;
    /**
     * 连线附加配置 JSON 数据。
     */
    private String edgeData;
}
