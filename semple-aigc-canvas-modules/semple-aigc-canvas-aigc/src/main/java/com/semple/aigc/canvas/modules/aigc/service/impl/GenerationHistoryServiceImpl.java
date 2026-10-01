package com.semple.aigc.canvas.modules.aigc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semple.aigc.canvas.api.aigc.domain.GeneratedAsset;
import com.semple.aigc.canvas.api.aigc.domain.UserAccount;
import com.semple.aigc.canvas.api.aigc.domain.WorkflowStepRun;
import com.semple.aigc.canvas.api.aigc.mapper.UserAccountMapper;
import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.modules.aigc.mapper.GeneratedAssetMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.WorkflowStepRunMapper;
import com.semple.aigc.canvas.modules.aigc.service.GenerationHistoryService;
import com.semple.aigc.canvas.modules.aigc.service.SpaceAccessService;
import com.semple.aigc.canvas.modules.aigc.vo.GenerationHistoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 按类型查询生成历史，并批量关联创建者和模型快照。
 */
@Service
@RequiredArgsConstructor
public class GenerationHistoryServiceImpl implements GenerationHistoryService {
    private final GeneratedAssetMapper generatedAssetMapper;
    private final WorkflowStepRunMapper stepRunMapper;
    private final UserAccountMapper userAccountMapper;
    private final SpaceAccessService accessService;
    private final ObjectMapper objectMapper;

    /**
     * 按资产类型分页查询生成历史并返回通用历史 VO。
     */
    @Override
    public Page<GenerationHistoryVO> list(Long workspaceId, Integer assetType,
                                           long pageNum, long pageSize, Long userId) {
        Page<GeneratedAsset> assets = queryAssets(workspaceId, assetType, pageNum, pageSize, userId);
        Map<Long, UserAccount> creators = loadCreators(assets);
        Map<Long, WorkflowStepRun> steps = loadSteps(assets);

        Page<GenerationHistoryVO> result = new Page<>(pageNum, pageSize, assets.getTotal());
        result.setRecords(assets.getRecords().stream().map(asset -> toVO(asset,
                creators.get(asset.getCreatorUserId()), steps.get(asset.getStepRunId()))).toList());
        return result;
    }

    /**
     * 查询当前工作区的模型生成记录并按创建时间倒序排列。
     */
    private Page<GeneratedAsset> queryAssets(Long workspaceId, Integer assetType,
                                              long pageNum, long pageSize, Long userId) {
        accessService.assertReadable(workspaceId, userId);
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            throw new BizException(ErrorCode.PARAM_ERROR, "分页参数无效");
        }

        return generatedAssetMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<GeneratedAsset>()
                        .eq(GeneratedAsset::getWorkspaceId, workspaceId)
                        .eq(assetType != null, GeneratedAsset::getAssetType, assetType)
                        .eq(GeneratedAsset::getAssetSource, 1)
                        .orderByDesc(GeneratedAsset::getCreateTime)
                        .orderByDesc(GeneratedAsset::getId));
    }

    /**
     * 批量加载当前分页记录关联的创建者。
     */
    private Map<Long, UserAccount> loadCreators(Page<GeneratedAsset> assets) {
        List<Long> creatorIds = assets.getRecords().stream().map(GeneratedAsset::getCreatorUserId)
                .filter(Objects::nonNull).distinct().toList();
        return creatorIds.isEmpty() ? Map.of()
                : userAccountMapper.selectBatchIds(creatorIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
    }

    /**
     * 批量加载当前分页记录关联的步骤运行快照。
     */
    private Map<Long, WorkflowStepRun> loadSteps(Page<GeneratedAsset> assets) {
        List<Long> stepRunIds = assets.getRecords().stream().map(GeneratedAsset::getStepRunId)
                .filter(Objects::nonNull).distinct().toList();
        return stepRunIds.isEmpty() ? Map.of()
                : stepRunMapper.selectBatchIds(stepRunIds).stream()
                .collect(Collectors.toMap(WorkflowStepRun::getId, Function.identity()));
    }

    /**
     * 将生成资产、创建者和模型快照组装为通用历史 VO。
     */
    private GenerationHistoryVO toVO(GeneratedAsset asset, UserAccount creator, WorkflowStepRun step) {
        JsonNode parameters = readJson(asset.getGenerationConfig());
        JsonNode selected = parameters != null && parameters.has("config")
                ? parameters.get("config") : parameters;
        return new GenerationHistoryVO(asset.getId(), asset.getAssetNo(), asset.getAssetType(),
                asset.getStorageUrl(), asset.getThumbnailUrl(), asset.getPromptSnapshot(),
                creator == null ? null : creator.getUsername(), asset.getCreateTime(),
                step == null ? null : step.getModelName(), parameters,
                firstValue(selected, "aspectRatio", "aspect_ratio", "ratio"),
                firstValue(selected, "resolution", "size"),
                firstValue(selected, "referenceImages", "referenceImage", "reference_images"),
                firstValue(selected, "referenceAudio", "reference_audio"),
                asset.getMimeType(), asset.getWidth(), asset.getHeight(), asset.getDurationMs(),
                asset.getAssetStatus(), asset.getAssetId());
    }

    /**
     * 解析生成参数 JSON。
     */
    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "生成历史参数 JSON 损坏");
        }
    }

    /**
     * 从多个兼容字段名中读取第一个非空参数。
     */
    private JsonNode firstValue(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }
}
