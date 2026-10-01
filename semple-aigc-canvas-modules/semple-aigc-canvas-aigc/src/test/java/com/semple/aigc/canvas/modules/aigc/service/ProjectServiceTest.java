package com.semple.aigc.canvas.modules.aigc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.semple.aigc.canvas.api.aigc.domain.Canvas;
import com.semple.aigc.canvas.api.aigc.domain.ProjectItem;
import com.semple.aigc.canvas.api.aigc.domain.ProjectOperationLog;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.modules.aigc.service.impl.ProjectServiceImpl;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasEdgeMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasNodeMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.ProjectItemMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.ProjectOperationLogMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasVersionMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasWorkflowExecutionMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasWorkflowExecutionStepMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {
    @Mock private ProjectItemMapper itemMapper;
    @Mock private ProjectOperationLogMapper logMapper;
    @Mock private CanvasMapper canvasMapper;
    @Mock private CanvasNodeMapper nodeMapper;
    @Mock private CanvasEdgeMapper edgeMapper;
    @Mock private CanvasVersionMapper versionMapper;
    @Mock private CanvasWorkflowExecutionMapper executionMapper;
    @Mock private CanvasWorkflowExecutionStepMapper executionStepMapper;
    @Mock private SpaceAccessService accessService;

    @Test
    void creatingProjectAlsoCreatesDefaultCanvas() {
        when(itemMapper.insert(any(ProjectItem.class))).thenAnswer(invocation -> {
            invocation.<ProjectItem>getArgument(0).setId(101L);
            return 1;
        });
        when(canvasMapper.insert(any(Canvas.class))).thenAnswer(invocation -> {
            invocation.<Canvas>getArgument(0).setId(201L);
            return 1;
        });
        ProjectService service = new ProjectServiceImpl(itemMapper, logMapper, canvasMapper, nodeMapper,
                edgeMapper, versionMapper, executionMapper, executionStepMapper, accessService, new ObjectMapper());

        ProjectItem result = service.create(7L, 2, null, "新项目", "cover.png", 7L);

        assertThat(result.getId()).isEqualTo(101L);
        assertThat(result.getCanvasId()).isEqualTo(201L);
        verify(itemMapper).updateById(result);
        verify(logMapper).insert(any(ProjectOperationLog.class));
    }

    @Test
    void childOfRecycledFolderCannotBeOpened() {
        ProjectItem child = new ProjectItem();
        child.setId(101L);
        child.setWorkspaceId(7L);
        child.setParentId(8L);
        child.setItemStatus(1);
        ProjectItem parent = new ProjectItem();
        parent.setId(8L);
        parent.setWorkspaceId(7L);
        parent.setItemStatus(2);
        when(itemMapper.selectById(101L)).thenReturn(child);
        when(itemMapper.selectById(8L)).thenReturn(parent);
        ProjectService service = new ProjectServiceImpl(itemMapper, logMapper, canvasMapper, nodeMapper,
                edgeMapper, versionMapper, executionMapper, executionStepMapper, accessService, new ObjectMapper());

        assertThatThrownBy(() -> service.detail(101L, 7L)).isInstanceOf(BizException.class);
    }

    @Test
    void batchRestoreRestoresParentBeforeChildEvenWhenChildIdIsSmaller() {
        ProjectItem parent = new ProjectItem();
        parent.setId(20L);
        parent.setWorkspaceId(7L);
        parent.setCreatorUserId(7L);
        parent.setItemType(1);
        parent.setItemStatus(2);
        parent.setLockVersion(0);
        ProjectItem child = new ProjectItem();
        child.setId(10L);
        child.setWorkspaceId(7L);
        child.setCreatorUserId(7L);
        child.setItemType(2);
        child.setItemStatus(2);
        child.setOriginalParentId(20L);
        child.setLockVersion(0);
        when(itemMapper.selectById(10L)).thenReturn(child);
        when(itemMapper.selectById(20L)).thenReturn(parent);
        when(itemMapper.selectForUpdate(10L)).thenReturn(child);
        when(itemMapper.selectForUpdate(20L)).thenReturn(parent);
        ProjectService service = new ProjectServiceImpl(itemMapper, logMapper, canvasMapper, nodeMapper,
                edgeMapper, versionMapper, executionMapper, executionStepMapper, accessService, new ObjectMapper());

        assertThat(service.restoreBatch(java.util.List.of(10L, 20L), 7L))
                .extracting(ProjectItem::getId).containsExactly(20L, 10L);
        assertThat(child.getParentId()).isEqualTo(20L);
    }
}
