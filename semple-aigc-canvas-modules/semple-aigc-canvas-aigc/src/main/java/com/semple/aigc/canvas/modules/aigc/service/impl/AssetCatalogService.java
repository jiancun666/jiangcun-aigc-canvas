package com.semple.aigc.canvas.modules.aigc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.semple.aigc.canvas.api.aigc.domain.*;
import com.semple.aigc.canvas.api.aigc.mapper.*;
import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.modules.aigc.mapper.GeneratedAssetMapper;
import com.semple.aigc.canvas.modules.aigc.service.SpaceAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** 生成历史与素材库之间的显式保存操作，以及素材目录管理。 */
@Service
@RequiredArgsConstructor
public class AssetCatalogService {
    private final GeneratedAssetMapper generatedMapper;
    private final AssetMapper assetMapper;
    private final AssetFolderMapper folderMapper;
    private final AssetTagMapper tagMapper;
    private final AssetTagRelationMapper relationMapper;
    private final SpaceAccessService accessService;

    @Transactional
    public Asset saveGenerated(Long generatedId, Long folderId, String name, List<Long> tagIds,
                               Long workspaceId, Long userId) {
        accessService.assertReadable(workspaceId, userId);
        GeneratedAsset generated = generatedMapper.selectForUpdate(generatedId);
        if (generated == null || !workspaceId.equals(generated.getWorkspaceId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!Integer.valueOf(1).equals(generated.getAssetStatus())
                || !Integer.valueOf(1).equals(generated.getAssetSource())
                || generated.getAssetType() == null || generated.getAssetType() < 2
                || generated.getAssetType() > 4 || generated.getStorageUrl() == null
                || generated.getStorageUrl().isBlank() || generated.getStorageUrl().length() > 2048) {
            throw new BizException(ErrorCode.CONFLICT, "该生成结果不能保存为素材");
        }
        requireFolder(folderId, workspaceId);
        if (tagIds != null && (tagIds.size() > 20 || tagIds.stream().anyMatch(Objects::isNull)
                || tagIds.stream().distinct().count() != tagIds.size())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (tagIds != null) {
            for (Long tagId : tagIds) {
                requireTag(tagId, workspaceId);
            }
        }
        if (generated.getAssetId() != null) {
            Asset existing = assetMapper.selectById(generated.getAssetId());
            if (existing != null && workspaceId.equals(existing.getWorkspaceId())) {
                return existing;
            }
            throw new BizException(ErrorCode.CONFLICT, "生成结果关联的素材已失效");
        }
        Asset asset = new Asset();
        asset.setWorkspaceId(workspaceId);
        asset.setFolderId(folderId);
        asset.setUploadedBy(userId);
        asset.setAssetType(switch (generated.getAssetType()) {
            case 2 -> "IMAGE";
            case 3 -> "VIDEO";
            case 4 -> "AUDIO";
            default -> throw new BizException(ErrorCode.PARAM_ERROR);
        });
        String assetName = name == null || name.isBlank() ? generated.getAssetNo() : name.trim();
        if (assetName == null || assetName.isBlank() || assetName.length() > 255) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE, "素材名称长度应为 1–255 个字符");
        }
        asset.setAssetName(assetName);
        asset.setAssetUrl(generated.getStorageUrl());
        asset.setThumbnailUrl(generated.getThumbnailUrl());
        asset.setMimeType(generated.getMimeType() == null ? switch (generated.getAssetType()) {
            case 2 -> "image/*";
            case 3 -> "video/*";
            default -> "audio/*";
        } : generated.getMimeType());
        asset.setSizeBytes(generated.getFileSize() == null ? 0L : generated.getFileSize());
        asset.setWidth(generated.getWidth());
        asset.setHeight(generated.getHeight());
        asset.setDurationMs(generated.getDurationMs());
        assetMapper.insert(asset);
        generated.setAssetId(asset.getId());
        generatedMapper.updateById(generated);
        if (tagIds != null) {
            for (Long tagId : tagIds) {
                AssetTagRelation relation = new AssetTagRelation();
                relation.setAssetId(asset.getId());
                relation.setTagId(tagId);
                relation.setWorkspaceId(workspaceId);
                relation.setCreatedBy(userId);
                relationMapper.insert(relation);
            }
        }
        return asset;
    }

    public List<AssetFolder> folders(Long workspaceId, Long userId) {
        accessService.assertReadable(workspaceId, userId);
        return folderMapper.selectList(new LambdaQueryWrapper<AssetFolder>()
                .eq(AssetFolder::getWorkspaceId, workspaceId).orderByAsc(AssetFolder::getId));
    }

    @Transactional
    public AssetFolder createFolder(Long workspaceId, Long userId, Long parentId, String name) {
        accessService.assertReadable(workspaceId, userId);
        requireFolder(parentId, workspaceId);
        AssetFolder folder = new AssetFolder();
        folder.setWorkspaceId(workspaceId);
        folder.setCreatedBy(userId);
        folder.setParentId(parentId);
        folder.setFolderName(requiredName(name));
        folderMapper.insert(folder);
        return folder;
    }

    public List<AssetTag> tags(Long workspaceId, Long userId) {
        accessService.assertReadable(workspaceId, userId);
        return tagMapper.selectList(new LambdaQueryWrapper<AssetTag>()
                .eq(AssetTag::getWorkspaceId, workspaceId).orderByAsc(AssetTag::getSortNo));
    }

    @Transactional
    public AssetTag createTag(Long workspaceId, Long userId, String name) {
        accessService.assertReadable(workspaceId, userId);
        String tagName = requiredName(name);
        if (tagName.length() > 64) throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE);
        if (tagMapper.selectCount(new LambdaQueryWrapper<AssetTag>()
                .eq(AssetTag::getWorkspaceId, workspaceId).eq(AssetTag::getTagName, tagName)) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "标签已存在");
        }
        AssetTag tag = new AssetTag();
        tag.setWorkspaceId(workspaceId);
        tag.setTagName(tagName);
        tag.setTagType("CUSTOM");
        tag.setCreatedBy(userId);
        tag.setSortNo(0);
        tagMapper.insert(tag);
        return tag;
    }

    public void requireFolder(Long folderId, Long workspaceId) {
        if (folderId == null) return;
        AssetFolder folder = folderMapper.selectById(folderId);
        if (folder == null || !workspaceId.equals(folder.getWorkspaceId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "素材文件夹不存在");
        }
    }

    public void requireTag(Long tagId, Long workspaceId) {
        AssetTag tag = tagMapper.selectById(tagId);
        if (tag == null || !workspaceId.equals(tag.getWorkspaceId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "素材标签不存在");
        }
    }

    private String requiredName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 80) {
            throw new BizException(ErrorCode.PARAM_ERROR, "名称长度应为 1–80 个字符");
        }
        return name.trim();
    }
}
