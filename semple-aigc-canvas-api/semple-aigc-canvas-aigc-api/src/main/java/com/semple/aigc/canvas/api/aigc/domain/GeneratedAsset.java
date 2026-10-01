package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型生成结果与用户资产库之间的可追溯记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_generated_asset")
public class GeneratedAsset extends BaseEntity {
    /**
     * 对外资产编号。
     */
    private String assetNo;
    /**
     * 类型：1文本，2图片，3视频，4音频，5文件。
     */
    private Integer assetType;
    /**
     * 来源：1模型生成，2用户上传，3历史引用。
     */
    private Integer assetSource;
    /**
     * 资产所属工作区 ID。
     */
    private Long workspaceId;
    /**
     * 用户资产库 asset 表 ID。
     */
    private Long assetId;
    /**
     * 项目 ID。
     */
    private Long projectItemId;
    /**
     * 画布 ID。
     */
    private Long canvasId;
    /**
     * 产出节点 ID。
     */
    private Long canvasNodeId;
    /**
     * 工作流运行 ID。
     */
    private Long workflowRunId;
    /**
     * 步骤运行 ID。
     */
    private Long stepRunId;
    /**
     * 步骤内产出序号，从 1 开始；用于生成结果幂等键。
     */
    private Integer outputIndex;
    /**
     * 创建用户 ID。
     */
    private Long creatorUserId;
    /**
     * 模型定义 ID。
     */
    private Long modelDefinitionId;
    /**
     * 资源访问地址。
     */
    private String storageUrl;
    /**
     * 缩略图地址。
     */
    private String thumbnailUrl;
    /**
     * 文本内容。
     */
    private String textContent;
    /**
     * MIME 类型。
     */
    private String mimeType;
    /**
     * 文件大小。
     */
    private Long fileSize;
    /**
     * 内容摘要。
     */
    private String contentHash;
    /**
     * 媒体宽度。
     */
    private Integer width;
    /**
     * 媒体高度。
     */
    private Integer height;
    /**
     * 媒体时长。
     */
    private Long durationMs;
    /**
     * 最终提示词快照。
     */
    private String promptSnapshot;
    /**
     * 生成参数 JSON。
     */
    private String generationConfig;
    /**
     * 供应商及媒体扩展 JSON。
     */
    private String assetMetadata;
    /**
     * 状态：1正常，2处理中，3失效，4已删除。
     */
    private Integer assetStatus;
}
