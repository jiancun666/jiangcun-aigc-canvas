package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目的画布元数据与当前快照版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_canvas")
public class Canvas extends BaseEntity {
    /**
     * 所属项目项 ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectItemId;
    /**
     * 画布名称。
     */
    private String name;
    /**
     * 画布状态：1 正常，2 已删除。
     */
    private Integer canvasStatus;
    /**
     * 缩放比例、中心点等视口 JSON 数据。
     */
    private String viewportData;
    /**
     * 画布级配置 JSON 数据。
     */
    private String canvasSetting;
    /**
     * 快照乐观锁版本号。
     */
    private Integer lockVersion;
    /**
     * 画布创建人用户 ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;
}
