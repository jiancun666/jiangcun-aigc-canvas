package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 基于指定画布版本创建的多节点执行记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_canvas_workflow_execution")
public class CanvasWorkflowExecution extends BaseEntity {
    private Long workspaceId;
    private Long projectItemId;
    private Long canvasId;
    private Long creatorUserId;
    private Integer canvasRevision;
    private String targetNodeKey;
    /**
     * 状态：1运行中，2成功，3部分成功，4失败。
     */
    private Integer executionStatus;
    private String errorMessage;
}
