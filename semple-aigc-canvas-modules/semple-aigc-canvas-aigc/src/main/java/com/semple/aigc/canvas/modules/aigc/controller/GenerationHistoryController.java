package com.semple.aigc.canvas.modules.aigc.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.semple.aigc.canvas.api.aigc.domain.Asset;
import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.domain.R;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.common.web.context.UserKit;
import com.semple.aigc.canvas.common.web.controller.BaseController;
import com.semple.aigc.canvas.modules.aigc.service.GenerationHistoryService;
import com.semple.aigc.canvas.modules.aigc.service.impl.AssetCatalogService;
import com.semple.aigc.canvas.modules.aigc.vo.GenerationHistoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * 生成历史 REST 接口。
 */
@RestController
@RequestMapping("/generation/history")
@RequiredArgsConstructor
@Tag(name = "生成历史", description = "查询生成结果历史")
public class GenerationHistoryController extends BaseController {
    private final GenerationHistoryService historyService;
    private final AssetCatalogService assetCatalogService;

    @Schema(description = "将当前工作区的图片、视频或音频生成结果保存到素材库的请求")
    public record SaveToAssetsRequest(
            @Schema(description = "生成结果记录 ID，从生成历史或任务详情的 assets 中获取；须为当前工作区的有效媒体结果，文本结果不支持保存", example = "1000")
            @NotNull Long generatedAssetId,
            @Schema(description = "目标素材文件夹 ID，须属于当前工作区；省略或 null 表示根目录", example = "1000")
            Long folderId,
            @Schema(description = "素材名称，最多 255 个字符；省略或空白时使用生成结果的 assetNo", example = "测试生成结果")
            String name,
            @Schema(description = "当前工作区的标签 ID 列表，最多 20 项；不可重复或包含 null，省略表示不添加标签", example = "[1000,1001]")
            List<Long> tagIds) {
    }

    @Operation(summary = "将生成历史结果保存到素材库")
    @PostMapping("/save-to-assets")
    public R<Asset> saveToAssets(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "生成结果 ID、目标文件夹、素材名称及标签；只支持图片、视频、音频", required = true)
            @RequestBody
            SaveToAssetsRequest request) {
        return success(assetCatalogService.saveGenerated(request.generatedAssetId(), request.folderId(),
                request.name(), request.tagIds(), UserKit.requireWorkspaceId(), UserKit.requireUserId()));
    }

    /**
     * 分页查询当前工作区的生成历史。省略 type 或使用 ALL 时返回全部类型。
     */
    @Operation(summary = "分页查询生成历史")
    @GetMapping
    public R<Page<GenerationHistoryVO>> list(
            @Parameter(
                    description = "历史类型：ALL 全部，IMAGE 图片，VIDEO 视频，AUDIO 音频；默认 ALL，支持大小写，ALL 也包含文本生成记录",
                    example = "ALL",
                    schema = @Schema(allowableValues = {"ALL", "IMAGE", "VIDEO", "AUDIO"}, defaultValue = "ALL"))
            @RequestParam(defaultValue = "ALL")
            String type,
            @Parameter(description = "页码，从 1 开始，默认 1", example = "1", schema = @Schema(minimum = "1", defaultValue = "1"))
            @RequestParam(defaultValue = "1")
            long pageNum,
            @Parameter(description = "每页条数，默认 20，范围 1–100", example = "20", schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20")
            long pageSize) {
        return success(historyService.list(UserKit.requireWorkspaceId(), parseType(type),
                pageNum, pageSize, UserKit.requireUserId()));
    }

    /**
     * 将接口媒体类型参数转换为生成历史表的 asset_type。
     */
    private Integer parseType(String type) {
        return switch (type == null ? "ALL" : type.trim().toUpperCase(Locale.ROOT)) {
            case "ALL" -> null;
            case "IMAGE" -> 2;
            case "VIDEO" -> 3;
            case "AUDIO" -> 4;
            default -> throw new BizException(ErrorCode.PARAM_ERROR,
                    "type 只能是 ALL、IMAGE、VIDEO 或 AUDIO");
        };
    }
}
