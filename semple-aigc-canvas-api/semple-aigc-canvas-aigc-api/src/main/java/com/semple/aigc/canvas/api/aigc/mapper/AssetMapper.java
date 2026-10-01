package com.semple.aigc.canvas.api.aigc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.semple.aigc.canvas.api.aigc.domain.Asset;
import org.apache.ibatis.annotations.Mapper;

/** 用户上传资产 Mapper。 */
@Mapper
public interface AssetMapper extends BaseMapper<Asset> {
}
