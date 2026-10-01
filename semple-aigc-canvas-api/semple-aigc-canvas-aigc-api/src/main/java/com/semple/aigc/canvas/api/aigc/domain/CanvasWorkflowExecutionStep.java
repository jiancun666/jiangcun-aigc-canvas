package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 画布执行中单个节点的输入、依赖与结果快照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_canvas_workflow_execution_step")
public class CanvasWorkflowExecutionStep extends BaseEntity {
    private Long executionId;
    private Long canvasNodeId;
    private String nodeKey;
    private Integer nodeType;
    private Integer sequenceNo;
    private Long modelDefinitionId;
    private String prompt;
    private String modelConfig;
    private String dependencies;
    private String taskNo;
    private String outputSnapshot;
    /**
     * 状态：1等待，2已提交，3成功，4失败，5跳过。
     */
    private Integer stepStatus;
    private String errorMessage;
}
