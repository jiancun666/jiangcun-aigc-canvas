package com.semple.aigc.canvas.api.aigc.domain;

import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户上传资产实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("asset")
public class Asset extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 资产所属个人或团队工作空间 ID。 */
    private Long workspaceId;

    /** 所属资产文件夹 ID；NULL 表示未归入文件夹。 */
    private Long folderId;

    /** 上传用户 ID。 */
    private Long uploadedBy;

    /** 资产类型：IMAGE、AUDIO、VIDEO。 */
    private String assetType;

    /** 资产名称。 */
    private String assetName;

    /** 资产上传后的访问 URL。 */
    private String assetUrl;

    /** 视频截图或图片缩略图访问 URL。 */
    private String thumbnailUrl;

    /** 文件 MIME 类型。 */
    private String mimeType;

    /** 文件大小，字节。 */
    private Long sizeBytes;

    /** 图片或视频宽度，像素。 */
    private Integer width;

    /** 图片或视频高度，像素。 */
    private Integer height;

    /** 音频或视频时长，毫秒。 */
    private Long durationMs;
}
