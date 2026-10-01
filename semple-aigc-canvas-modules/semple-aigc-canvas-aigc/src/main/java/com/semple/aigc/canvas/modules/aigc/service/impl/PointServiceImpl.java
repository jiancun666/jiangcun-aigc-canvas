package com.semple.aigc.canvas.modules.aigc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.semple.aigc.canvas.api.aigc.domain.PointAccount;
import com.semple.aigc.canvas.api.aigc.domain.PointBizOrder;
import com.semple.aigc.canvas.api.aigc.domain.PointLedger;
import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.modules.aigc.mapper.PointAccountMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.PointBizOrderMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.PointLedgerMapper;
import com.semple.aigc.canvas.modules.aigc.service.PointService;
import com.semple.aigc.canvas.modules.aigc.service.SpaceAccessService;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 使用“可用余额 + 预占余额”实现生成任务的可信计费。
 */
@Service
@RequiredArgsConstructor
public class PointServiceImpl extends ServiceImpl<PointAccountMapper, PointAccount> implements PointService {
    private final PointAccountMapper accountMapper;
    private final PointLedgerMapper ledgerMapper;
    private final PointBizOrderMapper orderMapper;
    private final SpaceAccessService accessService;

    /**
     * 查询工作区积分账户；尚未开户时返回只读的零余额视图。
     */
    @Override
    public PointAccount account(Long workspaceId, Long userId) {
        accessService.assertReadable(workspaceId, userId);
        PointAccount account = selectAccount(workspaceId);
        return account == null ? emptyAccount(workspaceId) : account;
    }

    /**
     * 分页查询积分流水，并返回相同筛选条件下的变动金额合计。
     */
    @Override
    public LedgerPage ledgers(Long workspaceId, Integer ledgerType, Long memberUserId,
                              LocalDateTime start, LocalDateTime end, long pageNum, long pageSize, Long userId) {
        accessService.assertReadable(workspaceId, userId);
        if (pageNum < 1 || pageSize < 1) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE, "分页参数必须大于 0");
        }
        PointAccount account = selectAccount(workspaceId);
        if (account == null) {
            return new LedgerPage(0, 0, java.util.List.of());
        }
        LambdaQueryWrapper<PointLedger> query = ledgerQuery(account.getId(), ledgerType, memberUserId, start, end);
        Page<PointLedger> page = ledgerMapper.selectPage(Page.of(pageNum, Math.min(pageSize, 100)), query);
        Long aggregate = ledgerMapper.selectObjs(ledgerQuery(account.getId(), ledgerType, memberUserId, start, end)
                        .select(PointLedger::getChangeAmount)).stream()
                .map(value -> ((Number) value).longValue()).reduce(0L, Long::sum);
        return new LedgerPage(page.getTotal(), aggregate, page.getRecords());
    }

    /**
     * 向工作区发放积分，使用幂等键避免重复入账。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public PointLedger grant(Long workspaceId, long amount, String idempotencyKey,
                             String remark, Long operatorId) {
        requirePositive(amount);
        // 在修改余额前锁定幂等键对应流水，重复请求直接返回首次入账结果。
        PointLedger existing = ledgerMapper.selectKeyForUpdate(idempotencyKey);
        if (existing != null) {
            return existing;
        }
        PointAccount account = lockOrCreateAccount(workspaceId);
        long availableBefore = account.getAvailableBalance();
        long reservedBefore = account.getReservedBalance();
        account.setAvailableBalance(Math.addExact(availableBefore, amount));
        account.setAcquiredTotal(Math.addExact(account.getAcquiredTotal(), amount));
        bump(account);
        return insertLedger(account, GRANT, amount, availableBefore, 0, reservedBefore,
                operatorId, null, null, null, null, null, null, null,
                idempotencyKey, remark, null);
    }

    /**
     * 为生成任务预占积分，并创建积分业务单和预占流水。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public PointBizOrder reserve(ReserveCommand command, Long userId) {
        requirePositive(command.reservedPoints());
        accessService.assertReadable(command.workspaceId(), userId);
        // 锁定账户行后再检查余额和修改可用/预占余额，避免并发超额消费。
        PointAccount account = accountMapper.selectWorkspaceForUpdate(command.workspaceId());
        normalize(account);
        if (account == null) {
            throw new BizException(ErrorCode.POINTS_INSUFFICIENT);
        }
        PointBizOrder existing = orderMapper.selectTaskForUpdate(command.generationTaskId());
        if (existing != null) {
            return existing;
        }
        if (account.getAccountStatus() != NORMAL || account.getAvailableBalance() < command.reservedPoints()) {
            throw new BizException(ErrorCode.POINTS_INSUFFICIENT);
        }
        long availableBefore = account.getAvailableBalance();
        long reservedBefore = account.getReservedBalance();
        account.setAvailableBalance(availableBefore - command.reservedPoints());
        account.setReservedBalance(Math.addExact(reservedBefore, command.reservedPoints()));
        bump(account);

        String orderNo = "PO" + compactUuid();
        PointLedger ledger = insertLedger(account, RESERVE, -command.reservedPoints(), availableBefore,
                command.reservedPoints(), reservedBefore, null, userId, command.projectItemId(),
                command.workflowRunId(), command.workflowStepRunId(), command.modelCode(),
                command.generationTaskId(), orderNo, "reserve:" + command.generationTaskId(),
                "生成任务预占积分", null);
        PointBizOrder order = new PointBizOrder();
        order.setBizOrderNo(orderNo);
        order.setGenerationTaskId(command.generationTaskId());
        order.setAccountId(account.getId());
        order.setWorkspaceId(command.workspaceId());
        order.setMemberUserId(userId);
        order.setProjectItemId(command.projectItemId());
        order.setWorkflowRunId(command.workflowRunId());
        order.setWorkflowStepRunId(command.workflowStepRunId());
        order.setModelDefinitionId(command.modelDefinitionId());
        order.setPriceRuleId(command.priceRuleId());
        order.setModelCode(command.modelCode());
        order.setCostPoints(command.reservedPoints());
        order.setPriceSnapshot(command.priceSnapshot());
        order.setOrderStatus(ORDER_RESERVED);
        order.setConsumeLedgerId(ledger.getId());
        order.setChargedAt(LocalDateTime.now());
        order.setLockVersion(0);
        orderMapper.insert(order);
        return order;
    }

    /**
     * 将已预占的积分订单标记为模型执行中。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public PointBizOrder markRunning(String generationTaskId) {
        PointBizOrder order = requireOrderForUpdate(generationTaskId);
        if (order.getOrderStatus() == ORDER_RESERVED) {
            order.setOrderStatus(ORDER_RUNNING);
            updateOrder(order);
        }
        return order;
    }

    /**
     * 按供应商实际用量结算订单，并返还未消费的预占积分。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public PointBizOrder settle(String generationTaskId, long actualPoints, String usageSnapshot) {
        requirePositive(actualPoints);
        PointBizOrder snapshot = requireOrder(generationTaskId);
        PointAccount account = accountMapper.selectWorkspaceForUpdate(snapshot.getWorkspaceId());
        normalize(account);
        PointBizOrder order = requireOrderForUpdate(generationTaskId);
        if (order.getOrderStatus() == ORDER_SETTLED) {
            return order;
        }
        if (order.getOrderStatus() != ORDER_RESERVED && order.getOrderStatus() != ORDER_RUNNING) {
            throw new BizException(ErrorCode.CONFLICT, "当前积分订单不能结算");
        }
        long reserved = order.getCostPoints();
        long additional = Math.max(0, actualPoints - reserved);
        if (account == null || account.getReservedBalance() < reserved) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "积分预占余额异常");
        }
        if (additional > account.getAvailableBalance()) {
            // 实际消费超出预占且余额不足时不猜测扣款，标记待对账并保留资金现场。
            order.setActualPoints(actualPoints);
            order.setUsageSnapshot(usageSnapshot);
            order.setOrderStatus(ORDER_UNCERTAIN);
            order.setFailureCode("POINT_SETTLEMENT_INSUFFICIENT");
            order.setFailureMessage("实际用量超过预占积分且可用余额不足，等待对账");
            updateOrder(order);
            return order;
        }
        long availableBefore = account.getAvailableBalance();
        long reservedBefore = account.getReservedBalance();
        long availableChange = reserved - actualPoints;
        account.setAvailableBalance(Math.addExact(availableBefore, availableChange));
        account.setReservedBalance(reservedBefore - reserved);
        account.setConsumedTotal(Math.addExact(account.getConsumedTotal(), actualPoints));
        if (availableChange > 0) {
            account.setRefundedTotal(Math.addExact(account.getRefundedTotal(), availableChange));
        }
        bump(account);
        PointLedger ledger = insertLedger(account, SETTLE, availableChange, availableBefore, -reserved,
                reservedBefore, null, order.getMemberUserId(), order.getProjectItemId(),
                order.getWorkflowRunId(), order.getWorkflowStepRunId(), order.getModelCode(),
                generationTaskId, order.getBizOrderNo(), "settle:" + generationTaskId,
                "生成任务按实际用量结算", order.getConsumeLedgerId());
        order.setActualPoints(actualPoints);
        order.setUsageSnapshot(usageSnapshot);
        order.setOrderStatus(ORDER_SETTLED);
        order.setRefundLedgerId(availableChange > 0 ? ledger.getId() : null);
        order.setFinishedAt(LocalDateTime.now());
        updateOrder(order);
        return order;
    }

    /**
     * 生成失败或取消时释放全部预占积分。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public PointBizOrder refund(String generationTaskId, String failureCode, String failureMessage) {
        PointBizOrder snapshot = requireOrder(generationTaskId);
        PointAccount account = accountMapper.selectWorkspaceForUpdate(snapshot.getWorkspaceId());
        normalize(account);
        PointBizOrder order = requireOrderForUpdate(generationTaskId);
        if (order.getOrderStatus() == ORDER_REFUNDED) {
            return order;
        }
        if (order.getOrderStatus() != ORDER_RESERVED && order.getOrderStatus() != ORDER_RUNNING) {
            throw new BizException(ErrorCode.CONFLICT, "只有未结算生成任务可以释放预占积分");
        }
        if (account == null || account.getReservedBalance() < order.getCostPoints()) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "积分预占余额异常");
        }
        long availableBefore = account.getAvailableBalance();
        long reservedBefore = account.getReservedBalance();
        account.setAvailableBalance(Math.addExact(availableBefore, order.getCostPoints()));
        account.setReservedBalance(reservedBefore - order.getCostPoints());
        account.setRefundedTotal(Math.addExact(account.getRefundedTotal(), order.getCostPoints()));
        bump(account);
        PointLedger ledger = insertLedger(account, RELEASE, order.getCostPoints(), availableBefore,
                -order.getCostPoints(), reservedBefore, null, order.getMemberUserId(),
                order.getProjectItemId(), order.getWorkflowRunId(), order.getWorkflowStepRunId(),
                order.getModelCode(), generationTaskId, order.getBizOrderNo(),
                "release:" + generationTaskId, "生成失败或取消释放预占积分", order.getConsumeLedgerId());
        order.setOrderStatus(ORDER_REFUNDED);
        order.setRefundLedgerId(ledger.getId());
        order.setFailureCode(failureCode);
        order.setFailureMessage(failureMessage);
        order.setFinishedAt(LocalDateTime.now());
        order.setRefundedAt(LocalDateTime.now());
        updateOrder(order);
        return order;
    }

    /**
     * 工作区解散时清空可用积分；存在运行中预占时拒绝清户。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public PointLedger clearWorkspace(Long workspaceId, String idempotencyKey, Long operatorId) {
        PointLedger existing = ledgerMapper.selectKeyForUpdate(idempotencyKey);
        if (existing != null) {
            return existing;
        }
        PointAccount account = accountMapper.selectWorkspaceForUpdate(workspaceId);
        normalize(account);
        if (account == null || account.getAvailableBalance() == 0) {
            throw new BizException(ErrorCode.CONFLICT, "工作区积分余额已为零或账户不存在");
        }
        if (account.getReservedBalance() > 0) {
            throw new BizException(ErrorCode.CONFLICT, "存在运行中的积分预占，不能关闭账户");
        }
        long before = account.getAvailableBalance();
        account.setAvailableBalance(0L);
        account.setClearedTotal(Math.addExact(account.getClearedTotal(), before));
        account.setAccountStatus(3);
        bump(account);
        return insertLedger(account, CLEAR, -before, before, 0, 0,
                operatorId, null, null, null, null, null, null, null,
                idempotencyKey, "工作区解散积分清零", null);
    }

    /**
     * 增加账户锁版本并持久化余额变更。
     */
    private void bump(PointAccount account) {
        account.setLockVersion(account.getLockVersion() + 1);
        accountMapper.updateById(account);
    }

    /**
     * 增加订单锁版本并持久化状态变更。
     */
    private void updateOrder(PointBizOrder order) {
        order.setLockVersion(order.getLockVersion() + 1);
        orderMapper.updateById(order);
    }

    /**
     * 获取工作区账户行锁；账户不存在时以并发安全方式创建后重新加锁。
     */
    private PointAccount lockOrCreateAccount(Long workspaceId) {
        PointAccount account = accountMapper.selectWorkspaceForUpdate(workspaceId);
        if (account != null) {
            normalize(account);
            return account;
        }
        accountMapper.insertIgnore(workspaceId);
        account = accountMapper.selectWorkspaceForUpdate(workspaceId);
        normalize(account);
        return account;
    }

    /**
     * 构造未开户工作区使用的零余额账户视图。
     */
    private PointAccount emptyAccount(Long workspaceId) {
        PointAccount account = new PointAccount();
        account.setWorkspaceId(workspaceId);
        account.setAvailableBalance(0L);
        account.setReservedBalance(0L);
        account.setAcquiredTotal(0L);
        account.setConsumedTotal(0L);
        account.setRefundedTotal(0L);
        account.setClearedTotal(0L);
        account.setAccountStatus(NORMAL);
        account.setLockVersion(0);
        return account;
    }

    /**
     * 兼容旧数据，把空的预占余额规范为零。
     */
    private void normalize(PointAccount account) {
        if (account != null && account.getReservedBalance() == null) {
            account.setReservedBalance(0L);
        }
    }

    /**
     * 查询并规范化工作区积分账户。
     */
    private PointAccount selectAccount(Long workspaceId) {
        PointAccount account = accountMapper.selectOne(new LambdaQueryWrapper<PointAccount>()
                .eq(PointAccount::getWorkspaceId, workspaceId));
        normalize(account);
        return account;
    }

    /**
     * 查询任务关联的积分业务单，不存在时抛出业务异常。
     */
    private PointBizOrder requireOrder(String taskId) {
        PointBizOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PointBizOrder>()
                .eq(PointBizOrder::getGenerationTaskId, taskId));
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "积分业务单不存在");
        }
        return order;
    }

    /**
     * 查询并锁定任务关联的积分业务单。
     */
    private PointBizOrder requireOrderForUpdate(String taskId) {
        PointBizOrder order = orderMapper.selectTaskForUpdate(taskId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "积分业务单不存在");
        }
        return order;
    }

    /**
     * 写入包含可用余额和预占余额前后值的完整积分流水。
     */
    private PointLedger insertLedger(PointAccount account, int type, long availableChange,
                                     long availableBefore, long reservedChange, long reservedBefore,
                                     Long operatorId, Long memberId, Long projectId, Long workflowRunId,
                                     Long workflowStepRunId, String modelCode, String taskId, String orderNo,
                                     String key, String remark, Long relatedId) {
        PointLedger ledger = new PointLedger();
        ledger.setAccountId(account.getId());
        ledger.setWorkspaceId(account.getWorkspaceId());
        ledger.setLedgerType(type);
        ledger.setChangeAmount(availableChange);
        ledger.setBalanceBefore(availableBefore);
        ledger.setBalanceAfter(availableBefore + availableChange);
        ledger.setReservedChange(reservedChange);
        ledger.setReservedBefore(reservedBefore);
        ledger.setReservedAfter(reservedBefore + reservedChange);
        ledger.setOperatorUserId(operatorId);
        ledger.setMemberUserId(memberId);
        ledger.setProjectItemId(projectId);
        ledger.setWorkflowRunId(workflowRunId);
        ledger.setWorkflowStepRunId(workflowStepRunId);
        ledger.setModelCode(modelCode);
        ledger.setGenerationTaskId(taskId);
        ledger.setBizOrderNo(orderNo);
        ledger.setRelatedLedgerId(relatedId);
        ledger.setIdempotencyKey(key);
        ledger.setRemark(remark);
        ledger.setOccurredAt(LocalDateTime.now());
        ledgerMapper.insert(ledger);
        return ledger;
    }

    /**
     * 构造分页查询与汇总查询共用的流水筛选条件。
     */
    private LambdaQueryWrapper<PointLedger> ledgerQuery(Long accountId, Integer type, Long memberId,
                                                        LocalDateTime start, LocalDateTime end) {
        return new LambdaQueryWrapper<PointLedger>().eq(PointLedger::getAccountId, accountId)
                .eq(type != null, PointLedger::getLedgerType, type)
                .eq(memberId != null, PointLedger::getMemberUserId, memberId)
                .ge(start != null, PointLedger::getOccurredAt, start)
                .le(end != null, PointLedger::getOccurredAt, end)
                .orderByDesc(PointLedger::getOccurredAt);
    }

    /**
     * 校验积分数量必须为正数。
     */
    private void requirePositive(long amount) {
        if (amount <= 0) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE, "积分必须大于 0");
        }
    }

    /**
     * 生成不含连字符的随机业务编号片段。
     */
    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
