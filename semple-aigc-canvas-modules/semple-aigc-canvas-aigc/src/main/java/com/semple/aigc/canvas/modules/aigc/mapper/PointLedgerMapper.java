package com.semple.aigc.canvas.modules.aigc.mapper;

import com.semple.aigc.canvas.api.aigc.domain.PointLedger;
import com.semple.aigc.canvas.api.aigc.mapper.PointLedgerParentMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 不可变积分流水数据访问接口。
 */
@Mapper
public interface PointLedgerMapper extends PointLedgerParentMapper {
    /**
     * 按幂等键查询并锁定流水，防止同一业务动作重复记账。
     */
    @Select("SELECT * FROM aigc_point_ledger WHERE idempotency_key=#{key} AND deleted=1 FOR UPDATE")
    PointLedger selectKeyForUpdate(@Param("key") String key);
}
