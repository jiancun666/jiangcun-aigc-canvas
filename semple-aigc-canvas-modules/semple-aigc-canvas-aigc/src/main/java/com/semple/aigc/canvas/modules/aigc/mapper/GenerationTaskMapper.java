package com.semple.aigc.canvas.modules.aigc.mapper;

import com.semple.aigc.canvas.api.aigc.domain.GenerationTask;
import com.semple.aigc.canvas.api.aigc.mapper.GenerationTaskParentMapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 生成任务数据访问接口，包含 worker 租约竞争操作。
 */
@Mapper
public interface GenerationTaskMapper extends GenerationTaskParentMapper {
    /**
     * 查询等待提交、到期轮询或租约已过期的任务候选项。
     */
    @Select("SELECT * FROM aigc_generation_task WHERE deleted=1 AND task_status IN (1,2,3) "
            + "AND (next_poll_at IS NULL OR next_poll_at<=NOW(3)) "
            + "AND (lease_until IS NULL OR lease_until<NOW(3)) ORDER BY create_time LIMIT #{limit}")
    List<GenerationTask> selectRunnable(@Param("limit") int limit);

    /**
     * 结算事务内锁定任务行，防止租约过期后的旧 worker 与新 worker 同时结算。
     */
    @Select("SELECT * FROM aigc_generation_task WHERE id=#{id} AND deleted=1 FOR UPDATE")
    GenerationTask selectForUpdate(@Param("id") Long id);

    /**
     * 原子获取任务租约；多实例中只允许一个 worker 成功。
     */
    @Update("UPDATE aigc_generation_task SET lock_owner=#{owner}, "
            + "lease_until=DATE_ADD(NOW(3), INTERVAL #{leaseSeconds} SECOND), "
            + "task_status=IF(task_status=1,2,task_status), attempt_no=attempt_no+1, "
            + "started_at=COALESCE(started_at,NOW(3)) WHERE id=#{id} AND task_status IN (1,2,3) "
            + "AND (lease_until IS NULL OR lease_until<NOW(3)) AND deleted=1")
    int claim(@Param("id") Long id, @Param("owner") String owner,
              @Param("leaseSeconds") int leaseSeconds);
}
