package com.semple.aigc.canvas.modules.aigc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semple.aigc.canvas.api.aigc.domain.Canvas;
import com.semple.aigc.canvas.api.aigc.domain.ProjectItem;
import com.semple.aigc.canvas.api.aigc.domain.ProjectOperationLog;
import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.modules.aigc.mapper.*;
import com.semple.aigc.canvas.modules.aigc.service.ProjectService;
import com.semple.aigc.canvas.modules.aigc.service.SpaceAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;

/**
 * 项目与文件夹领域服务，负责目录树、回收站、权限校验以及审计日志。
 */
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl extends ServiceImpl<ProjectItemMapper, ProjectItem> implements ProjectService {

    private final ProjectItemMapper itemMapper;
    private final ProjectOperationLogMapper logMapper;
    private final CanvasMapper canvasMapper;
    private final CanvasNodeMapper nodeMapper;
    private final CanvasEdgeMapper edgeMapper;
    private final CanvasVersionMapper versionMapper;
    private final CanvasWorkflowExecutionMapper executionMapper;
    private final CanvasWorkflowExecutionStepMapper executionStepMapper;
    private final SpaceAccessService accessService;
    private final ObjectMapper objectMapper;

    @Value("${semple-aigc-canvas.project.recycle-retention-days:30}")
    private int retentionDays;
    @Value("${semple-aigc-canvas.project.purge-batch-size:100}")
    private int purgeBatchSize;

    /**
     * 查询空间中的项目项；普通目录按 parentId 查询，回收站忽略目录层级。
     */
    @Override
    public List<ProjectItem> list(Long workspaceId, Long parentId, String keyword, boolean recycle,
                                  Long userId) {
        accessService.assertReadable(workspaceId, userId);
        if (!recycle && parentId != null) {
            validateParent(parentId, workspaceId);
        }
        LambdaQueryWrapper<ProjectItem> query = new LambdaQueryWrapper<ProjectItem>()
                .eq(ProjectItem::getWorkspaceId, workspaceId)
                .eq(ProjectItem::getItemStatus, recycle ? RECYCLED : ACTIVE)
                .orderByDesc(ProjectItem::getCreateTime);
        if (!recycle) {
            query.eq(parentId != null, ProjectItem::getParentId, parentId)
                    .isNull(parentId == null, ProjectItem::getParentId);
        }
        if (StringUtils.hasText(keyword)) {
            query.like(ProjectItem::getName, keyword.trim());
        }
        return itemMapper.selectList(query);
    }

    /**
     * 获取可读项目项详情。
     */
    @Override
    public ProjectItem detail(Long id, Long userId) {
        ProjectItem item = requireItem(id);
        accessService.assertReadable(item.getWorkspaceId(), userId);
        assertActiveHierarchy(item);
        return item;
    }

    /**
     * 获取可写项目项详情，供画布等关联业务复用项目权限规则。
     */
    @Override
    public ProjectItem writableDetail(Long id, Long userId) {
        ProjectItem item = requireItem(id);
        accessService.assertWritable(item, userId);
        assertActiveHierarchy(item);
        return item;
    }

    /**
     * 创建文件夹或项目。
     * 项目和默认画布必须处于同一事务，避免产生没有画布的半成品项目。
     */
    @Transactional(rollbackFor = {Exception.class})
    @Override
    public ProjectItem create(Long workspaceId, int itemType, Long parentId, String name,
                              String coverUrl, Long userId) {
        validateItemType(itemType);
        accessService.assertReadable(workspaceId, userId);
        validateName(name);
        validateParent(parentId, workspaceId);
        ProjectItem item = new ProjectItem();
        item.setWorkspaceId(workspaceId);
        item.setItemType(itemType);
        item.setParentId(parentId);
        item.setCreatorUserId(userId);
        item.setName(name.trim());
        item.setCoverUrl(coverUrl);
        item.setItemStatus(ACTIVE);
        item.setLockVersion(0);
        itemMapper.insert(item);
        if (itemType == PROJECT) {
            // 项目创建成功后立即创建默认画布，并回填项目的默认画布 ID。
            Canvas canvas = new Canvas();
            canvas.setProjectItemId(item.getId());
            canvas.setName("画布1");
            canvas.setCanvasStatus(ACTIVE);
            canvas.setLockVersion(0);
            canvas.setCreatedBy(userId);
            canvasMapper.insert(canvas);
            item.setCanvasId(canvas.getId());
            itemMapper.updateById(item);
        }
        writeLog(item, userId, 1, null, item);
        return item;
    }

    /**
     * 重命名项目项，并记录变更前后的审计快照。
     */
    @Transactional(rollbackFor = {Exception.class})
    @Override
    public ProjectItem rename(Long id, String name, Long userId) {
        validateName(name);
        ProjectItem item = lockWritable(id, userId);
        assertActiveHierarchy(item);
        ProjectItem before = copy(item);
        item.setName(name.trim());
        item.setLockVersion(item.getLockVersion() + 1);
        itemMapper.updateById(item);
        writeLog(item, userId, 3, before, item);
        return item;
    }

    /**
     * 更新项目或文件夹封面。
     */
    @Transactional(rollbackFor = {Exception.class})
    @Override
    public ProjectItem updateCover(Long id, String coverUrl, Long userId) {
        ProjectItem item = lockWritable(id, userId);
        assertActiveHierarchy(item);
        ProjectItem before = copy(item);
        item.setCoverUrl(StringUtils.hasText(coverUrl) ? coverUrl.trim() : null);
        item.setLockVersion(item.getLockVersion() + 1);
        itemMapper.updateById(item);
        writeLog(item, userId, 4, before, item);
        return item;
    }

    /**
     * 移动项目项，并校验目标目录所属空间及目录环路。
     */
    @Transactional(rollbackFor = {Exception.class})
    @Override
    public ProjectItem move(Long id, Long parentId, Long userId) {
        ProjectItem item = lockWritable(id, userId);
        assertActiveHierarchy(item);
        validateParent(parentId, item.getWorkspaceId());
        assertNoCycle(item, parentId);
        ProjectItem before = copy(item);
        item.setParentId(parentId);
        item.setLockVersion(item.getLockVersion() + 1);
        itemMapper.updateById(item);
        writeLog(item, userId, parentId == null ? 6 : 5, before, item);
        return item;
    }

    /**
     * 逻辑删除项目项。
     * originalParentId 用于后续恢复，purgeAt 控制永久清理时间。
     */
    @Transactional(rollbackFor = {Exception.class})
    @Override
    public void recycle(Long id, Long userId) {
        ProjectItem item = lockWritable(id, userId);
        if (item.getItemStatus() == RECYCLED) {
            return;
        }
        assertActiveHierarchy(item);
        ProjectItem before = copy(item);
        LocalDateTime now = LocalDateTime.now();
        item.setOriginalParentId(item.getParentId());
        item.setParentId(null);
        item.setItemStatus(RECYCLED);
        item.setDeletedBy(userId);
        item.setDeletedAt(now);
        item.setPurgeAt(now.plusDays(retentionDays));
        item.setLockVersion(item.getLockVersion() + 1);
        itemMapper.updateById(item);
        writeLog(item, userId, 7, before, item);
    }

    /**
     * 恢复回收站项目项；原父目录不可用时回退到根目录。
     */
    @Transactional(rollbackFor = {Exception.class})
    @Override
    public ProjectItem restore(Long id, Long userId) {
        ProjectItem item = lockWritable(id, userId);
        if (item.getItemStatus() != RECYCLED) {
            return item;
        }
        ProjectItem before = copy(item);
        Long restoreParent = item.getOriginalParentId();
        if (restoreParent != null) {
            ProjectItem parent = itemMapper.selectById(restoreParent);
            if (parent == null || parent.getItemStatus() != ACTIVE || parent.getItemType() != FOLDER
                    || !item.getWorkspaceId().equals(parent.getWorkspaceId())) {
                restoreParent = null;
            } else {
                try {
                    assertActiveHierarchy(parent);
                } catch (BizException ignored) {
                    restoreParent = null;
                }
            }
        }
        item.setParentId(restoreParent);
        item.setOriginalParentId(null);
        item.setItemStatus(ACTIVE);
        item.setDeletedBy(null);
        item.setDeletedAt(null);
        item.setPurgeAt(null);
        item.setLockVersion(item.getLockVersion() + 1);
        itemMapper.updateById(item);
        writeLog(item, userId, 8, before, item);
        return item;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public List<ProjectItem> restoreBatch(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE, "每次须选择 1 至 100 个项目或文件夹");
        }
        Set<Long> pending = new LinkedHashSet<>(ids.stream().distinct().sorted(Comparator.naturalOrder()).toList());
        Map<Long, Long> originalParents = new HashMap<>();
        for (Long id : pending) {
            originalParents.put(id, requireItem(id).getOriginalParentId());
        }
        List<ProjectItem> restored = new ArrayList<>(pending.size());
        while (!pending.isEmpty()) {
            Long next = pending.stream().filter(id -> !pending.contains(originalParents.get(id)))
                    .findFirst().orElseThrow(() -> new BizException(ErrorCode.CONFLICT, "恢复目录存在循环引用"));
            restored.add(restore(next, userId));
            pending.remove(next);
        }
        return restored;
    }

    /**
     * 校验画布归属并切换项目默认画布。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ProjectItem setDefaultCanvas(Long id, Long canvasId, Long userId) {
        ProjectItem item = lockWritable(id, userId);
        assertActiveHierarchy(item);
        if (item.getItemType() != PROJECT) {
            throw new BizException(ErrorCode.PARAM_ERROR, "目标不是项目");
        }
        Canvas canvas = canvasMapper.selectById(canvasId);
        if (canvas == null || canvas.getCanvasStatus() != ACTIVE || !id.equals(canvas.getProjectItemId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "画布不属于当前项目");
        }
        ProjectItem before = copy(item);
        item.setCanvasId(canvasId);
        item.setLockVersion(item.getLockVersion() + 1);
        itemMapper.updateById(item);
        writeLog(item, userId, 10, before, item);
        return item;
    }

    /**
     * 定时分批永久清理超过保留期限的项目项。
     */
    @Scheduled(cron = "${semple-aigc-canvas.project.purge-cron:0 20 3 * * ?}")
    @Transactional(rollbackFor = {Exception.class})
    @Override
    public void purgeExpired() {
        for (ProjectItem item : itemMapper.selectExpired(LocalDateTime.now(), purgeBatchSize)) {
            purgeTree(item, item.getDeletedBy() == null ? item.getCreatorUserId() : item.getDeletedBy());
        }
    }

    /**
     * 递归永久删除目录树及其画布数据，采用子节点优先顺序。
     */
    private void purgeTree(ProjectItem item, Long operatorId) {
        // 先清理子项再清理父项，保证目录树不会留下孤儿数据。
        List<ProjectItem> children = itemMapper.selectList(new LambdaQueryWrapper<ProjectItem>()
                .eq(ProjectItem::getParentId, item.getId()));
        for (ProjectItem child : children) {
            purgeTree(child, operatorId);
        }
        if (item.getItemType() == PROJECT) {
            // 项目可包含多个画布，永久清理时逐个删除其执行记录和版本快照。
            for (Canvas canvas : canvasMapper.selectList(new LambdaQueryWrapper<Canvas>()
                    .eq(Canvas::getProjectItemId, item.getId()))) {
                executionStepMapper.physicallyDeleteByCanvasId(canvas.getId());
                executionMapper.physicallyDeleteByCanvasId(canvas.getId());
                versionMapper.physicallyDeleteByCanvasId(canvas.getId());
                edgeMapper.physicallyDeleteByCanvasId(canvas.getId());
                nodeMapper.physicallyDeleteByCanvasId(canvas.getId());
                canvasMapper.physicallyDelete(canvas.getId());
            }
        }
        writeLog(item, operatorId, 9, item, null);
        itemMapper.physicallyDelete(item.getId());
    }

    /**
     * 锁定项目项并校验当前用户写权限。
     */
    private ProjectItem lockWritable(Long id, Long userId) {
        // 修改前锁定项目行，串行化同一项目上的重命名、移动和回收操作。
        ProjectItem item = itemMapper.selectForUpdate(id);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "项目或文件夹不存在");
        }
        accessService.assertWritable(item, userId);
        return item;
    }

    /**
     * 按主键获取项目项，不存在时抛出业务异常。
     */
    private ProjectItem requireItem(Long id) {
        ProjectItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "项目或文件夹不存在");
        }
        return item;
    }

    /**
     * 校验父目录存在、有效且与项目项属于同一空间。
     */
    private void validateParent(Long parentId, Long workspaceId) {
        if (parentId == null) {
            return;
        }
        ProjectItem parent = requireItem(parentId);
        if (parent.getItemType() != FOLDER || parent.getItemStatus() != ACTIVE
                || !parent.getWorkspaceId().equals(workspaceId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "目标文件夹无效");
        }
        assertActiveHierarchy(parent);
    }

    /**
     * 回收站文件夹下仍标记为正常的子项目同样不可访问。
     */
    private void assertActiveHierarchy(ProjectItem item) {
        ProjectItem current = item;
        for (int depth = 0; current != null && depth < 100; depth++) {
            if (!Integer.valueOf(ACTIVE).equals(current.getItemStatus())) {
                throw new BizException(ErrorCode.NOT_FOUND, "项目或文件夹不存在");
            }
            Long parentId = current.getParentId();
            if (parentId == null) {
                return;
            }
            ProjectItem parent = itemMapper.selectById(parentId);
            if (parent == null || !item.getWorkspaceId().equals(parent.getWorkspaceId())) {
                throw new BizException(ErrorCode.NOT_FOUND, "项目或文件夹不存在");
            }
            current = parent;
        }
        throw new BizException(ErrorCode.CONFLICT, "项目目录层级无效");
    }

    /**
     * 校验移动目标不在当前项目项的子树中。
     */
    private void assertNoCycle(ProjectItem item, Long parentId) {
        // 沿父链向上查找，防止文件夹被移动到自身或任意子目录中。
        Long cursor = parentId;
        for (int depth = 0; cursor != null && depth < 100; depth++) {
            if (cursor.equals(item.getId())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "不能移动到自身或子文件夹中");
            }
            ProjectItem parent = itemMapper.selectById(cursor);
            cursor = parent == null ? null : parent.getParentId();
        }
    }

    /**
     * 按 Unicode 码点校验名称非空且不超过 10 个字符。
     */
    private void validateName(String name) {
        if (!StringUtils.hasText(name) || name.trim().codePointCount(0, name.trim().length()) > 10) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE, "名称不能为空且最多 10 个字符");
        }
    }

    /**
     * 校验项目项类型枚举值。
     */
    private void validateItemType(int itemType) {
        if (itemType != FOLDER && itemType != PROJECT) {
            throw new BizException(ErrorCode.PARAM_ERROR, "itemType 只能是 1 或 2");
        }
    }

    /**
     * 复制项目项，供审计日志保存修改前快照。
     */
    private ProjectItem copy(ProjectItem source) {
        return objectMapper.convertValue(source, ProjectItem.class);
    }

    /**
     * 写入项目操作审计日志。
     */
    private void writeLog(ProjectItem item, Long operatorId, int operation, Object before, Object after) {
        // 审计日志与业务变更同事务写入，业务回滚时日志也随之回滚。
        ProjectOperationLog log = new ProjectOperationLog();
        log.setProjectItemId(item.getId());
        log.setWorkspaceId(item.getWorkspaceId());
        log.setItemType(item.getItemType());
        log.setOperatorUserId(operatorId);
        log.setCreatorUserId(item.getCreatorUserId());
        log.setOperationType(operation);
        try {
            log.setBeforeData(before == null ? null : objectMapper.writeValueAsString(before));
            log.setAfterData(after == null ? null : objectMapper.writeValueAsString(after));
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "操作日志序列化失败");
        }
        logMapper.insert(log);
    }
}
