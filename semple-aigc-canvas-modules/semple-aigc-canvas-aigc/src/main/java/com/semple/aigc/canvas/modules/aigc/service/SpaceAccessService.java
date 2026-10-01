package com.semple.aigc.canvas.modules.aigc.service;

import com.semple.aigc.canvas.api.aigc.domain.ProjectItem;

/**
 * 个人与团队空间权限服务接口。
 */
public interface SpaceAccessService {
    /**
     * 校验当前用户能否读取个人或团队空间。
     */
    void assertReadable(Long workspaceId, Long userId);

    /**
     * 校验当前用户能否修改指定项目项。
     */
    void assertWritable(ProjectItem item, Long userId);
}
