package com.semple.aigc.canvas.modules.aigc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;
import com.semple.aigc.canvas.api.aigc.domain.ModelPriceRule;
import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.modules.aigc.mapper.ModelDefinitionMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.ModelPriceRuleMapper;
import com.semple.aigc.canvas.modules.aigc.service.ModelCatalogService;
import com.semple.aigc.canvas.modules.aigc.service.ModelPointCalculator;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 数据库模型目录与按生效时间匹配的价格计算实现。
 */
@Service
@RequiredArgsConstructor
public class ModelCatalogServiceImpl implements ModelCatalogService {
    private final ModelDefinitionMapper modelMapper;
    private final ModelPriceRuleMapper priceRuleMapper;
    private final ObjectMapper objectMapper;
    private final ModelPointCalculator pointCalculator;

    /**
     * 查询启用的模型目录，并可按文本、图片、视频或音频类型筛选。
     */
    @Override
    public List<ModelDefinition> availableModels(Integer modelType) {
        if (modelType != null && (modelType < 1 || modelType > 4)) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE, "模型类型必须在 1 到 4 之间");
        }
        List<ModelDefinition> models = modelMapper.selectList(new LambdaQueryWrapper<ModelDefinition>()
                .eq(modelType != null, ModelDefinition::getModelType, modelType)
                .eq(ModelDefinition::getEnabled, 1)
                .orderByAsc(ModelDefinition::getProviderCode, ModelDefinition::getModelName));
        // 密钥引用属于服务端配置，不应通过模型目录接口返回。
        models.forEach(model -> model.setCredentialRef(null));
        return models;
    }

    /**
     * 根据当前生效价格规则计算本次请求的预估积分并生成不可变报价快照。
     */
    @Override
    public ModelQuote quote(Long modelDefinitionId, int imageCount, String prompt) {
        if (imageCount < 1 || imageCount > 16) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE, "生成张数必须在 1 到 16 之间");
        }
        ModelDefinition model = requireModel(modelDefinitionId);
        LocalDateTime now = LocalDateTime.now();
        // 同一模型可能存在多期价格，始终选择当前时间已生效且尚未失效的最新一条。
        ModelPriceRule rule = priceRuleMapper.selectOne(new LambdaQueryWrapper<ModelPriceRule>()
                .eq(ModelPriceRule::getModelDefinitionId, modelDefinitionId)
                .eq(ModelPriceRule::getEnabled, 1)
                .le(ModelPriceRule::getEffectiveFrom, now)
                .and(q -> q.isNull(ModelPriceRule::getEffectiveTo).or().gt(ModelPriceRule::getEffectiveTo, now))
                .orderByDesc(ModelPriceRule::getEffectiveFrom)
                .last("LIMIT 1"));
        if (rule == null) {
            throw new BizException(ErrorCode.CONFLICT, "模型暂未配置有效价格");
        }
        long points = pointCalculator.estimate(rule, imageCount, prompt);
        // 任务保存报价快照，防止价格规则后续变化影响历史任务结算和审计。
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("modelDefinitionId", model.getId());
        snapshot.put("providerCode", model.getProviderCode());
        snapshot.put("modelCode", model.getModelCode());
        snapshot.put("priceRuleId", rule.getId());
        snapshot.put("ruleName", rule.getRuleName());
        snapshot.put("billingMode", rule.getBillingMode());
        snapshot.put("unitPoints", rule.getUnitPoints());
        snapshot.put("ruleConfig", rule.getRuleConfig());
        snapshot.put("imageCount", imageCount);
        snapshot.put("quotedPoints", points);
        snapshot.put("quotedAt", now.toString());
        try {
            return new ModelQuote(model, rule, imageCount, points, objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "生成价格快照失败");
        }
    }

    /**
     * 获取一个启用且类型合法的模型定义。
     */
    @Override
    public ModelDefinition requireModel(Long modelDefinitionId) {
        ModelDefinition model = modelMapper.selectById(modelDefinitionId);
        if (model == null || !Integer.valueOf(1).equals(model.getEnabled())
                || model.getModelType() == null || model.getModelType() < 1 || model.getModelType() > 4) {
            throw new BizException(ErrorCode.NOT_FOUND, "可用模型不存在");
        }
        return model;
    }
}
