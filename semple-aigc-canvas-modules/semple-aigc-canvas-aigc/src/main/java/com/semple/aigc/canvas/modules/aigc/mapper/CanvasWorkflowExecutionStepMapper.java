package com.semple.aigc.canvas.modules.aigc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.semple.aigc.canvas.api.aigc.domain.CanvasWorkflowExecutionStep;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 画布执行步骤数据访问接口。
 */
@Mapper
public interface CanvasWorkflowExecutionStepMapper extends BaseMapper<CanvasWorkflowExecutionStep> {
    /**
     * 永久删除画布时清理关联步骤。
     */
    @Delete("DELETE s FROM aigc_canvas_workflow_execution_step s "
            + "JOIN aigc_canvas_workflow_execution e ON e.id=s.execution_id WHERE e.canvas_id=#{canvasId}")
    int physicallyDeleteByCanvasId(@Param("canvasId") Long canvasId);
}
