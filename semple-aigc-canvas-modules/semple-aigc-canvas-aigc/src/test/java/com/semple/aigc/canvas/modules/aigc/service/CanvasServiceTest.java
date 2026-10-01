package com.semple.aigc.canvas.modules.aigc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.api.aigc.domain.Canvas;
import com.semple.aigc.canvas.api.aigc.domain.CanvasNode;
import com.semple.aigc.canvas.api.aigc.domain.Asset;
import com.semple.aigc.canvas.api.aigc.domain.ProjectItem;
import com.semple.aigc.canvas.api.aigc.mapper.AssetMapper;
import com.semple.aigc.canvas.modules.aigc.service.impl.CanvasServiceImpl;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasEdgeMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasNodeMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasVersionMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.ProjectItemMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.GeneratedAssetMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CanvasServiceTest {
    @Mock private CanvasMapper canvasMapper;
    @Mock private CanvasNodeMapper nodeMapper;
    @Mock private CanvasEdgeMapper edgeMapper;
    @Mock private ProjectService projectService;
    @Mock private ProjectItemMapper projectItemMapper;
    @Mock private CanvasVersionMapper versionMapper;
    @Mock private AssetMapper assetMapper;
    @Mock private GeneratedAssetMapper generatedAssetMapper;
    private CanvasService service;

    @BeforeEach
    void setUp() {
        service = new CanvasServiceImpl(canvasMapper, nodeMapper, edgeMapper, projectService,
                projectItemMapper,
                versionMapper, new ObjectMapper(), assetMapper, generatedAssetMapper);
    }

    @Test
    void saveIncrementsRevisionWithoutPhysicallyDeletingStableNodes() {
        Canvas canvas = canvas(3);
        when(canvasMapper.selectForUpdate(11L)).thenReturn(canvas);
        when(nodeMapper.selectList(any())).thenReturn(List.of());
        when(edgeMapper.selectList(any())).thenReturn(List.of());

        CanvasService.CanvasDocument result = service.save(11L,
                new CanvasService.SaveCanvasCommand("主画布", 3, null, null, List.of(), List.of()), 9L);

        assertThat(result.canvas().getLockVersion()).isEqualTo(4);
        verify(canvasMapper).updateById(canvas);
        verify(nodeMapper, never()).physicallyDeleteByCanvasId(11L);
        verify(edgeMapper, never()).physicallyDeleteByCanvasId(11L);
    }

    @Test
    void saveRejectsStaleRevisionBeforeWriting() {
        when(canvasMapper.selectForUpdate(11L)).thenReturn(canvas(5));

        assertThatThrownBy(() -> service.save(11L,
                new CanvasService.SaveCanvasCommand("主画布", 4, null, null, List.of(), List.of()), 9L))
                .isInstanceOf(BizException.class);

        verify(canvasMapper, never()).updateById(any(Canvas.class));
    }

    @Test
    void saveReusesNodeIdWhenNodeKeyAlreadyExists() {
        Canvas canvas = canvas(1);
        CanvasNode existing = new CanvasNode();
        existing.setId(99L);
        existing.setCanvasId(11L);
        existing.setNodeKey("node-1");
        existing.setNodeStatus(1);
        existing.setLockVersion(2);
        when(canvasMapper.selectForUpdate(11L)).thenReturn(canvas);
        when(nodeMapper.selectList(any())).thenReturn(List.of(existing));
        when(edgeMapper.selectList(any())).thenReturn(List.of());
        CanvasService.NodeCommand node = new CanvasService.NodeCommand("node-1", 2, "图片节点", null,
                BigDecimal.ONE, BigDecimal.ONE, null, null, null, null, null);

        service.save(11L, new CanvasService.SaveCanvasCommand(
                "主画布", 1, null, null, List.of(node), List.of()), 9L);

        assertThat(existing.getId()).isEqualTo(99L);
        assertThat(existing.getLockVersion()).isEqualTo(3);
        verify(nodeMapper).updateById(existing);
        verify(nodeMapper, never()).insert(any(CanvasNode.class));
    }

    @Test
    void saveRejectsMediaAssetFromAnotherWorkspace() throws Exception {
        when(canvasMapper.selectForUpdate(11L)).thenReturn(canvas(1));
        ProjectItem project = new ProjectItem();
        project.setWorkspaceId(7L);
        when(projectService.writableDetail(21L, 9L)).thenReturn(project);
        Asset foreignAsset = new Asset();
        foreignAsset.setWorkspaceId(8L);
        when(assetMapper.selectById(42L)).thenReturn(foreignAsset);
        CanvasService.NodeCommand node = new CanvasService.NodeCommand("node-1", 2, "图片节点", null,
                BigDecimal.ONE, BigDecimal.ONE, null, null, null, null,
                new ObjectMapper().readTree("{\"assetId\":42}"));

        assertThatThrownBy(() -> service.save(11L, new CanvasService.SaveCanvasCommand(
                "主画布", 1, null, null, List.of(node), List.of()), 9L)).isInstanceOf(BizException.class);
        verify(canvasMapper, never()).updateById(any(Canvas.class));
    }

    private Canvas canvas(int revision) {
        Canvas canvas = new Canvas();
        canvas.setId(11L);
        canvas.setProjectItemId(21L);
        canvas.setCanvasStatus(1);
        canvas.setLockVersion(revision);
        return canvas;
    }
}
