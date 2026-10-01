package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型积分价格规则，按生效时间进行版本化。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_model_price_rule")
public class ModelPriceRule extends BaseEntity {
    /**
     * 模型定义 ID。
     */
    private Long modelDefinitionId;
    /**
     * 规则名称。
     */
    private String ruleName;
    /**
     * 计费方式：1按次，2按张，3按秒，4按Token，5组合规则。
     */
    private Integer billingMode;
    /**
     * 每个计费单位对应的积分。
     */
    private Long unitPoints;
    /**
     * 匹配条件及阶梯计价 JSON。
     */
    private String ruleConfig;
    /**
     * 生效时间。
     */
    private LocalDateTime effectiveFrom;
    /**
     * 失效时间，为空表示长期有效。
     */
    private LocalDateTime effectiveTo;
    /**
     * 是否启用：0否，1是。
     */
    private Integer enabled;
}
