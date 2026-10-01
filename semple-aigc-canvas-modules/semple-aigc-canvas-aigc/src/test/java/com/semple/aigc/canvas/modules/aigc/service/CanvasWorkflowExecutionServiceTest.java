package com.semple.aigc.canvas.modules.aigc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.semple.aigc.canvas.api.aigc.domain.Canvas;
import com.semple.aigc.canvas.api.aigc.domain.CanvasEdge;
import com.semple.aigc.canvas.api.aigc.domain.CanvasNode;
import com.semple.aigc.canvas.api.aigc.domain.CanvasWorkflowExecution;
import com.semple.aigc.canvas.api.aigc.domain.CanvasWorkflowExecutionStep;
import com.semple.aigc.canvas.api.aigc.domain.GenerationTask;
import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;
import com.semple.aigc.canvas.api.aigc.domain.ModelPriceRule;
import com.semple.aigc.canvas.api.aigc.domain.ProjectItem;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasWorkflowExecutionMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasWorkflowExecutionStepMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.GeneratedAssetMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.GenerationTaskMapper;
import com.semple.aigc.canvas.modules.aigc.service.impl.CanvasWorkflowExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CanvasWorkflowExecutionServiceTest {
    @Test
    void referenceSourceIsIncludedWhenDownstreamModelTaskIsCreated() {
        CanvasService canvasService = mock(CanvasService.class);
        ProjectService projectService = mock(ProjectService.class);
        ModelCatalogService catalog = mock(ModelCatalogService.class);
        GenerationTaskService generation = mock(GenerationTaskService.class);
        CanvasWorkflowExecutionMapper runs = mock(CanvasWorkflowExecutionMapper.class);
        CanvasWorkflowExecutionStepMapper steps = mock(CanvasWorkflowExecutionStepMapper.class);
        List<CanvasWorkflowExecutionStep> savedSteps = new ArrayList<>();
        CanvasWorkflowExecutionService service = new CanvasWorkflowExecutionService(canvasService, projectService,
                mock(SpaceAccessService.class), catalog, generation, runs, steps,
                mock(GenerationTaskMapper.class), mock(GeneratedAssetMapper.class), new ObjectMapper());

        Canvas canvas = new Canvas();
        canvas.setId(3L);
        canvas.setProjectItemId(4L);
        canvas.setLockVersion(7);
        CanvasNode source = node(1L, "source", 5);
        source.setNodeData("{\"url\":\"https://example.test/source.png\",\"mimeType\":\"image/png\"}");
        CanvasNode target = node(2L, "target", 2);
        target.setModelDefinitionId(11L);
        target.setInputConfig("{\"prompt\":\"make it blue\"}");
        CanvasEdge edge = new CanvasEdge();
        edge.setSourceNodeId(1L);
        edge.setTargetNodeId(2L);
        when(canvasService.get(3L, 6L)).thenReturn(new CanvasService.CanvasDocument(canvas,
                List.of(source, target), List.of(edge)));
        ProjectItem project = new ProjectItem();
        project.setId(4L);
        project.setWorkspaceId(5L);
        project.setItemType(2);
        project.setItemStatus(1);
        when(projectService.writableDetail(4L, 6L)).thenReturn(project);
        ModelDefinition model = new ModelDefinition();
        model.setModelType(2);
        when(catalog.quote(11L, 1, "make it blue"))
                .thenReturn(new ModelCatalogService.ModelQuote(model, new ModelPriceRule(), 1, 1, "{}"));
        when(runs.insert(any(CanvasWorkflowExecution.class))).thenAnswer(invocation -> {
            invocation.<CanvasWorkflowExecution>getArgument(0).setId(100L);
            return 1;
        });
        when(steps.insert(any(CanvasWorkflowExecutionStep.class))).thenAnswer(invocation -> {
            CanvasWorkflowExecutionStep step = invocation.getArgument(0);
            step.setId(200L + savedSteps.size());
            savedSteps.add(step);
            return 1;
        });
        when(steps.selectList(any())).thenAnswer(invocation -> savedSteps);
        CanvasWorkflowExecutionService.ExecutionView started = service.start(3L, "target", 6L);
        when(runs.selectList(any())).thenReturn(List.of(started.execution()));
        GenerationTask task = new GenerationTask();
        task.setTaskNo("GT1");
        when(generation.createFromWorkflow(any(), eq(5L), eq(6L)))
                .thenReturn(new GenerationTaskService.TaskView(task, List.of(), List.of()));

        service.runDue();

        assertThat(savedSteps).extracting(CanvasWorkflowExecutionStep::getStepStatus).containsExactly(3, 2);
        ArgumentCaptor<GenerationTaskService.CreateTaskCommand> command =
                ArgumentCaptor.forClass(GenerationTaskService.CreateTaskCommand.class);
        verify(generation).createFromWorkflow(command.capture(), eq(5L), eq(6L));
        assertThat(command.getValue().config().path("referenceImages").get(0).asText())
                .isEqualTo("https://example.test/source.png");
    }

    private CanvasNode node(Long id, String key, int type) {
        CanvasNode node = new CanvasNode();
        node.setId(id);
        node.setNodeKey(key);
        node.setNodeType(type);
        return node;
    }
}
