package com.semple.aigc.canvas.modules.aigc.mapper;

import com.semple.aigc.canvas.api.aigc.mapper.CanvasEdgeParentMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 画布连线数据访问接口。
 */
@Mapper
public interface CanvasEdgeMapper extends CanvasEdgeParentMapper {
    /**
     * 永久删除指定画布的连线，用于快照覆盖和项目过期清理。
     */
    @Delete("DELETE FROM aigc_canvas_edge WHERE canvas_id=#{canvasId}")
    int physicallyDeleteByCanvasId(@Param("canvasId") Long canvasId);

    /** 永久删除一条已从画布快照移除的连线。 */
    @Delete("DELETE FROM aigc_canvas_edge WHERE id=#{id}")
    int physicallyDeleteById(@Param("id") Long id);
}
