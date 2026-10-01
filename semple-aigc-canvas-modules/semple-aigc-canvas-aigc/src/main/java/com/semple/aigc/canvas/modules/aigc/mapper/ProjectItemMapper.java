package com.semple.aigc.canvas.modules.aigc.mapper;

import com.semple.aigc.canvas.api.aigc.domain.ProjectItem;
import com.semple.aigc.canvas.api.aigc.mapper.ProjectItemParentMapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 项目和文件夹数据访问接口。
 */
@Mapper
public interface ProjectItemMapper extends ProjectItemParentMapper {
    /**
     * 按主键查询并获取排他行锁，用于项目项变更。
     */
    @Select("SELECT * FROM aigc_project_item WHERE id = #{id} AND deleted=1 FOR UPDATE")
    ProjectItem selectForUpdate(@Param("id") Long id);

    /**
     * 分批查询已超过回收站保留期限的项目项。
     */
    @Select("SELECT * FROM aigc_project_item WHERE item_status=2 AND purge_at<=#{now} AND deleted=1 "
            + "ORDER BY purge_at LIMIT #{limit}")
    List<ProjectItem> selectExpired(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 绕过逻辑删除语义执行永久物理删除，仅供过期清理任务使用。
     */
    @Delete("DELETE FROM aigc_project_item WHERE id=#{id}")
    int physicallyDelete(@Param("id") Long id);
}
