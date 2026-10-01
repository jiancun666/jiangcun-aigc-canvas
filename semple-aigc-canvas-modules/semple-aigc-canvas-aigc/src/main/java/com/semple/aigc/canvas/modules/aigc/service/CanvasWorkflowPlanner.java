package com.semple.aigc.canvas.modules.aigc.service;

import com.semple.aigc.canvas.api.aigc.domain.CanvasEdge;
import com.semple.aigc.canvas.api.aigc.domain.CanvasNode;
import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从目标节点向上游遍历画布连线，并按依赖顺序生成执行计划。
 */
public final class CanvasWorkflowPlanner {
    private CanvasWorkflowPlanner() {
    }

    public record PlannedNode(CanvasNode node, List<String> dependencies) {
    }

    /**
     * 只选择目标节点及其上游节点，并拒绝环形依赖。
     */
    public static List<PlannedNode> plan(List<CanvasNode> nodes, List<CanvasEdge> edges, String targetKey) {
        Map<Long, CanvasNode> byId = new HashMap<>();
        Map<String, CanvasNode> byKey = new HashMap<>();
        for (CanvasNode node : nodes) {
            byId.put(node.getId(), node);
            byKey.put(node.getNodeKey(), node);
        }
        CanvasNode target = byKey.get(targetKey);
        if (target == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "目标节点不存在");
        }
        Map<String, List<String>> parents = new LinkedHashMap<>();
        for (CanvasNode node : nodes) parents.put(node.getNodeKey(), new ArrayList<>());
        for (CanvasEdge edge : edges) {
            CanvasNode source = byId.get(edge.getSourceNodeId());
            CanvasNode destination = byId.get(edge.getTargetNodeId());
            if (source != null && destination != null) {
                List<String> dependencies = parents.get(destination.getNodeKey());
                if (!dependencies.contains(source.getNodeKey())) {
                    dependencies.add(source.getNodeKey());
                }
            }
        }
        List<PlannedNode> ordered = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        visit(targetKey, byKey, parents, visiting, visited, ordered);
        if (ordered.size() > 100) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE, "工作流不能超过100个节点");
        }
        return ordered;
    }

    private static void visit(String key, Map<String, CanvasNode> byKey, Map<String, List<String>> parents,
                              Set<String> visiting, Set<String> visited, List<PlannedNode> ordered) {
        if (visited.contains(key)) {
            return;
        }
        if (!visiting.add(key)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "画布连线存在环");
        }
        for (String parent : parents.get(key)) {
            visit(parent, byKey, parents, visiting, visited, ordered);
        }
        visiting.remove(key);
        visited.add(key);
        ordered.add(new PlannedNode(byKey.get(key), List.copyOf(parents.get(key))));
    }
}
