package com.semple.aigc.canvas.modules.aigc.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.semple.aigc.canvas.api.aigc.domain.PointAccount;
import com.semple.aigc.canvas.api.aigc.domain.PointBizOrder;
import com.semple.aigc.canvas.api.aigc.mapper.UserAccountMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.ModelDefinitionMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.PointBizOrderMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.ProjectItemMapper;
import com.semple.aigc.canvas.modules.aigc.service.impl.PointDisplayService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PointDisplayServiceTest {
    @Test
    void settledOrderShowsActualConsumptionAndUnusedReservationReturn() {
        PointService pointService = mock(PointService.class);
        PointBizOrderMapper orderMapper = mock(PointBizOrderMapper.class);
        PointDisplayService service = new PointDisplayService(pointService, orderMapper,
                mock(ProjectItemMapper.class), mock(ModelDefinitionMapper.class), mock(UserAccountMapper.class));
        PointAccount account = new PointAccount();
        account.setId(5L);
        account.setAvailableBalance(80L);
        when(pointService.account(1L, 2L)).thenReturn(account);
        PointBizOrder order = new PointBizOrder();
        order.setId(9L);
        order.setOrderStatus(PointService.ORDER_SETTLED);
        order.setCostPoints(30L);
        order.setActualPoints(18L);
        order.setFinishedAt(LocalDateTime.of(2026, 9, 30, 10, 0));
        Page<PointBizOrder> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(order));
        when(orderMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(orderMapper.sumDisplayPoints(eq(5L), eq("CONSUMED"), any(), any(), any())).thenReturn(18L);
        when(orderMapper.sumDisplayPoints(eq(5L), eq("RETURNED"), any(), any(), any())).thenReturn(12L);

        PointDisplayService.DetailPage consumed = service.details(1L, 2L,
                PointDisplayService.Category.CONSUMED, null, null, null, 1, 20);
        PointDisplayService.DetailPage returned = service.details(1L, 2L,
                PointDisplayService.Category.RETURNED, null, null, null, 1, 20);

        assertThat(consumed.records().get(0).points()).isEqualTo(18);
        assertThat(returned.records().get(0).points()).isEqualTo(12);
        assertThat(returned.records().get(0).reason()).isEqualTo("结算差额返还");
        assertThat(returned.aggregate()).isEqualTo(12);
    }
}
