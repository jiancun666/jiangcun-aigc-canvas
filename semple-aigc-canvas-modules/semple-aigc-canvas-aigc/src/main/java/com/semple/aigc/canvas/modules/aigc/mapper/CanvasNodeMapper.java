package com.semple.aigc.canvas.modules.aigc.mapper;

import com.semple.aigc.canvas.api.aigc.mapper.CanvasNodeParentMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 画布节点数据访问接口。
 */
@Mapper
public interface CanvasNodeMapper extends CanvasNodeParentMapper {
    /**
     * 永久删除指定画布的节点，用于快照覆盖和项目过期清理。
     */
    @Delete("DELETE FROM aigc_canvas_node WHERE canvas_id=#{canvasId}")
    int physicallyDeleteByCanvasId(@Param("canvasId") Long canvasId);
}
