package com.semple.aigc.canvas.modules.aigc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.semple.aigc.canvas.api.aigc.domain.ProjectItem;

import java.util.List;

/**
 * 项目与文件夹领域服务接口。
 */
public interface ProjectService extends IService<ProjectItem> {
    int FOLDER = 1;
    int PROJECT = 2;
    int ACTIVE = 1;
    int RECYCLED = 2;

    /**
     * 查询空间目录或回收站中的项目项。
     */
    List<ProjectItem> list(Long workspaceId, Long parentId, String keyword,
                           boolean recycle, Long userId);

    /**
     * 查询当前用户可读的项目项。
     */
    ProjectItem detail(Long id, Long userId);

    /**
     * 查询当前用户可写的项目项，供画布等关联业务复用权限规则。
     */
    ProjectItem writableDetail(Long id, Long userId);

    /**
     * 创建文件夹或项目；项目会同步创建默认画布。
     */
    ProjectItem create(Long workspaceId, int itemType, Long parentId, String name,
                       String coverUrl, Long userId);

    /**
     * 重命名项目项。
     */
    ProjectItem rename(Long id, String name, Long userId);

    /**
     * 更新项目封面。
     */
    ProjectItem updateCover(Long id, String coverUrl, Long userId);

    /**
     * 移动项目项到指定父目录。
     */
    ProjectItem move(Long id, Long parentId, Long userId);

    /**
     * 将项目项移入业务回收站。
     */
    void recycle(Long id, Long userId);

    /**
     * 从业务回收站恢复项目项。
     */
    ProjectItem restore(Long id, Long userId);

    /**
     * 在同一事务中恢复回收站中的多个项目或文件夹。
     */
    List<ProjectItem> restoreBatch(List<Long> ids, Long userId);

    /**
     * 切换项目默认画布。
     */
    ProjectItem setDefaultCanvas(Long id, Long canvasId, Long userId);

    /**
     * 分批永久清理超过保留期限的项目树。
     */
    void purgeExpired();
}
