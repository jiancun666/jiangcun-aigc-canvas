package com.semple.aigc.canvas.modules.aigc.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.semple.aigc.canvas.modules.aigc.vo.GenerationHistoryVO;

/** 按类型分页查询当前工作区的生成历史。 */
public interface GenerationHistoryService {
    /** 分页查询指定工作区的生成历史；assetType 为空时查询全部类型。 */
    Page<GenerationHistoryVO> list(Long workspaceId, Integer assetType,
                                   long pageNum, long pageSize, Long userId);
}
