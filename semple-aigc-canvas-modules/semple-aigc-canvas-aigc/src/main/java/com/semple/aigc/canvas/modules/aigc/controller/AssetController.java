package com.semple.aigc.canvas.modules.aigc.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.semple.aigc.canvas.api.aigc.domain.Asset;
import com.semple.aigc.canvas.api.aigc.domain.AssetFolder;
import com.semple.aigc.canvas.api.aigc.domain.AssetTag;
import com.semple.aigc.canvas.api.aigc.mapper.AssetMapper;
import com.semple.aigc.canvas.api.aigc.domain.AssetTagRelation;
import com.semple.aigc.canvas.api.aigc.mapper.AssetTagRelationMapper;
import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.domain.R;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.common.web.context.UserKit;
import com.semple.aigc.canvas.common.web.controller.BaseController;
import com.semple.aigc.canvas.modules.aigc.service.SpaceAccessService;
import com.semple.aigc.canvas.modules.aigc.service.impl.AssetCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 当前工作区的媒体素材查询接口。
 */
@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
@Tag(name = "素材目录", description = "查询工作区中的图片、视频和音频素材")
public class AssetController extends BaseController {
    private final AssetMapper assetMapper;
    private final SpaceAccessService accessService;
    private final AssetCatalogService catalogService;
    private final AssetTagRelationMapper relationMapper;

    @Schema(description = "创建当前工作区素材文件夹的请求")
    public record CreateFolderRequest(
            @Schema(description = "当前工作区的父素材文件夹 ID；省略或 null 表示根目录", example = "1000")
            Long parentId,
            @Schema(description = "文件夹名称，去除首尾空白后非空且最多 80 个字符", example = "测试素材")
            @NotBlank String name) {
    }

    @Schema(description = "创建当前工作区自定义素材标签的请求")
    public record CreateTagRequest(
            @Schema(description = "标签名称，去除首尾空白后非空且最多 80 个字符", example = "模型测试")
            @NotBlank String name) {
    }

    @GetMapping("/folders")
    @Operation(summary = "查询素材文件夹")
    public R<List<AssetFolder>> folders() {
        return success(catalogService.folders(UserKit.requireWorkspaceId(), UserKit.requireUserId()));
    }

    @PostMapping("/folders")
    @Operation(summary = "创建素材文件夹")
    public R<AssetFolder> createFolder(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "素材文件夹名称及可选父文件夹", required = true)
            @RequestBody
            CreateFolderRequest request) {
        return success(catalogService.createFolder(UserKit.requireWorkspaceId(), UserKit.requireUserId(),
                request.parentId(), request.name()));
    }

    @GetMapping("/tags")
    @Operation(summary = "查询素材标签")
    public R<List<AssetTag>> tags() {
        return success(catalogService.tags(UserKit.requireWorkspaceId(), UserKit.requireUserId()));
    }

    @PostMapping("/tags")
    @Operation(summary = "创建素材标签")
    public R<AssetTag> createTag(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "自定义素材标签名称", required = true)
            @RequestBody
            CreateTagRequest request) {
        return success(catalogService.createTag(UserKit.requireWorkspaceId(), UserKit.requireUserId(),
                request.name()));
    }

    /**
     * 分页查询素材，可按媒体类型筛选。
     */
    @Operation(summary = "分页查询素材目录")
    @GetMapping
    public R<Page<Asset>> list(
            @Parameter(
                    description = "素材类型：IMAGE 图片，VIDEO 视频，AUDIO 音频；须大写，省略查询全部媒体类型",
                    example = "IMAGE",
                    schema = @Schema(allowableValues = {"IMAGE", "VIDEO", "AUDIO"}))
            @RequestParam(required = false)
            String assetType,
            @Parameter(description = "当前工作区素材文件夹 ID；省略表示不按文件夹筛选", example = "1000")
            @RequestParam(required = false)
            Long folderId,
            @Parameter(description = "当前工作区素材标签 ID；省略表示不按标签筛选", example = "1000")
            @RequestParam(required = false)
            Long tagId,
            @Parameter(description = "素材名称模糊搜索关键字，最多 80 个字符；省略或空白表示不按名称筛选", example = "测试", schema = @Schema(maxLength = 80))
            @RequestParam(required = false)
            String keyword,
            @Parameter(description = "页码，从 1 开始，默认 1", example = "1", schema = @Schema(minimum = "1", defaultValue = "1"))
            @RequestParam(defaultValue = "1")
            long pageNum,
            @Parameter(description = "每页条数，默认 20，范围 1–100", example = "20", schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20")
            long pageSize) {
        Long workspaceId = UserKit.requireWorkspaceId();
        accessService.assertReadable(workspaceId, UserKit.requireUserId());
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE);
        }
        if (assetType != null && !Set.of("IMAGE", "VIDEO", "AUDIO").contains(assetType)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        catalogService.requireFolder(folderId, workspaceId);
        if (tagId != null) catalogService.requireTag(tagId, workspaceId);
        if (keyword != null && keyword.length() > 80) throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE);
        List<Long> taggedIds = tagId == null ? null : relationMapper.selectList(
                        new LambdaQueryWrapper<AssetTagRelation>().eq(AssetTagRelation::getWorkspaceId, workspaceId)
                                .eq(AssetTagRelation::getTagId, tagId))
                .stream().map(AssetTagRelation::getAssetId).toList();
        if (taggedIds != null && taggedIds.isEmpty()) {
            return success(Page.of(pageNum, pageSize));
        }
        return success(assetMapper.selectPage(Page.of(pageNum, pageSize), new LambdaQueryWrapper<Asset>()
                .eq(Asset::getWorkspaceId, workspaceId)
                .eq(assetType != null, Asset::getAssetType, assetType)
                .eq(folderId != null, Asset::getFolderId, folderId)
                .like(keyword != null && !keyword.isBlank(), Asset::getAssetName, keyword == null ? null : keyword.trim())
                .in(taggedIds != null, Asset::getId, taggedIds)
                .orderByDesc(Asset::getCreateTime)));
    }
}
