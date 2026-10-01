package com.semple.aigc.canvas.modules.aigc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;
import com.semple.aigc.canvas.api.aigc.domain.ModelPriceRule;
import com.semple.aigc.canvas.modules.aigc.mapper.ModelDefinitionMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.ModelPriceRuleMapper;
import com.semple.aigc.canvas.modules.aigc.service.impl.ModelCatalogServiceImpl;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelCatalogServiceTest {
    @Mock private ModelDefinitionMapper modelMapper;
    @Mock private ModelPriceRuleMapper priceRuleMapper;

    @Test
    void quoteUsesServerPriceRuleAndImageCount() {
        ModelDefinition model = new ModelDefinition();
        model.setId(10L);
        model.setProviderCode("DASHSCOPE");
        model.setModelCode("wanx-v1");
        model.setModelType(2);
        model.setEnabled(1);
        ModelPriceRule rule = new ModelPriceRule();
        rule.setId(20L);
        rule.setModelDefinitionId(10L);
        rule.setRuleName("按张计费");
        rule.setBillingMode(2);
        rule.setUnitPoints(12L);
        rule.setEffectiveFrom(LocalDateTime.now().minusDays(1));
        rule.setEnabled(1);
        when(modelMapper.selectById(10L)).thenReturn(model);
        when(priceRuleMapper.selectOne(any())).thenReturn(rule);
        ObjectMapper objectMapper = new ObjectMapper();
        ModelCatalogService service = new ModelCatalogServiceImpl(modelMapper, priceRuleMapper,
                objectMapper, new ModelPointCalculator(objectMapper));

        ModelCatalogService.ModelQuote quote = service.quote(10L, 3, null);

        assertThat(quote.points()).isEqualTo(36L);
        assertThat(quote.priceSnapshot()).contains("\"quotedPoints\":36");
    }
}
