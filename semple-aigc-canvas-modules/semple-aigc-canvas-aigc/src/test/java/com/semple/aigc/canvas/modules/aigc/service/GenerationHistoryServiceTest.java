package com.semple.aigc.canvas.modules.aigc.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semple.aigc.canvas.api.aigc.domain.GeneratedAsset;
import com.semple.aigc.canvas.api.aigc.domain.UserAccount;
import com.semple.aigc.canvas.api.aigc.domain.WorkflowStepRun;
import com.semple.aigc.canvas.api.aigc.mapper.UserAccountMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.GeneratedAssetMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.WorkflowStepRunMapper;
import com.semple.aigc.canvas.modules.aigc.service.impl.GenerationHistoryServiceImpl;
import com.semple.aigc.canvas.modules.aigc.vo.GenerationHistoryVO;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 通用生成历史的图片、视频和音频映射测试。 */
class GenerationHistoryServiceTest {
    @Test
    void listMapsCreatorModelAndSelectedImageParameters() {
        GeneratedAsset asset = new GeneratedAsset();
        asset.setId(10L);
        asset.setAssetType(2);
        asset.setAssetSource(1);
        asset.setWorkspaceId(20L);
        asset.setCreatorUserId(30L);
        asset.setStepRunId(40L);
        asset.setStorageUrl("https://example/image.png");
        asset.setPromptSnapshot("a red bird");
        asset.setCreateTime(new Date(1_700_000_000_000L));
        asset.setGenerationConfig("{\"imageCount\":1,\"config\":{\"aspectRatio\":\"16:9\","
                + "\"size\":\"1024*576\",\"referenceImages\":[\"ref.png\"]}}");

        UserAccount creator = new UserAccount();
        creator.setId(30L);
        creator.setUsername("林默");
        WorkflowStepRun step = new WorkflowStepRun();
        step.setId(40L);
        step.setModelName("Ark Image");

        GeneratedAssetMapper assetMapper = mock(GeneratedAssetMapper.class);
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        WorkflowStepRunMapper stepMapper = mock(WorkflowStepRunMapper.class);
        Page<GeneratedAsset> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(List.of(asset));
        when(assetMapper.selectPage(any(), any())).thenReturn(page);
        when(userMapper.selectBatchIds(List.of(30L))).thenReturn(List.of(creator));
        when(stepMapper.selectBatchIds(List.of(40L))).thenReturn(List.of(step));

        GenerationHistoryServiceImpl service = new GenerationHistoryServiceImpl(
                assetMapper, stepMapper, userMapper, mock(SpaceAccessService.class), new ObjectMapper());
        GenerationHistoryVO result = service.list(20L, 2, 1, 20, 30L).getRecords().getFirst();

        assertThat(result.assetType()).isEqualTo(2);
        assertThat(result.storageUrl()).isEqualTo("https://example/image.png");
        assertThat(result.creator()).isEqualTo("林默");
        assertThat(result.createdAt()).isEqualTo(new Date(1_700_000_000_000L));
        assertThat(result.model()).isEqualTo("Ark Image");
        assertThat(result.prompt()).isEqualTo("a red bird");
        assertThat(result.aspectRatio().asText()).isEqualTo("16:9");
        assertThat(result.resolution().asText()).isEqualTo("1024*576");
        assertThat(result.referenceImages().get(0).asText()).isEqualTo("ref.png");
    }

    @Test
    void listMapsVideoFieldsForTheMediaHistoryView() {
        GeneratedAsset asset = new GeneratedAsset();
        asset.setId(11L);
        asset.setAssetType(3);
        asset.setAssetSource(1);
        asset.setWorkspaceId(20L);
        asset.setStorageUrl("https://example/video.mp4");
        asset.setThumbnailUrl("https://example/video-thumb.jpg");
        asset.setMimeType("video/mp4");
        asset.setDurationMs(12_500L);
        asset.setPromptSnapshot("a moving red bird");
        asset.setCreatorUserId(30L);
        asset.setStepRunId(40L);
        asset.setCreateTime(new Date(1_700_000_000_000L));
        asset.setGenerationConfig("{\"config\":{\"ratio\":\"16:9\",\"resolution\":\"1080p\"}}");

        UserAccount creator = new UserAccount();
        creator.setId(30L);
        creator.setUsername("林默");
        WorkflowStepRun step = new WorkflowStepRun();
        step.setId(40L);
        step.setModelName("Ark Video");

        GeneratedAssetMapper assetMapper = mock(GeneratedAssetMapper.class);
        Page<GeneratedAsset> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(List.of(asset));
        when(assetMapper.selectPage(any(), any())).thenReturn(page);
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        WorkflowStepRunMapper stepMapper = mock(WorkflowStepRunMapper.class);
        when(userMapper.selectBatchIds(List.of(30L))).thenReturn(List.of(creator));
        when(stepMapper.selectBatchIds(List.of(40L))).thenReturn(List.of(step));

        GenerationHistoryServiceImpl service = new GenerationHistoryServiceImpl(
                assetMapper, stepMapper, userMapper,
                mock(SpaceAccessService.class), new ObjectMapper());
        GenerationHistoryVO result = service.list(20L, 3, 1, 20, 30L)
                .getRecords().getFirst();

        assertThat(result.assetType()).isEqualTo(3);
        assertThat(result.storageUrl()).isEqualTo("https://example/video.mp4");
        assertThat(result.thumbnailUrl()).isEqualTo("https://example/video-thumb.jpg");
        assertThat(result.mimeType()).isEqualTo("video/mp4");
        assertThat(result.durationMs()).isEqualTo(12_500L);
        assertThat(result.creator()).isEqualTo("林默");
        assertThat(result.createdAt()).isEqualTo(new Date(1_700_000_000_000L));
        assertThat(result.prompt()).isEqualTo("a moving red bird");
        assertThat(result.model()).isEqualTo("Ark Video");
        assertThat(result.aspectRatio().asText()).isEqualTo("16:9");
        assertThat(result.resolution().asText()).isEqualTo("1080p");
    }

    /** 验证音频历史保留创作信息和请求中的参考音频。 */
    @Test
    void listMapsAudioReferenceAndCreationDetails() {
        GeneratedAsset asset = new GeneratedAsset();
        asset.setId(12L);
        asset.setAssetType(4);
        asset.setCreatorUserId(30L);
        asset.setStepRunId(40L);
        asset.setCreateTime(new Date(1_700_000_000_000L));
        asset.setPromptSnapshot("read the script");
        asset.setGenerationConfig("{\"config\":{\"referenceAudio\":\"voice.wav\"}}");
        UserAccount creator = new UserAccount();
        creator.setId(30L);
        creator.setUsername("林默");
        WorkflowStepRun step = new WorkflowStepRun();
        step.setId(40L);
        step.setModelName("Ark Audio");
        GeneratedAssetMapper assetMapper = mock(GeneratedAssetMapper.class);
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        WorkflowStepRunMapper stepMapper = mock(WorkflowStepRunMapper.class);
        Page<GeneratedAsset> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(List.of(asset));
        when(assetMapper.selectPage(any(), any())).thenReturn(page);
        when(userMapper.selectBatchIds(List.of(30L))).thenReturn(List.of(creator));
        when(stepMapper.selectBatchIds(List.of(40L))).thenReturn(List.of(step));

        GenerationHistoryServiceImpl service = new GenerationHistoryServiceImpl(
                assetMapper, stepMapper, userMapper, mock(SpaceAccessService.class), new ObjectMapper());
        GenerationHistoryVO result = service.list(20L, 4, 1, 20, 30L).getRecords().getFirst();

        assertThat(result.creator()).isEqualTo("林默");
        assertThat(result.createdAt()).isEqualTo(new Date(1_700_000_000_000L));
        assertThat(result.prompt()).isEqualTo("read the script");
        assertThat(result.model()).isEqualTo("Ark Audio");
        assertThat(result.referenceAudio().asText()).isEqualTo("voice.wav");
    }
}
