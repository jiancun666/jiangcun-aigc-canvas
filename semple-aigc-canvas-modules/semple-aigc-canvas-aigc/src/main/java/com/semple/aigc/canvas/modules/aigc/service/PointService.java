package com.semple.aigc.canvas.modules.aigc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.semple.aigc.canvas.api.aigc.domain.PointAccount;
import com.semple.aigc.canvas.api.aigc.domain.PointBizOrder;
import com.semple.aigc.canvas.api.aigc.domain.PointLedger;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 积分账户、预占、结算、释放和不可变流水服务。
 */
public interface PointService extends IService<PointAccount> {
    int NORMAL = 1;
    int GRANT = 1;
    int RESERVE = 2;
    int SETTLE = 3;
    int RELEASE = 4;
    int CLEAR = 5;
    int ORDER_RESERVED = 1;
    int ORDER_RUNNING = 2;
    int ORDER_SETTLED = 3;
    int ORDER_REFUNDED = 4;
    int ORDER_UNCERTAIN = 5;

    /**
     * 查询当前用户可读的工作区积分账户。
     */
    PointAccount account(Long workspaceId, Long userId);

    /**
     * 分页查询积分流水并计算筛选范围内的可用积分净变动。
     */
    LedgerPage ledgers(Long workspaceId, Integer ledgerType, Long memberUserId,
                       LocalDateTime start, LocalDateTime end, long pageNum, long pageSize, Long userId);

    /**
     * 幂等发放积分。
     */
    PointLedger grant(Long workspaceId, long amount, String idempotencyKey,
                      String remark, Long operatorId);

    /**
     * 使用服务端报价为生成任务预占积分。
     */
    PointBizOrder reserve(ReserveCommand command, Long userId);

    /**
     * 供应商调用开始后将积分订单标记为运行中。
     */
    PointBizOrder markRunning(String generationTaskId);

    /**
     * 按供应商实际用量结算，自动释放未使用的预占积分。
     */
    PointBizOrder settle(String generationTaskId, long actualPoints, String usageSnapshot);

    /**
     * 任务失败或取消时释放全部预占积分。
     */
    PointBizOrder refund(String generationTaskId, String failureCode, String failureMessage);

    /**
     * 工作区解散时清零积分；存在运行中预占时拒绝关闭。
     */
    PointLedger clearWorkspace(Long workspaceId, String idempotencyKey, Long operatorId);

    /**
     * 服务端可信报价转换成的积分预占命令。
     */
    record ReserveCommand(Long workspaceId, String generationTaskId,
                          Long projectItemId, Long workflowRunId, Long workflowStepRunId,
                          Long modelDefinitionId, Long priceRuleId, String modelCode,
                          Long reservedPoints, String priceSnapshot) {
    }

    /**
     * 流水分页结果以及筛选范围内可用积分变动汇总。
     */
    record LedgerPage(long total, long aggregate, List<PointLedger> records) {
    }
}
