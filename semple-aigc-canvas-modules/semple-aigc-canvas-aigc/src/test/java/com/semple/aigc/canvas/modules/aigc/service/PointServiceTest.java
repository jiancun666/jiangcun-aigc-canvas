package com.semple.aigc.canvas.modules.aigc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.semple.aigc.canvas.api.aigc.domain.PointAccount;
import com.semple.aigc.canvas.api.aigc.domain.PointBizOrder;
import com.semple.aigc.canvas.api.aigc.domain.PointLedger;
import com.semple.aigc.canvas.modules.aigc.service.impl.PointServiceImpl;
import com.semple.aigc.canvas.modules.aigc.mapper.PointAccountMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.PointBizOrderMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.PointLedgerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {
    @Mock private PointAccountMapper accountMapper;
    @Mock private PointLedgerMapper ledgerMapper;
    @Mock private PointBizOrderMapper orderMapper;
    @Mock private SpaceAccessService accessService;

    @Test
    void reserveMovesPointsFromAvailableToReservedAndCreatesOrderSnapshot() {
        PointAccount account = account();
        when(accountMapper.selectWorkspaceForUpdate(7L)).thenReturn(account);
        when(orderMapper.selectTaskForUpdate("task-1")).thenReturn(null);
        when(ledgerMapper.insert(any(PointLedger.class))).thenAnswer(invocation -> {
            invocation.<PointLedger>getArgument(0).setId(55L);
            return 1;
        });
        PointService service = new PointServiceImpl(accountMapper, ledgerMapper, orderMapper, accessService);

        PointBizOrder order = service.reserve(new PointService.ReserveCommand(
                7L, "task-1", 101L, 201L, 301L, 401L, 501L,
                "gpt-image", 30L, "{\"quotedPoints\":30}"), 7L);

        assertThat(account.getAvailableBalance()).isEqualTo(70L);
        assertThat(account.getReservedBalance()).isEqualTo(30L);
        assertThat(account.getConsumedTotal()).isZero();
        assertThat(order.getWorkflowRunId()).isEqualTo(201L);
        assertThat(order.getWorkflowStepRunId()).isEqualTo(301L);
        assertThat(order.getModelDefinitionId()).isEqualTo(401L);
        assertThat(order.getPriceRuleId()).isEqualTo(501L);
        assertThat(order.getConsumeLedgerId()).isEqualTo(55L);
        verify(accountMapper).updateById(account);
        verify(orderMapper).insert(order);
    }

    @Test
    void settleConsumesActualPointsAndReleasesUnusedReservation() {
        PointAccount account = account();
        account.setAvailableBalance(70L);
        account.setReservedBalance(30L);
        PointBizOrder order = new PointBizOrder();
        order.setId(8L);
        order.setGenerationTaskId("task-1");
        order.setWorkspaceId(7L);
        order.setMemberUserId(9L);
        order.setCostPoints(30L);
        order.setOrderStatus(PointService.ORDER_RUNNING);
        order.setLockVersion(0);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.selectTaskForUpdate("task-1")).thenReturn(order);
        when(accountMapper.selectWorkspaceForUpdate(7L)).thenReturn(account);
        PointService service = new PointServiceImpl(accountMapper, ledgerMapper, orderMapper, accessService);

        PointBizOrder settled = service.settle("task-1", 20L, "{\"imageCount\":1}");

        assertThat(account.getAvailableBalance()).isEqualTo(80L);
        assertThat(account.getReservedBalance()).isZero();
        assertThat(account.getConsumedTotal()).isEqualTo(20L);
        assertThat(settled.getOrderStatus()).isEqualTo(PointService.ORDER_SETTLED);
        verify(ledgerMapper).insert(any(PointLedger.class));
    }

    private PointAccount account() {
        PointAccount account = new PointAccount();
        account.setId(1L);
        account.setWorkspaceId(7L);
        account.setAvailableBalance(100L);
        account.setReservedBalance(0L);
        account.setAcquiredTotal(100L);
        account.setConsumedTotal(0L);
        account.setRefundedTotal(0L);
        account.setClearedTotal(0L);
        account.setAccountStatus(1);
        account.setLockVersion(0);
        return account;
    }
}
