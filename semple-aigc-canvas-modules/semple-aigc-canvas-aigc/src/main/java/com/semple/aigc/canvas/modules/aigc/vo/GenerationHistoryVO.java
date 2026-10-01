package com.semple.aigc.canvas.modules.aigc.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Date;

/** 通用生成历史查询结果。日期分组由前端依据 createdAt 完成。 */
public record GenerationHistoryVO(
        Long id,
        String assetNo,
        Integer assetType,
        String storageUrl,
        String thumbnailUrl,
        String prompt,
        String creator,
        Date createdAt,
        String model,
        JsonNode generationParameters,
        JsonNode aspectRatio,
        JsonNode resolution,
        JsonNode referenceImages,
        JsonNode referenceAudio,
        String mimeType,
        Integer width,
        Integer height,
        Long durationMs,
        Integer status,
        Long savedAssetId) {
}
