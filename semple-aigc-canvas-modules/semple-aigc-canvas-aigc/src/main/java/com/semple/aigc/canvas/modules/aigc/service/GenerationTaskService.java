package com.semple.aigc.canvas.modules.aigc.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.semple.aigc.canvas.api.aigc.domain.GeneratedAsset;
import com.semple.aigc.canvas.api.aigc.domain.GenerationTask;
import com.semple.aigc.canvas.api.aigc.domain.ModelCallLog;

import java.util.List;

/**
 * 单节点模型生成任务的创建、查询、取消、重试和 worker 执行服务。
 */
public interface GenerationTaskService {
    /**
     * 创建单节点模型生成任务并预占积分。
     */
    TaskView create(CreateTaskCommand command, Long userId);

    /**
     * 从画布工作流创建幂等的生成任务。
     */
    TaskView createFromWorkflow(CreateTaskCommand command, Long workspaceId, Long userId);

    /**
     * 查询任务、生成资产和模型调用日志。
     */
    TaskView detail(String taskNo, Long userId);

    /**
     * 分页查询当前工作区任务。
     */
    Page<GenerationTask> list(Long workspaceId, Integer status, long pageNum, long pageSize, Long userId);

    /**
     * 请求取消任务并释放预占积分。
     */
    TaskView cancel(String taskNo, Long userId);

    /**
     * 从失败或取消任务复制参数并创建新任务。
     */
    TaskView retry(String taskNo, String clientRequestId, Long userId);

    /**
     * worker 扫描并处理一批到期任务。
     */
    void runDueTasks();

    /**
     * 创建模型任务命令，imageCount 为兼容字段，统一表示期望输出数量。
     */
    record CreateTaskCommand(String clientRequestId, Long projectItemId, Long canvasId,
                             Long canvasNodeId, Long modelDefinitionId, String prompt,
                             Integer imageCount, JsonNode config) {
    }

    /**
     * 任务聚合视图。
     */
    record TaskView(GenerationTask task, List<GeneratedAsset> assets, List<ModelCallLog> callLogs) {
    }

    /**
     * 轻量任务进度。
     */
    record TaskProgress(String taskNo, Integer status, Integer progress,
                        String errorCode, String errorMessage) {
    }
}
