package com.semple.aigc.canvas.modules.aigc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semple.aigc.canvas.api.aigc.domain.Canvas;
import com.semple.aigc.canvas.api.aigc.domain.CanvasEdge;
import com.semple.aigc.canvas.api.aigc.domain.CanvasNode;
import com.semple.aigc.canvas.api.aigc.domain.CanvasVersion;
import com.semple.aigc.canvas.api.aigc.domain.ProjectItem;
import com.semple.aigc.canvas.api.aigc.domain.Asset;
import com.semple.aigc.canvas.api.aigc.domain.GeneratedAsset;
import com.semple.aigc.canvas.api.aigc.mapper.AssetMapper;
import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasEdgeMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasNodeMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasVersionMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.ProjectItemMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.GeneratedAssetMapper;
import com.semple.aigc.canvas.modules.aigc.service.CanvasService;
import com.semple.aigc.canvas.modules.aigc.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 画布快照服务，统一管理画布元数据、节点和连线。
 */
@Service
@RequiredArgsConstructor
public class CanvasServiceImpl extends ServiceImpl<CanvasMapper, Canvas> implements CanvasService {
    private final CanvasMapper canvasMapper;
    private final CanvasNodeMapper nodeMapper;
    private final CanvasEdgeMapper edgeMapper;
    private final ProjectService projectService;
    private final ProjectItemMapper projectItemMapper;
    private final CanvasVersionMapper versionMapper;
    private final ObjectMapper objectMapper;
    private final AssetMapper assetMapper;
    private final GeneratedAssetMapper generatedAssetMapper;

    /**
     * 获取当前用户可读的完整画布文档。
     */
    @Override
    public CanvasDocument get(Long canvasId, Long userId) {
        Canvas canvas = requireCanvas(canvasId);
        projectService.detail(canvas.getProjectItemId(), userId);
        return document(canvas);
    }

    /**
     * 查询项目中的有效画布。
     */
    @Override
    public List<Canvas> listByProject(Long projectId, Long userId) {
        ProjectItem project = projectService.detail(projectId, userId);
        if (!Integer.valueOf(2).equals(project.getItemType())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "目标不是项目");
        }
        return canvasMapper.selectList(new LambdaQueryWrapper<Canvas>()
                .eq(Canvas::getProjectItemId, projectId)
                .eq(Canvas::getCanvasStatus, 1)
                .orderByAsc(Canvas::getCreateTime));
    }

    /**
     * 创建项目画布。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Canvas create(Long projectId, String name, Long userId) {
        ProjectItem project = projectService.writableDetail(projectId, userId);
        if (!Integer.valueOf(2).equals(project.getItemType()) || !Integer.valueOf(1).equals(project.getItemStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "目标不是有效项目");
        }
        requireText(name, 40, "画布名称");
        Canvas canvas = new Canvas();
        canvas.setProjectItemId(projectId);
        canvas.setName(name.trim());
        canvas.setCanvasStatus(1);
        canvas.setLockVersion(0);
        canvas.setCreatedBy(userId);
        canvasMapper.insert(canvas);
        return canvas;
    }

    /**
     * 删除非默认画布，并与默认画布切换串行执行。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(Long canvasId, Long userId) {
        Canvas candidate = canvasMapper.selectById(canvasId);
        if (candidate == null || !Integer.valueOf(1).equals(candidate.getCanvasStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "画布不存在");
        }
        ProjectItem project = projectService.writableDetail(candidate.getProjectItemId(), userId);
        // 与默认画布切换共用项目行锁，再锁定画布，避免并发删除默认画布。
        project = projectItemMapper.selectForUpdate(project.getId());
        Canvas canvas = canvasMapper.selectForUpdate(canvasId);
        if (canvas == null || !Integer.valueOf(1).equals(canvas.getCanvasStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "画布不存在");
        }
        if (project == null || !project.getId().equals(canvas.getProjectItemId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "项目不存在");
        }
        if (canvasId.equals(project.getCanvasId())) {
            throw new BizException(ErrorCode.CONFLICT, "默认画布不能删除，请先切换默认画布");
        }
        canvas.setCanvasStatus(2);
        canvasMapper.updateById(canvas);
    }

    /**
     * 查询画布版本摘要。
     */
    @Override
    public List<VersionInfo> versions(Long canvasId, Long userId) {
        get(canvasId, userId);
        return versionMapper.selectList(new LambdaQueryWrapper<CanvasVersion>()
                        .eq(CanvasVersion::getCanvasId, canvasId)
                        .orderByDesc(CanvasVersion::getRevision))
                .stream().map(version -> new VersionInfo(version.getRevision(),
                        version.getCreateTime(), version.getCreatedByUserId())).toList();
    }

    /**
     * 读取指定版本的完整画布内容。
     */
    @Override
    public SaveCanvasCommand version(Long canvasId, Integer revision, Long userId) {
        get(canvasId, userId);
        CanvasVersion version = versionMapper.selectOne(new LambdaQueryWrapper<CanvasVersion>()
                .eq(CanvasVersion::getCanvasId, canvasId).eq(CanvasVersion::getRevision, revision));
        if (version == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "画布版本不存在");
        }
        try {
            return objectMapper.readValue(version.getSnapshotJson(), SaveCanvasCommand.class);
        } catch (Exception error) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "画布版本无法读取");
        }
    }

    /**
     * 将历史内容保存为画布的新版本。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public CanvasDocument restore(Long canvasId, Integer revision, Integer expectedRevision, Long userId) {
        SaveCanvasCommand old = version(canvasId, revision, userId);
        return save(canvasId, new SaveCanvasCommand(old.name(), expectedRevision, old.viewport(),
                old.settings(), old.nodes(), old.edges()), userId);
    }

    /**
     * 记录当前画布内容快照，重复版本不再插入。
     */
    @Override
    public void recordVersion(Long canvasId, Long userId) {
        archive(document(requireCanvas(canvasId)), userId);
    }

    /**
     * 全量保存画布快照。
     * 通过数据库行锁和 revision 双重校验防止多个编辑请求相互覆盖。
     */
    @Transactional(rollbackFor = {Exception.class})
    @Override
    public CanvasDocument save(Long canvasId, SaveCanvasCommand command, Long userId) {
        Canvas canvas = canvasMapper.selectForUpdate(canvasId);
        if (canvas == null || canvas.getCanvasStatus() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "画布不存在");
        }
        ProjectItem project = projectService.writableDetail(canvas.getProjectItemId(), userId);
        validateCommand(command);
        validateMediaReferences(command, project == null ? null : project.getWorkspaceId());
        // revision 由客户端读取画布时获得；不一致说明画布已被其他请求修改。
        if (!canvas.getLockVersion().equals(command.revision())) {
            throw new BizException(ErrorCode.CONFLICT, "画布已被其他请求更新，请刷新后重试");
        }
        archive(document(canvas), userId);
        canvas.setName(command.name());
        canvas.setViewportData(json(command.viewport()));
        canvas.setCanvasSetting(json(command.settings()));
        canvas.setLockVersion(canvas.getLockVersion() + 1);
        canvasMapper.updateById(canvas);

        // 节点按 nodeKey 差量更新，确保生成任务和资产引用的数据库 ID 永久稳定。
        Map<String, CanvasNode> existingNodes = new HashMap<>();
        for (CanvasNode node : nodeMapper.selectList(new LambdaQueryWrapper<CanvasNode>()
                .eq(CanvasNode::getCanvasId, canvasId))) {
            existingNodes.put(node.getNodeKey(), node);
        }
        Map<String, Long> nodeIds = new HashMap<>();
        for (NodeCommand source : command.nodes()) {
            CanvasNode node = existingNodes.remove(source.nodeKey());
            boolean insert = node == null;
            if (insert) {
                node = new CanvasNode();
                node.setCanvasId(canvasId);
                node.setNodeKey(source.nodeKey());
                node.setLockVersion(0);
                node.setCreatedBy(userId);
            }
            node.setNodeType(source.nodeType());
            node.setName(source.name());
            node.setModelDefinitionId(source.modelDefinitionId());
            node.setPositionX(source.x());
            node.setPositionY(source.y());
            node.setWidth(source.width());
            node.setHeight(source.height());
            node.setInputConfig(json(source.inputConfig()));
            node.setModelConfig(json(source.modelConfig()));
            node.setNodeData(json(source.data()));
            node.setNodeStatus(1);
            if (insert) {
                nodeMapper.insert(node);
            } else {
                node.setLockVersion(node.getLockVersion() + 1);
                nodeMapper.updateById(node);
            }
            nodeIds.put(node.getNodeKey(), node.getId());
        }
        // 快照中已移除的节点保留记录并标记删除，历史任务和资产仍可追溯。
        for (CanvasNode removed : existingNodes.values()) {
            removed.setNodeStatus(3);
            removed.setLockVersion(removed.getLockVersion() + 1);
            nodeMapper.updateById(removed);
        }

        Map<String, CanvasEdge> existingEdges = new HashMap<>();
        for (CanvasEdge edge : edgeMapper.selectList(new LambdaQueryWrapper<CanvasEdge>()
                .eq(CanvasEdge::getCanvasId, canvasId))) {
            existingEdges.put(edge.getEdgeKey(), edge);
        }
        for (EdgeCommand source : command.edges()) {
            // 前端使用 nodeKey 建立连线，落库前映射为稳定的节点数据库 ID。
            Long sourceId = nodeIds.get(source.sourceNodeKey());
            Long targetId = nodeIds.get(source.targetNodeKey());
            if (sourceId == null || targetId == null || sourceId.equals(targetId)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "连线引用了无效节点");
            }
            CanvasEdge edge = existingEdges.remove(source.edgeKey());
            boolean insert = edge == null;
            if (insert) {
                edge = new CanvasEdge();
                edge.setCanvasId(canvasId);
                edge.setEdgeKey(source.edgeKey());
            }
            edge.setSourceNodeId(sourceId);
            edge.setSourceHandle(source.sourceHandle());
            edge.setTargetNodeId(targetId);
            edge.setTargetHandle(source.targetHandle());
            edge.setEdgeData(json(source.data()));
            if (insert) {
                edgeMapper.insert(edge);
            } else {
                edgeMapper.updateById(edge);
            }
        }
        // 连线没有被生成记录引用，可永久删除以允许将来复用相同 edgeKey。
        for (CanvasEdge removed : existingEdges.values()) {
            edgeMapper.physicallyDeleteById(removed.getId());
        }
        CanvasDocument updated = document(canvas);
        archive(updated, userId);
        return updated;
    }

    private void archive(CanvasDocument document, Long userId) {
        Long canvasId = document.canvas().getId();
        Integer revision = document.canvas().getLockVersion();
        CanvasVersion existing = versionMapper.selectOne(new LambdaQueryWrapper<CanvasVersion>()
                .eq(CanvasVersion::getCanvasId, canvasId).eq(CanvasVersion::getRevision, revision));
        if (existing != null) {
            return;
        }
        Map<Long, String> keys = new HashMap<>();
        List<NodeCommand> nodes = new ArrayList<>();
        for (CanvasNode node : document.nodes()) {
            keys.put(node.getId(), node.getNodeKey());
            nodes.add(new NodeCommand(node.getNodeKey(), node.getNodeType(), node.getName(),
                    node.getModelDefinitionId(), node.getPositionX(), node.getPositionY(),
                    node.getWidth(), node.getHeight(), parse(node.getInputConfig()),
                    parse(node.getModelConfig()), parse(node.getNodeData())));
        }
        List<EdgeCommand> edges = new ArrayList<>();
        for (CanvasEdge edge : document.edges()) {
            String source = keys.get(edge.getSourceNodeId());
            String target = keys.get(edge.getTargetNodeId());
            if (source != null && target != null) {
                edges.add(new EdgeCommand(edge.getEdgeKey(), source,
                        edge.getSourceHandle(), target, edge.getTargetHandle(), parse(edge.getEdgeData())));
            }
        }
        SaveCanvasCommand snapshot = new SaveCanvasCommand(document.canvas().getName(), revision,
                parse(document.canvas().getViewportData()), parse(document.canvas().getCanvasSetting()), nodes, edges);
        CanvasVersion version = new CanvasVersion();
        version.setCanvasId(canvasId);
        version.setRevision(revision);
        version.setCreatedByUserId(userId);
        try {
            version.setSnapshotJson(objectMapper.writeValueAsString(snapshot));
        } catch (Exception error) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "画布版本保存失败");
        }
        versionMapper.insert(version);
    }

    private JsonNode parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception error) {
            throw new BizException(ErrorCode.CONFLICT, "画布数据不是有效 JSON");
        }
    }

    /**
     * 校验节点/连线唯一键、节点类型以及连线引用完整性。
     */
    private void validateCommand(SaveCanvasCommand command) {
        requireText(command.name(), 40, "画布名称");
        Set<String> nodeKeys = new HashSet<>();
        for (NodeCommand node : command.nodes()) {
            requireText(node.nodeKey(), 64, "节点键");
            requireText(node.name(), 100, "节点名称");
            if (node.nodeType() == null || node.nodeType() < 1 || node.nodeType() > 7
                    || node.x() == null || node.y() == null || !nodeKeys.add(node.nodeKey())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "节点参数无效或节点键重复");
            }
        }
        Set<String> edgeKeys = new HashSet<>();
        for (EdgeCommand edge : command.edges()) {
            requireText(edge.edgeKey(), 64, "连线键");
            if (!edgeKeys.add(edge.edgeKey()) || !nodeKeys.contains(edge.sourceNodeKey())
                    || !nodeKeys.contains(edge.targetNodeKey())
                    || edge.sourceNodeKey().equals(edge.targetNodeKey())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "连线参数无效或连线键重复");
            }
        }
    }

    /**
     * 校验客户端提交的素材/历史 ID，URL 字段由现有生成配置逻辑处理。
     */
    private void validateMediaReferences(SaveCanvasCommand command, Long workspaceId) {
        Set<Long> assetIds = new HashSet<>();
        Set<Long> generatedIds = new HashSet<>();
        for (NodeCommand node : command.nodes()) {
            collectMediaIds(node.data(), assetIds, generatedIds);
            collectMediaIds(node.inputConfig(), assetIds, generatedIds);
            collectMediaIds(node.modelConfig(), assetIds, generatedIds);
        }
        for (Long id : assetIds) {
            Asset asset = assetMapper.selectById(id);
            if (asset == null || !Objects.equals(workspaceId, asset.getWorkspaceId())) {
                throw new BizException(ErrorCode.NOT_FOUND, "节点引用的素材不存在");
            }
        }
        for (Long id : generatedIds) {
            GeneratedAsset generated = generatedAssetMapper.selectById(id);
            if (generated == null || !Objects.equals(workspaceId, generated.getWorkspaceId())
                    || !Integer.valueOf(1).equals(generated.getAssetStatus())) {
                throw new BizException(ErrorCode.NOT_FOUND, "节点引用的生成历史不存在");
            }
        }
    }

    private void collectMediaIds(JsonNode value, Set<Long> assetIds, Set<Long> generatedIds) {
        if (value == null || value.isNull()) return;
        if (value.isArray()) {
            value.forEach(child -> collectMediaIds(child, assetIds, generatedIds));
            return;
        }
        if (!value.isObject()) return;
        value.fields().forEachRemaining(field -> {
            if ("assetId".equals(field.getKey())) {
                if (!field.getValue().isNull() && !field.getValue().asText().isBlank()) {
                    assetIds.add(requiredMediaId(field.getValue()));
                }
            } else if ("generatedAssetId".equals(field.getKey())) {
                if (!field.getValue().isNull() && !field.getValue().asText().isBlank()) {
                    generatedIds.add(requiredMediaId(field.getValue()));
                }
            } else {
                collectMediaIds(field.getValue(), assetIds, generatedIds);
            }
        });
    }

    private Long requiredMediaId(JsonNode value) {
        try {
            long id = Long.parseLong(value.asText());
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {
            // 非数字引用不符合素材库 ID 协议。
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "节点素材引用 ID 无效");
    }

    /**
     * 校验必填文本及数据库字段最大长度。
     */
    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_ERROR, field + "不能为空且长度不能超过 " + maxLength);
        }
    }

    /**
     * 从三张表装配完整画布文档。
     */
    private CanvasDocument document(Canvas canvas) {
        List<CanvasNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<CanvasNode>()
                .eq(CanvasNode::getCanvasId, canvas.getId()).eq(CanvasNode::getNodeStatus, 1));
        List<CanvasEdge> edges = edgeMapper.selectList(new LambdaQueryWrapper<CanvasEdge>()
                .eq(CanvasEdge::getCanvasId, canvas.getId()));
        return new CanvasDocument(canvas, nodes, edges);
    }

    /**
     * 获取有效画布，不存在或已删除时抛出业务异常。
     */
    private Canvas requireCanvas(Long id) {
        Canvas canvas = canvasMapper.selectById(id);
        if (canvas == null || canvas.getCanvasStatus() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "画布不存在");
        }
        return canvas;
    }

    /**
     * 将 Jackson JSON 节点转换为数据库 JSON 字符串。
     */
    private String json(JsonNode value) {
        return value == null || value.isNull() ? null : value.toString();
    }

}
