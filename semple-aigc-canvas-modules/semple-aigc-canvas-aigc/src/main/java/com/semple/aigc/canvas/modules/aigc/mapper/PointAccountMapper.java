package com.semple.aigc.canvas.modules.aigc.mapper;

import com.semple.aigc.canvas.api.aigc.domain.PointAccount;
import com.semple.aigc.canvas.api.aigc.mapper.PointAccountParentMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 积分账户数据访问接口。
 */
@Mapper
public interface PointAccountMapper extends PointAccountParentMapper {
    /**
     * 并发安全地尝试创建零余额账户；账户已存在时忽略唯一键冲突。
     */
    @Insert("INSERT IGNORE INTO aigc_point_account "
            + "(workspace_id, available_balance, reserved_balance, acquired_total, consumed_total, refunded_total, "
            + "cleared_total, account_status, lock_version, deleted, create_time, update_time) VALUES "
            + "(#{workspaceId}, 0, 0, 0, 0, 0, 0, 1, 0, 1, NOW(), NOW())")
    int insertIgnore(@Param("workspaceId") Long workspaceId);

    /**
     * 按账户主体查询并获取排他行锁，余额变更必须使用该方法。
     */
    @Select("SELECT * FROM aigc_point_account WHERE workspace_id=#{workspaceId} "
            + "AND deleted=1 FOR UPDATE")
    PointAccount selectWorkspaceForUpdate(@Param("workspaceId") Long workspaceId);
}
