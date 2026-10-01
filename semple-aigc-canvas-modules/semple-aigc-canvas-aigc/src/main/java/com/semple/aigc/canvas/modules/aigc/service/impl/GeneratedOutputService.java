package com.semple.aigc.canvas.modules.aigc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.semple.aigc.canvas.api.aigc.domain.Canvas;
import com.semple.aigc.canvas.api.aigc.domain.CanvasNode;
import com.semple.aigc.canvas.api.aigc.domain.GeneratedAsset;
import com.semple.aigc.canvas.api.aigc.domain.GenerationTask;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasNodeMapper;
import com.semple.aigc.canvas.modules.aigc.service.CanvasService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 将完成的生成结果回写到原画布节点。用户随后可从生成历史显式保存到资产库。
 */
@Service
@RequiredArgsConstructor
public class GeneratedOutputService {
    private final CanvasMapper canvasMapper;
    private final CanvasNodeMapper nodeMapper;
    private final ObjectMapper objectMapper;
    private final CanvasService canvasService;

    /**
     * 与生成任务完成、积分结算处于同一事务，保证结果与扣费状态一致。
     */
    public void publish(GenerationTask task, List<GeneratedAsset> outputs) {
        // 与普通画布保存共用行锁；节点删除后仍保留生成历史，但不重新创建节点。
        Canvas canvas = canvasMapper.selectForUpdate(task.getCanvasId());
        if (canvas == null || !Integer.valueOf(1).equals(canvas.getCanvasStatus())) {
            return;
        }
        CanvasNode node = nodeMapper.selectOne(new LambdaQueryWrapper<CanvasNode>()
                .eq(CanvasNode::getId, task.getCanvasNodeId())
                .eq(CanvasNode::getCanvasId, task.getCanvasId())
                .eq(CanvasNode::getNodeStatus, 1));
        if (node == null) {
            return;
        }
        canvasService.recordVersion(task.getCanvasId(), task.getCreatorUserId());
        ObjectNode data = parseObject(node.getNodeData());
        ObjectNode generation = objectMapper.createObjectNode();
        generation.put("taskNo", task.getTaskNo());
        generation.put("modelDefinitionId", task.getModelDefinitionId());
        ArrayNode results = generation.putArray("outputs");
        for (GeneratedAsset output : outputs) {
            ObjectNode result = results.addObject();
            result.put("assetNo", output.getAssetNo());
            if (output.getAssetId() != null) {
                result.put("assetId", output.getAssetId());
            }
            if (output.getStorageUrl() != null) {
                result.put("url", output.getStorageUrl());
            }
            if (output.getTextContent() != null) {
                result.put("text", output.getTextContent());
            }
        }
        data.set("generation", generation);
        node.setNodeData(data.toString());
        node.setLockVersion(node.getLockVersion() + 1);
        nodeMapper.updateById(node);
        canvas.setLockVersion(canvas.getLockVersion() + 1);
        canvasMapper.updateById(canvas);
        canvasService.recordVersion(task.getCanvasId(), task.getCreatorUserId());
    }

    private ObjectNode parseObject(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode value = objectMapper.readTree(json);
            if (value instanceof ObjectNode object) {
                return object;
            }
        } catch (Exception ignored) {
            // 保留旧节点的异常数据，避免回写时覆盖原始内容。
        }
        ObjectNode data = objectMapper.createObjectNode();
        data.put("legacyNodeData", json);
        return data;
    }
}
