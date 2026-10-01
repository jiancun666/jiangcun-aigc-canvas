package com.semple.aigc.canvas.modules.aigc.service;

import com.semple.aigc.canvas.api.aigc.domain.CanvasEdge;
import com.semple.aigc.canvas.api.aigc.domain.CanvasNode;
import com.semple.aigc.canvas.common.core.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanvasWorkflowPlannerTest {
    @Test
    void includesOnlyTargetAncestorsAndOrdersDependenciesFirst() {
        List<CanvasWorkflowPlanner.PlannedNode> plan = CanvasWorkflowPlanner.plan(
                List.of(node(1L, "source"), node(2L, "middle"), node(3L, "target"), node(4L, "unrelated")),
                List.of(edge(1L, 2L), edge(2L, 3L)), "target");
        assertThat(plan).extracting(item -> item.node().getNodeKey())
                .containsExactly("source", "middle", "target");
        assertThat(plan.get(2).dependencies()).containsExactly("middle");
    }

    @Test
    void rejectsCycleBeforeSchedulingAnyTasks() {
        assertThatThrownBy(() -> CanvasWorkflowPlanner.plan(
                List.of(node(1L, "a"), node(2L, "b")),
                List.of(edge(1L, 2L), edge(2L, 1L)), "a"))
                .isInstanceOf(BizException.class);
    }

    private CanvasNode node(Long id, String key) {
        CanvasNode node = new CanvasNode();
        node.setId(id);
        node.setNodeKey(key);
        return node;
    }

    private CanvasEdge edge(Long source, Long target) {
        CanvasEdge edge = new CanvasEdge();
        edge.setSourceNodeId(source);
        edge.setTargetNodeId(target);
        return edge;
    }
}
