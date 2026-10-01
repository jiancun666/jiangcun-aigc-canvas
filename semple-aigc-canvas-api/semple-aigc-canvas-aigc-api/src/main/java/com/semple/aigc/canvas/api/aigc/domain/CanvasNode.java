package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;

import java.math.BigDecimal;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 画布节点及其编辑态模型配置。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_canvas_node")
public class CanvasNode extends BaseEntity {
    /**
     * 所属画布 ID。
     */
    private Long canvasId;
    /**
     * 前端生成的画布内节点唯一键。
     */
    private String nodeKey;
    /**
     * 节点类型：1 文本，2 图片，3 视频，4 音频，5 上传，6 历史资产，7 普通处理。
     */
    private Integer nodeType;
    /**
     * 节点展示名称。
     */
    private String name;
    /**
     * 所选模型定义 ID；非模型节点为空。
     */
    private Long modelDefinitionId;
    /**
     * 节点在画布中的 X 坐标。
     */
    private BigDecimal positionX;
    /**
     * 节点在画布中的 Y 坐标。
     */
    private BigDecimal positionY;
    /**
     * 节点宽度。
     */
    private BigDecimal width;
    /**
     * 节点高度。
     */
    private BigDecimal height;
    /**
     * 输入定义、提示词及上游映射 JSON。
     */
    private String inputConfig;
    /**
     * 模型尺寸、比例、时长等参数 JSON。
     */
    private String modelConfig;
    /**
     * 节点其他编辑态 JSON 数据。
     */
    private String nodeData;
    /**
     * 节点状态：1 正常，2 禁用，3 已删除。
     */
    private Integer nodeStatus;
    /**
     * 节点乐观锁版本号。
     */
    private Integer lockVersion;
    /**
     * 节点创建人用户 ID。
     */
    private Long createdBy;
}
