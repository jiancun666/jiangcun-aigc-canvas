package com.semple.aigc.canvas.modules.aigc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.semple.aigc.canvas.api.aigc.domain.Canvas;
import com.semple.aigc.canvas.api.aigc.domain.CanvasNode;
import com.semple.aigc.canvas.api.aigc.domain.GeneratedAsset;
import com.semple.aigc.canvas.api.aigc.domain.GenerationTask;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasNodeMapper;
import com.semple.aigc.canvas.modules.aigc.service.impl.GeneratedOutputService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeneratedOutputServiceTest {
    @Test
    void publishesMediaAndBumpsCanvasRevisionWithoutReplacingNodeFields() throws Exception {
        CanvasMapper canvasMapper = mock(CanvasMapper.class);
        CanvasNodeMapper nodeMapper = mock(CanvasNodeMapper.class);
        CanvasService canvasService = mock(CanvasService.class);
        GeneratedOutputService service = new GeneratedOutputService(
                canvasMapper, nodeMapper, new ObjectMapper(), canvasService);
        GenerationTask task = new GenerationTask();
        task.setTaskNo("GT1");
        task.setWorkspaceId(1L);
        task.setCreatorUserId(2L);
        task.setCanvasId(3L);
        task.setCanvasNodeId(4L);
        task.setModelDefinitionId(5L);
        GeneratedAsset output = new GeneratedAsset();
        output.setAssetNo("GA1");
        output.setAssetType(2);
        output.setStorageUrl("https://example.test/result.png");
        output.setMimeType("image/png");
        Canvas canvas = new Canvas();
        canvas.setId(3L);
        canvas.setCanvasStatus(1);
        canvas.setLockVersion(7);
        when(canvasMapper.selectForUpdate(3L)).thenReturn(canvas);
        CanvasNode node = new CanvasNode();
        node.setNodeData("{\"custom\":true}");
        node.setLockVersion(2);
        when(nodeMapper.selectOne(any())).thenReturn(node);

        service.publish(task, List.of(output));

        assertThat(output.getAssetId()).isNull();
        assertThat(new ObjectMapper().readTree(node.getNodeData()).path("custom").asBoolean()).isTrue();
        assertThat(new ObjectMapper().readTree(node.getNodeData()).path("generation")
                .path("outputs").get(0).path("assetNo").asText()).isEqualTo("GA1");
        assertThat(canvas.getLockVersion()).isEqualTo(8);
        verify(canvasService, org.mockito.Mockito.times(2)).recordVersion(3L, 2L);
    }

}
