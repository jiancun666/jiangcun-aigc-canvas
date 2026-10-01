package com.semple.aigc.canvas.modules.aigc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;
import com.semple.aigc.canvas.api.aigc.domain.PointAccount;
import com.semple.aigc.canvas.api.aigc.domain.PointBizOrder;
import com.semple.aigc.canvas.api.aigc.domain.PointLedger;
import com.semple.aigc.canvas.api.aigc.domain.ProjectItem;
import com.semple.aigc.canvas.api.aigc.domain.UserAccount;
import com.semple.aigc.canvas.api.aigc.mapper.UserAccountMapper;
import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.modules.aigc.mapper.ModelDefinitionMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.PointBizOrderMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.ProjectItemMapper;
import com.semple.aigc.canvas.modules.aigc.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 将现有预占、结算、释放账务投影为原型的获取、消耗、返还明细。 */
@Service
@RequiredArgsConstructor
public class PointDisplayService {
    private final PointService pointService;
    private final PointBizOrderMapper orderMapper;
    private final ProjectItemMapper projectMapper;
    private final ModelDefinitionMapper modelMapper;
    private final UserAccountMapper userMapper;

    public enum Category { ACQUIRED, CONSUMED, RETURNED }

    public record Detail(Long id, LocalDateTime occurredAt, long points, String reason,
                         String modelCode, String modelName, Long projectId, String projectName,
                         Long memberUserId, String memberName, String generationTaskId) { }

    public record DetailPage(long total, long aggregate, long availableBalance, List<Detail> records) { }

    public DetailPage details(Long workspaceId, Long userId, Category category, Long memberUserId,
                              LocalDateTime start, LocalDateTime end, long pageNum, long pageSize) {
        if (category == null) {
            throw new BizException(ErrorCode.PARAM_MISSING, "category 不能为空");
        }
        if (pageNum < 1 || pageSize < 1 || pageSize > 100 || (start != null && end != null && start.isAfter(end))) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE, "分页或时间范围无效");
        }
        PointAccount account = pointService.account(workspaceId, userId);
        long balance = account.getAvailableBalance() == null ? 0 : account.getAvailableBalance();
        if (category == Category.ACQUIRED) {
            PointService.LedgerPage page = pointService.ledgers(workspaceId, PointService.GRANT,
                    memberUserId, start, end, pageNum, pageSize, userId);
            List<Detail> records = page.records().stream().map(this::grantDetail).toList();
            return new DetailPage(page.total(), page.aggregate(), balance, records);
        }
        if (account.getId() == null) {
            return new DetailPage(0, 0, balance, List.of());
        }
        LambdaQueryWrapper<PointBizOrder> query = new LambdaQueryWrapper<PointBizOrder>()
                .eq(PointBizOrder::getAccountId, account.getId())
                .eq(memberUserId != null, PointBizOrder::getMemberUserId, memberUserId)
                .ge(start != null, PointBizOrder::getFinishedAt, start)
                .le(end != null, PointBizOrder::getFinishedAt, end)
                .orderByDesc(PointBizOrder::getFinishedAt)
                .orderByDesc(PointBizOrder::getId);
        if (category == Category.CONSUMED) {
            query.eq(PointBizOrder::getOrderStatus, PointService.ORDER_SETTLED);
        } else {
            query.and(group -> group.eq(PointBizOrder::getOrderStatus, PointService.ORDER_REFUNDED)
                    .or(settled -> settled.eq(PointBizOrder::getOrderStatus, PointService.ORDER_SETTLED)
                            .apply("cost_points > actual_points")));
        }
        Page<PointBizOrder> page = orderMapper.selectPage(Page.of(pageNum, pageSize), query);
        List<PointBizOrder> orders = page.getRecords();
        Map<Long, ProjectItem> projects = byId(projectMapper, orders.stream().map(PointBizOrder::getProjectItemId).collect(Collectors.toSet()));
        Map<Long, ModelDefinition> models = byId(modelMapper, orders.stream().map(PointBizOrder::getModelDefinitionId).collect(Collectors.toSet()));
        Map<Long, UserAccount> users = byId(userMapper, orders.stream().map(PointBizOrder::getMemberUserId).collect(Collectors.toSet()));
        List<Detail> records = orders.stream().map(order -> orderDetail(order, category, projects, models, users)).toList();
        Long aggregate = orderMapper.sumDisplayPoints(account.getId(), category.name(), memberUserId, start, end);
        return new DetailPage(page.getTotal(), aggregate == null ? 0 : aggregate, balance, records);
    }

    private Detail grantDetail(PointLedger ledger) {
        return new Detail(ledger.getId(), ledger.getOccurredAt(), ledger.getChangeAmount(),
                ledger.getRemark() == null || ledger.getRemark().isBlank() ? "积分发放" : ledger.getRemark(),
                null, null, ledger.getProjectItemId(), null,
                ledger.getMemberUserId(), null, ledger.getGenerationTaskId());
    }

    private Detail orderDetail(PointBizOrder order, Category category,
                               Map<Long, ProjectItem> projects, Map<Long, ModelDefinition> models,
                               Map<Long, UserAccount> users) {
        boolean failedOrCancelled = PointService.ORDER_REFUNDED == order.getOrderStatus();
        long points = category == Category.CONSUMED ? order.getActualPoints()
                : failedOrCancelled ? order.getCostPoints() : order.getCostPoints() - order.getActualPoints();
        ProjectItem project = order.getProjectItemId() == null ? null : projects.get(order.getProjectItemId());
        ModelDefinition model = order.getModelDefinitionId() == null ? null : models.get(order.getModelDefinitionId());
        UserAccount member = order.getMemberUserId() == null ? null : users.get(order.getMemberUserId());
        return new Detail(order.getId(), order.getFinishedAt(), points,
                category == Category.CONSUMED ? "实际结算" : failedOrCancelled ? "失败或取消返还" : "结算差额返还",
                order.getModelCode(), model == null ? order.getModelCode() : model.getModelName(),
                order.getProjectItemId(), project == null ? null : project.getName(),
                order.getMemberUserId(), member == null ? null : member.getUsername(), order.getGenerationTaskId());
    }

    private <T extends com.semple.aigc.canvas.common.core.domain.BaseEntity> Map<Long, T> byId(
            com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper, Set<Long> ids) {
        ids.remove(null);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mapper.selectBatchIds(ids).stream().collect(Collectors.toMap(T::getId, Function.identity()));
    }
}
