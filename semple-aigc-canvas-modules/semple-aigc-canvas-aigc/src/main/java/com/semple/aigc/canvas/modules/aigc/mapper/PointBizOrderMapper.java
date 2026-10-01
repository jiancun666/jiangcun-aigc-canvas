package com.semple.aigc.canvas.modules.aigc.mapper;

import com.semple.aigc.canvas.api.aigc.domain.PointBizOrder;
import com.semple.aigc.canvas.api.aigc.mapper.PointBizOrderParentMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.time.LocalDateTime;

/**
 * 生成任务积分业务单数据访问接口。
 */
@Mapper
public interface PointBizOrderMapper extends PointBizOrderParentMapper {
    /**
     * 按生成任务 ID 查询并锁定业务单，用于幂等结算和退款。
     */
    @Select("SELECT * FROM aigc_point_biz_order WHERE generation_task_id=#{taskId} AND deleted=1 FOR UPDATE")
    PointBizOrder selectTaskForUpdate(@Param("taskId") String taskId);

    /**
     * 按原型展示分类汇总实际消耗或返还额，不改变底层预占/结算流水。
     */
    @SelectProvider(type = PointBizOrderSqlProvider.class, method = "sumDisplayPoints")
    Long sumDisplayPoints(@Param("accountId") Long accountId,
                          @Param("category") String category,
                          @Param("memberUserId") Long memberUserId,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);
}
