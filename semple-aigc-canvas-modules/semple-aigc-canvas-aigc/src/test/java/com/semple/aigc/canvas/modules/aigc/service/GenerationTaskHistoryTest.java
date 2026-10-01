package com.semple.aigc.canvas.modules.aigc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.semple.aigc.canvas.api.aigc.domain.GeneratedAsset;
import com.semple.aigc.canvas.api.aigc.domain.GenerationTask;
import com.semple.aigc.canvas.api.aigc.domain.WorkflowStepRun;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasNodeMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.GeneratedAssetMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.GenerationTaskMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.ModelCallLogMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.ModelPriceRuleMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.PointAccountMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.WorkflowRunMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.WorkflowStepRunMapper;
import com.semple.aigc.canvas.modules.aigc.provider.ModelProvider.ProviderAsset;
import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;
import com.semple.aigc.canvas.modules.aigc.service.impl.GenerationTaskServiceImpl;
import com.semple.aigc.canvas.modules.aigc.service.impl.GeneratedOutputService;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/** 生成历史写入的关键业务意图测试。 */
class GenerationTaskHistoryTest {
    @Test
    void repeatedProviderResultUpdatesTheSameHistoryOutput() throws Exception {
        GenerationTaskMapper taskMapper = mock(GenerationTaskMapper.class);
        WorkflowRunMapper workflowRunMapper = mock(WorkflowRunMapper.class);
        WorkflowStepRunMapper stepRunMapper = mock(WorkflowStepRunMapper.class);
        GeneratedAssetMapper generatedAssetMapper = mock(GeneratedAssetMapper.class);
        ModelCallLogMapper callLogMapper = mock(ModelCallLogMapper.class);
        CanvasMapper canvasMapper = mock(CanvasMapper.class);
        CanvasNodeMapper nodeMapper = mock(CanvasNodeMapper.class);
        PointAccountMapper pointAccountMapper = mock(PointAccountMapper.class);
        ModelPriceRuleMapper priceRuleMapper = mock(ModelPriceRuleMapper.class);
        ProjectService projectService = mock(ProjectService.class);
        SpaceAccessService accessService = mock(SpaceAccessService.class);
        ModelCatalogService modelCatalogService = mock(ModelCatalogService.class);
        PointService pointService = mock(PointService.class);
        com.semple.aigc.canvas.modules.aigc.provider.ModelProviderRegistry registry =
                mock(com.semple.aigc.canvas.modules.aigc.provider.ModelProviderRegistry.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        ModelPointCalculator pointCalculator = mock(ModelPointCalculator.class);
        GenerationTaskServiceImpl service = new GenerationTaskServiceImpl(taskMapper, workflowRunMapper,
                stepRunMapper, generatedAssetMapper, callLogMapper, canvasMapper, nodeMapper,
                pointAccountMapper, priceRuleMapper, projectService, accessService, modelCatalogService,
                pointCalculator, pointService, registry, new ObjectMapper(), transactionTemplate,
                mock(GeneratedOutputService.class));

        GenerationTask task = new GenerationTask();
        task.setWorkspaceId(1L);
        task.setProjectItemId(2L);
        task.setCanvasId(3L);
        task.setCanvasNodeId(4L);
        task.setWorkflowRunId(5L);
        task.setWorkflowStepRunId(6L);
        task.setCreatorUserId(7L);
        task.setModelDefinitionId(8L);
        task.setPrompt("cat");
        task.setRequestConfig("{}");
        WorkflowStepRun step = new WorkflowStepRun();
        step.setNodeType(2);
        when(stepRunMapper.selectById(6L)).thenReturn(step);
        GeneratedAsset persisted = new GeneratedAsset();
        persisted.setId(100L);
        persisted.setAssetNo("GA-stable");
        when(generatedAssetMapper.selectOne(any())).thenReturn(persisted);

        Method saveAssets = GenerationTaskServiceImpl.class.getDeclaredMethod("saveAssets",
                GenerationTask.class, List.class, ModelDefinition.class);
        saveAssets.setAccessible(true);
        ModelDefinition model = new ModelDefinition();
        model.setProviderCode("TEST");
        model.setModelCode("test-image");
        List<ProviderAsset> result = List.of(new ProviderAsset("https://a", "image/png", 10, 10),
                new ProviderAsset("https://b", "image/png", 20, 20));

        @SuppressWarnings("unchecked")
        List<GeneratedAsset> first = (List<GeneratedAsset>) saveAssets.invoke(service, task, result, model);
        @SuppressWarnings("unchecked")
        List<GeneratedAsset> second = (List<GeneratedAsset>) saveAssets.invoke(service, task, result, model);

        assertThat(first).extracting(GeneratedAsset::getOutputIndex).containsExactly(1, 2);
        assertThat(second).extracting(GeneratedAsset::getAssetNo).containsExactly("GA-stable", "GA-stable");
        assertThat(second).allMatch(asset -> asset.getAssetId() == null);
        verify(generatedAssetMapper, times(4)).insertIgnore(any(GeneratedAsset.class));
        verify(generatedAssetMapper, times(4)).updateById(any(GeneratedAsset.class));
    }
}
