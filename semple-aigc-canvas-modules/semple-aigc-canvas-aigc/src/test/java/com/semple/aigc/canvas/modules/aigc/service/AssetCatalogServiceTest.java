package com.semple.aigc.canvas.modules.aigc.service;

import com.semple.aigc.canvas.api.aigc.domain.Asset;
import com.semple.aigc.canvas.api.aigc.domain.AssetFolder;
import com.semple.aigc.canvas.api.aigc.domain.GeneratedAsset;
import com.semple.aigc.canvas.api.aigc.mapper.AssetFolderMapper;
import com.semple.aigc.canvas.api.aigc.mapper.AssetMapper;
import com.semple.aigc.canvas.api.aigc.mapper.AssetTagMapper;
import com.semple.aigc.canvas.api.aigc.mapper.AssetTagRelationMapper;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.modules.aigc.mapper.GeneratedAssetMapper;
import com.semple.aigc.canvas.modules.aigc.service.impl.AssetCatalogService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssetCatalogServiceTest {
    private final GeneratedAssetMapper generatedMapper = mock(GeneratedAssetMapper.class);
    private final AssetMapper assetMapper = mock(AssetMapper.class);
    private final AssetFolderMapper folderMapper = mock(AssetFolderMapper.class);
    private final AssetTagMapper tagMapper = mock(AssetTagMapper.class);
    private final AssetTagRelationMapper relationMapper = mock(AssetTagRelationMapper.class);
    private final SpaceAccessService accessService = mock(SpaceAccessService.class);
    private final AssetCatalogService service = new AssetCatalogService(generatedMapper, assetMapper,
            folderMapper, tagMapper, relationMapper, accessService);

    @Test
    void explicitlySavesHistoryWithFolderAndMimeFallback() {
        GeneratedAsset generated = output(10L);
        generated.setMimeType(null);
        when(generatedMapper.selectForUpdate(7L)).thenReturn(generated);
        AssetFolder folder = new AssetFolder();
        folder.setWorkspaceId(10L);
        when(folderMapper.selectById(3L)).thenReturn(folder);
        when(assetMapper.insert(any(Asset.class))).thenAnswer(invocation -> {
            invocation.<Asset>getArgument(0).setId(11L);
            return 1;
        });

        Asset saved = service.saveGenerated(7L, 3L, "封面", List.of(), 10L, 2L);

        assertThat(saved.getId()).isEqualTo(11L);
        assertThat(saved.getMimeType()).isEqualTo("image/*");
        assertThat(saved.getSizeBytes()).isZero();
        assertThat(saved.getFolderId()).isEqualTo(3L);
        assertThat(generated.getAssetId()).isEqualTo(11L);
        verify(generatedMapper).updateById(generated);
    }

    @Test
    void savingTheSameHistoryAgainReturnsItsExistingAsset() {
        GeneratedAsset generated = output(10L);
        generated.setAssetId(11L);
        Asset existing = new Asset();
        existing.setId(11L);
        existing.setWorkspaceId(10L);
        when(generatedMapper.selectForUpdate(7L)).thenReturn(generated);
        when(assetMapper.selectById(11L)).thenReturn(existing);

        assertThat(service.saveGenerated(7L, null, null, null, 10L, 2L)).isSameAs(existing);
        verify(assetMapper, never()).insert(any(Asset.class));
    }

    @Test
    void rejectsAnotherWorkspacesHistoryBeforeWriting() {
        when(generatedMapper.selectForUpdate(7L)).thenReturn(output(99L));

        assertThatThrownBy(() -> service.saveGenerated(7L, null, null, null, 10L, 2L))
                .isInstanceOf(BizException.class);
        verify(assetMapper, never()).insert(any(Asset.class));
    }

    private GeneratedAsset output(Long workspaceId) {
        GeneratedAsset result = new GeneratedAsset();
        result.setId(7L);
        result.setWorkspaceId(workspaceId);
        result.setAssetSource(1);
        result.setAssetStatus(1);
        result.setAssetType(2);
        result.setAssetNo("GA7");
        result.setStorageUrl("https://example.test/result.png");
        return result;
    }
}
