package com.semple.aigc.canvas.modules.aigc.service;

import com.semple.aigc.canvas.modules.aigc.mapper.PointBizOrderMapper;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PointBizOrderMapperSqlTest {
    @Test
    void displayAggregateSqlRendersForBothCategories() throws Exception {
        Configuration configuration = new Configuration();
        configuration.addMapper(PointBizOrderMapper.class);
        var statement = configuration.getMappedStatement(PointBizOrderMapper.class.getName() + ".sumDisplayPoints");

        var consumed = statement.getBoundSql(Map.of("accountId", 1L, "category", "CONSUMED"));
        var returned = statement.getBoundSql(Map.of("accountId", 1L, "category", "RETURNED"));

        assertThat(consumed.getSql()).contains("order_status=3").doesNotContain("order_status=4 OR");
        assertThat(returned.getSql()).contains("order_status=4 OR");
    }
}
