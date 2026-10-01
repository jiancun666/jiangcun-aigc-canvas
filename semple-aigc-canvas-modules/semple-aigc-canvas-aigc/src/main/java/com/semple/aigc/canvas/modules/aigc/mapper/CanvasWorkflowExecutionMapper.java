package com.semple.aigc.canvas.modules.aigc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.semple.aigc.canvas.api.aigc.domain.CanvasWorkflowExecution;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 画布执行记录数据访问接口。
 */
@Mapper
public interface CanvasWorkflowExecutionMapper extends BaseMapper<CanvasWorkflowExecution> {
    /**
     * 永久删除画布时清理执行记录。
     */
    @Delete("DELETE FROM aigc_canvas_workflow_execution WHERE canvas_id=#{canvasId}")
    int physicallyDeleteByCanvasId(@Param("canvasId") Long canvasId);
}
