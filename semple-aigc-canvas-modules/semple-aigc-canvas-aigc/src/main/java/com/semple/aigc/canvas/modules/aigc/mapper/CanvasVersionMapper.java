package com.semple.aigc.canvas.modules.aigc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.semple.aigc.canvas.api.aigc.domain.CanvasVersion;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 画布版本快照数据访问接口。
 */
@Mapper
public interface CanvasVersionMapper extends BaseMapper<CanvasVersion> {
    /**
     * 永久删除画布时清理版本快照。
     */
    @Delete("DELETE FROM aigc_canvas_version WHERE canvas_id=#{canvasId}")
    int physicallyDeleteByCanvasId(@Param("canvasId") Long canvasId);
}
