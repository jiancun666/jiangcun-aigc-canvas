package com.semple.aigc.canvas.modules.aigc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semple.aigc.canvas.api.aigc.domain.ModelPriceRule;
import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;

import java.math.BigInteger;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 将供应商接口成本换算为平台积分；统一按 100 积分 = 1 元并向上取整到整数积分。
 */
@Component
@RequiredArgsConstructor
public class ModelPointCalculator {
    private static final int PER_CALL = 1;
    private static final int PER_IMAGE = 2;
    private static final int PER_SECOND = 3;
    private static final int PER_USAGE_UNIT = 4;
    private static final int COMPOSITE = 5;

    private final ObjectMapper objectMapper;

    /**
     * 根据请求阶段可获得的信息计算预占积分；动态计费模型使用规则内的保守预估值。
     */
    public long estimate(ModelPriceRule rule, int outputCount, String prompt) {
        JsonNode config = config(rule);
        return switch (rule.getBillingMode()) {
            case PER_CALL -> positive(rule.getUnitPoints());
            case PER_IMAGE -> multiply(rule.getUnitPoints(), Math.max(1, outputCount));
            case PER_SECOND -> multiply(rule.getUnitPoints(), positive(config.path("estimatedSeconds").asLong(1)));
            case PER_USAGE_UNIT -> estimateUsageRule(rule, config, prompt);
            case COMPOSITE -> positive(config.path("estimatedPoints").asLong(rule.getUnitPoints()));
            default -> throw invalidRule("不支持的计费方式");
        };
    }

    /**
     * 使用供应商返回的实际用量计算最终消耗积分；不足 1 积分的成本按 1 积分结算。
     */
    public long actual(ModelPriceRule rule, int outputCount, Map<String, Object> usage, long durationMs) {
        JsonNode config = config(rule);
        long points = switch (rule.getBillingMode()) {
            case PER_CALL -> positive(rule.getUnitPoints());
            case PER_IMAGE -> multiply(rule.getUnitPoints(), Math.max(1, outputCount));
            case PER_SECOND -> multiply(rule.getUnitPoints(), Math.max(1, divideRoundUp(durationMs, 1000)));
            case PER_USAGE_UNIT -> usagePoints(rule, config, usage);
            case COMPOSITE -> compositePoints(rule, config, usage);
            default -> throw invalidRule("不支持的计费方式");
        };
        return Math.max(1, points);
    }

    /**
     * 计算字符或 Token 单价规则在请求阶段的预占积分。
     */
    private long estimateUsageRule(ModelPriceRule rule, JsonNode config, String prompt) {
        if ("characters".equals(config.path("usageKey").asText()) && prompt != null) {
            return ratePoints(billableCharacters(prompt), rule.getUnitPoints(), unitSize(config));
        }
        long estimatedPoints = config.path("estimatedPoints").asLong(0);
        if (estimatedPoints > 0) {
            return estimatedPoints;
        }
        long estimatedUnits = config.path("estimatedUnits").asLong(1);
        return ratePoints(estimatedUnits, rule.getUnitPoints(), unitSize(config));
    }

    /**
     * 按 rule_config 指定的 usage 字段和计价单位计算实际积分。
     */
    private long usagePoints(ModelPriceRule rule, JsonNode config, Map<String, Object> usage) {
        String usageKey = config.path("usageKey").asText();
        long units = usageLong(usage, usageKey);
        if (units <= 0) {
            throw invalidRule("供应商响应缺少计费字段 " + usageKey);
        }
        return ratePoints(units, rule.getUnitPoints(), unitSize(config));
    }

    /**
     * 计算输入、输出 Token 使用不同阶梯单价的组合规则。
     */
    private long compositePoints(ModelPriceRule rule, JsonNode config, Map<String, Object> usage) {
        if (!"TIERED_TOKEN".equals(config.path("calculation").asText())) {
            throw invalidRule("未知的组合计费公式");
        }
        long inputTokens = usageLong(usage, "promptTokens");
        long outputTokens = usageLong(usage, "completionTokens");
        if (inputTokens <= 0 && outputTokens <= 0) {
            throw invalidRule("供应商响应缺少 Token 用量");
        }
        JsonNode tier = findTier(config.path("tiers"), inputTokens);
        long inputRate = positive(tier.path("inputPointsPerMillion").asLong());
        long outputRate = positive(tier.path("outputPointsPerMillion").asLong());
        BigInteger numerator = BigInteger.valueOf(inputTokens).multiply(BigInteger.valueOf(inputRate))
                .add(BigInteger.valueOf(outputTokens).multiply(BigInteger.valueOf(outputRate)));
        return divideRoundUp(numerator, BigInteger.valueOf(1_000_000L)).longValueExact();
    }

    /**
     * 按单次输入 Token 数选择首个覆盖当前用量的价格阶梯。
     */
    private JsonNode findTier(JsonNode tiers, long inputTokens) {
        if (!tiers.isArray() || tiers.isEmpty()) {
            throw invalidRule("Token 阶梯配置为空");
        }
        for (JsonNode tier : tiers) {
            if (inputTokens <= tier.path("maxInputTokens").asLong()) {
                return tier;
            }
        }
        throw invalidRule("输入 Token 超出价格阶梯范围");
    }

    /**
     * 按 MiniMax 口径统计计费字符：汉字计 2 个字符，其余 Unicode 码点计 1 个字符。
     */
    private long billableCharacters(String text) {
        return text.codePoints().mapToLong(codePoint -> isHan(codePoint) ? 2L : 1L).sum();
    }

    /**
     * 判断 Unicode 码点是否属于汉字脚本。
     */
    private boolean isHan(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }

    /**
     * 读取并校验每个单价对应的用量单位。
     */
    private long unitSize(JsonNode config) {
        return positive(config.path("unitSize").asLong());
    }

    /**
     * 从标准化 usage 中安全读取长整数值。
     */
    private long usageLong(Map<String, Object> usage, String key) {
        if (usage == null || key == null || key.isBlank()) {
            return 0;
        }
        Object value = usage.get(key);
        return value instanceof Number number ? number.longValue() : 0;
    }

    /**
     * 按给定费率计算积分，并在除法时向上取整避免低于真实接口成本。
     */
    private long ratePoints(long units, long unitPoints, long unitSize) {
        BigInteger numerator = BigInteger.valueOf(positive(units))
                .multiply(BigInteger.valueOf(positive(unitPoints)));
        return Math.max(1, divideRoundUp(numerator, BigInteger.valueOf(positive(unitSize))).longValueExact());
    }

    /**
     * 执行带溢出检查的积分乘法。
     */
    private long multiply(long left, long right) {
        return Math.multiplyExact(positive(left), positive(right));
    }

    /**
     * 对长整数执行向上取整除法。
     */
    private long divideRoundUp(long dividend, long divisor) {
        return divideRoundUp(BigInteger.valueOf(Math.max(0, dividend)),
                BigInteger.valueOf(positive(divisor))).longValueExact();
    }

    /**
     * 对任意精度整数执行向上取整除法。
     */
    private BigInteger divideRoundUp(BigInteger dividend, BigInteger divisor) {
        return dividend.add(divisor).subtract(BigInteger.ONE).divide(divisor);
    }

    /**
     * 解析价格规则 JSON；空配置按空对象处理。
     */
    private JsonNode config(ModelPriceRule rule) {
        try {
            if (rule == null || rule.getBillingMode() == null || rule.getUnitPoints() == null) {
                throw invalidRule("价格规则不完整");
            }
            String value = rule.getRuleConfig();
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw invalidRule("价格规则 JSON 无效");
        }
    }

    /**
     * 校验参与计费的整数必须为正数。
     */
    private long positive(long value) {
        if (value <= 0) {
            throw invalidRule("价格规则包含非正数");
        }
        return value;
    }

    /**
     * 构造统一的价格规则配置异常。
     */
    private BizException invalidRule(String message) {
        return new BizException(ErrorCode.CONFLICT, message);
    }
}
