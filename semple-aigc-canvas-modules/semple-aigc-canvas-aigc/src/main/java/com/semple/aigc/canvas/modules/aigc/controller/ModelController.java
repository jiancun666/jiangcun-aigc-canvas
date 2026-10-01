package com.semple.aigc.canvas.modules.aigc.controller;

import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;
import com.semple.aigc.canvas.common.core.domain.R;
import com.semple.aigc.canvas.common.web.controller.BaseController;
import com.semple.aigc.canvas.modules.aigc.service.ModelCatalogService;
import com.semple.aigc.canvas.modules.aigc.service.ModelCatalogService.ModelQuote;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型目录与服务端报价接口。
 */
@RestController
@RequestMapping("/models")
@RequiredArgsConstructor
@Tag(name = "模型目录", description = "可用文本、图片、视频、音频模型与服务端积分报价")
public class ModelController extends BaseController {
    private final ModelCatalogService modelCatalogService;

    /**
     * 查询全部启用模型，可按类型筛选：1 文本、2 图片、3 视频、4 音频。
     */
    @Operation(summary = "查询可用模型")
    @GetMapping
    public R<List<ModelDefinition>> models(
            @Parameter(description = "模型类型：1 文本，2 图片，3 视频，4 音频；省略返回全部启用模型", example = "1", schema = @Schema(allowableValues = {"1", "2", "3", "4"}))
            @RequestParam(required = false)
            Integer modelType) {
        return success(modelCatalogService.availableModels(modelType));
    }

    /**
     * 按模型和期望输出数量计算可信报价。
     */
    @Operation(summary = "模型报价", description = "价格完全由服务端有效价格规则计算；按字符计费时可传入提示词")
    @GetMapping("/{id}/quote")
    public R<ModelQuote> quote(
            @Parameter(description = "启用的模型定义 ID，从 GET /models 返回的 id 获取", example = "1000", required = true)
            @PathVariable
            Long id,
            @Parameter(description = "期望输出数量，范围 1–16，默认 1；文本、视频、音频也沿用该字段", example = "1", schema = @Schema(minimum = "1", maximum = "16", defaultValue = "1"))
            @RequestParam(defaultValue = "1")
            int imageCount,
            @Parameter(description = "可选输入文本；按字符计费的模型使用它计算预估积分", example = "请用一句中文介绍你自己。")
            @RequestParam(required = false)
            String prompt) {
        return success(modelCatalogService.quote(id, imageCount, prompt));
    }
}
