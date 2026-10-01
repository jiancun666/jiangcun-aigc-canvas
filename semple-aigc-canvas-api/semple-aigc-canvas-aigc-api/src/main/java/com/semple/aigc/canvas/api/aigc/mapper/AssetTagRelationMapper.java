package com.semple.aigc.canvas.api.aigc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.semple.aigc.canvas.api.aigc.domain.AssetTagRelation;
import org.apache.ibatis.annotations.Mapper;

/** 资产与标签关联 Mapper。 */
@Mapper
public interface AssetTagRelationMapper extends BaseMapper<AssetTagRelation> {
}
