package com.semple.aigc.canvas.modules.aigc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.semple.aigc.canvas.api.aigc.domain.ModelPriceRule;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelPointCalculatorTest {
    private final ModelPointCalculator calculator = new ModelPointCalculator(new ObjectMapper());

    /**
     * 验证 MiniMax 按计费字符结算并向上取整到整数积分。
     */
    @Test
    void calculatesMiniMaxCharactersAtCost() {
        ModelPriceRule rule = rule(4, 671310,
                "{\"usageKey\":\"characters\",\"unitSize\":10000000}");

        assertThat(calculator.estimate(rule, 1, "你好, AI!")).isEqualTo(1);
        assertThat(calculator.actual(rule, 1, Map.of("characters", 163L), 0)).isEqualTo(11);
    }

    /**
     * 验证 Seedance 使用返回的 completionTokens 按百万 Token 单价结算。
     */
    @Test
    void calculatesVideoCompletionTokensAtCost() {
        ModelPriceRule rule = rule(4, 7000,
                "{\"usageKey\":\"completionTokens\",\"unitSize\":1000000,\"estimatedPoints\":756}");

        assertThat(calculator.estimate(rule, 1, null)).isEqualTo(756);
        assertThat(calculator.actual(rule, 1, Map.of("completionTokens", 108000L), 0)).isEqualTo(756);
    }

    /**
     * 验证可灵 Credits 与美元汇率换算公式按实际视频毫秒数结算。
     */
    @Test
    void calculatesKlingCreditsByDuration() {
        ModelPriceRule rule = rule(4, 805572,
                "{\"usageKey\":\"durationMs\",\"unitSize\":6600000,\"estimatedPoints\":611}");

        assertThat(calculator.estimate(rule, 1, null)).isEqualTo(611);
        assertThat(calculator.actual(rule, 1, Map.of("durationMs", 5000L), 5000)).isEqualTo(611);
    }

    /**
     * 验证 Qwen 输入输出 Token 根据单次输入长度命中相同价格阶梯。
     */
    @Test
    void calculatesTieredInputAndOutputTokens() {
        ModelPriceRule rule = rule(5, 1, """
                {"calculation":"TIERED_TOKEN","estimatedPoints":36,"tiers":[
                  {"maxInputTokens":32768,"inputPointsPerMillion":15,"outputPointsPerMillion":150},
                  {"maxInputTokens":131072,"inputPointsPerMillion":30,"outputPointsPerMillion":300},
                  {"maxInputTokens":262144,"inputPointsPerMillion":60,"outputPointsPerMillion":600}
                ]}
                """);

        assertThat(calculator.estimate(rule, 1, null)).isEqualTo(36);
        assertThat(calculator.actual(rule, 1,
                Map.of("promptTokens", 100_000L, "completionTokens", 10_000L), 0)).isEqualTo(6);
    }

    /**
     * 构造测试使用的价格规则。
     */
    private ModelPriceRule rule(int billingMode, long unitPoints, String config) {
        ModelPriceRule rule = new ModelPriceRule();
        rule.setBillingMode(billingMode);
        rule.setUnitPoints(unitPoints);
        rule.setRuleConfig(config);
        return rule;
    }
}
