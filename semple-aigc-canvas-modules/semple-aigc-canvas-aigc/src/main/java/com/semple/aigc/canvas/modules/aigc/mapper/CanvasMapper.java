package com.semple.aigc.canvas.modules.aigc.mapper;

import com.semple.aigc.canvas.api.aigc.domain.Canvas;
import com.semple.aigc.canvas.api.aigc.mapper.CanvasParentMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 画布数据访问接口。
 */
@Mapper
public interface CanvasMapper extends CanvasParentMapper {
    /**
     * 按主键查询并获取排他行锁，用于串行化画布保存。
     */
    @Select("SELECT * FROM aigc_canvas WHERE id=#{id} AND deleted=1 FOR UPDATE")
    Canvas selectForUpdate(@Param("id") Long id);

    /**
     * 永久删除画布，仅供回收站过期清理使用。
     */
    @Delete("DELETE FROM aigc_canvas WHERE id=#{id}")
    int physicallyDelete(@Param("id") Long id);
}
