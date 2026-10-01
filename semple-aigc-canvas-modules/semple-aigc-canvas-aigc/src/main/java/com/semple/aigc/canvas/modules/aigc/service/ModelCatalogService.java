package com.semple.aigc.canvas.modules.aigc.service;

import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;
import com.semple.aigc.canvas.api.aigc.domain.ModelPriceRule;

import java.util.List;

/**
 * 模型目录与服务端可信报价服务。
 */
public interface ModelCatalogService {
    /**
     * 查询启用的模型；modelType 为空时返回全部类型。
     */
    List<ModelDefinition> availableModels(Integer modelType);

    /**
     * 根据模型、输出数量和输入文本计算预占积分，客户端不能直接指定价格。
     */
    ModelQuote quote(Long modelDefinitionId, int imageCount, String prompt);

    /**
     * 获取任意类型的启用模型定义。
     */
    ModelDefinition requireModel(Long modelDefinitionId);

    /**
     * 冻结后的模型报价。
     */
    record ModelQuote(ModelDefinition model, ModelPriceRule priceRule,
                      int imageCount, long points, String priceSnapshot) {
    }
}
