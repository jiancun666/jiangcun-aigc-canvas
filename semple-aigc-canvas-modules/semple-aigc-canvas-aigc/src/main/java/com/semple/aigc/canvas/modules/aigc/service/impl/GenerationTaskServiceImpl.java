package com.semple.aigc.canvas.modules.aigc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semple.aigc.canvas.api.aigc.domain.Canvas;
import com.semple.aigc.canvas.api.aigc.domain.CanvasNode;
import com.semple.aigc.canvas.api.aigc.domain.GeneratedAsset;
import com.semple.aigc.canvas.api.aigc.domain.GenerationTask;
import com.semple.aigc.canvas.api.aigc.domain.ModelCallLog;
import com.semple.aigc.canvas.api.aigc.domain.ModelDefinition;
import com.semple.aigc.canvas.api.aigc.domain.ModelPriceRule;
import com.semple.aigc.canvas.api.aigc.domain.PointAccount;
import com.semple.aigc.canvas.api.aigc.domain.PointBizOrder;
import com.semple.aigc.canvas.api.aigc.domain.ProjectItem;
import com.semple.aigc.canvas.api.aigc.domain.WorkflowRun;
import com.semple.aigc.canvas.api.aigc.domain.WorkflowStepRun;
import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasNodeMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.GeneratedAssetMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.GenerationTaskMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.ModelCallLogMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.ModelPriceRuleMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.PointAccountMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.WorkflowRunMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.WorkflowStepRunMapper;
import com.semple.aigc.canvas.modules.aigc.provider.ModelProvider;
import com.semple.aigc.canvas.modules.aigc.provider.ModelProvider.GenerationRequest;
import com.semple.aigc.canvas.modules.aigc.provider.ModelProvider.ProviderAsset;
import com.semple.aigc.canvas.modules.aigc.provider.ModelProvider.ProviderResult;
import com.semple.aigc.canvas.modules.aigc.provider.ModelProvider.ProviderStatus;
import com.semple.aigc.canvas.modules.aigc.provider.ModelProviderRegistry;
import com.semple.aigc.canvas.modules.aigc.service.GenerationTaskService;
import com.semple.aigc.canvas.modules.aigc.service.ModelCatalogService;
import com.semple.aigc.canvas.modules.aigc.service.ModelCatalogService.ModelQuote;
import com.semple.aigc.canvas.modules.aigc.service.ModelPointCalculator;
import com.semple.aigc.canvas.modules.aigc.service.PointService;
import com.semple.aigc.canvas.modules.aigc.service.PointService.ReserveCommand;
import com.semple.aigc.canvas.modules.aigc.service.ProjectService;
import com.semple.aigc.canvas.modules.aigc.service.SpaceAccessService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 单节点模型生成任务服务，负责任务创建、供应商调用、状态流转、资产落库和积分结算。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GenerationTaskServiceImpl implements GenerationTaskService {
    private static final int QUEUED = 1;
    private static final int SUBMITTING = 2;
    private static final int PROCESSING = 3;
    private static final int SUCCEEDED = 4;
    private static final int FAILED = 5;
    private static final int CANCELLED = 6;

    private final GenerationTaskMapper taskMapper;
    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowStepRunMapper stepRunMapper;
    private final GeneratedAssetMapper generatedAssetMapper;
    private final ModelCallLogMapper callLogMapper;
    private final CanvasMapper canvasMapper;
    private final CanvasNodeMapper nodeMapper;
    private final PointAccountMapper pointAccountMapper;
    private final ModelPriceRuleMapper priceRuleMapper;
    private final ProjectService projectService;
    private final SpaceAccessService accessService;
    private final ModelCatalogService modelCatalogService;
    private final ModelPointCalculator pointCalculator;
    private final PointService pointService;
    private final ModelProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final GeneratedOutputService generatedOutputService;

    @Value("${semple-aigc-canvas.generation.worker.batch-size:20}")
    private int batchSize;
    @Value("${semple-aigc-canvas.generation.worker.lease-seconds:60}")
    private int leaseSeconds;
    @Value("${semple-aigc-canvas.generation.worker.poll-seconds:5}")
    private int pollSeconds;

    /**
     * 创建生成任务，并通过客户端请求号保证工作区内幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public TaskView create(CreateTaskCommand command, Long userId) {
        validateCreate(command);
        Long workspaceId = requireProjectWorkspace(command.projectItemId(), userId);
        // 同一个工作区和客户端请求号只能创建一个任务，重试请求直接返回已有结果。
        GenerationTask existing = taskMapper.selectOne(new LambdaQueryWrapper<GenerationTask>()
                .eq(GenerationTask::getWorkspaceId, workspaceId)
                .eq(GenerationTask::getClientRequestId, command.clientRequestId()));
        if (existing != null) {
            return view(existing);
        }
        return createInternal(command, workspaceId, userId, null, 1);
    }

    /**
     * 由画布工作流调度器创建任务，使用请求号防止重复预占积分。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public TaskView createFromWorkflow(CreateTaskCommand command, Long workspaceId, Long userId) {
        validateCreate(command);
        ProjectItem project = projectService.getById(command.projectItemId());
        if (project == null || !Integer.valueOf(1).equals(project.getItemStatus())
                || !workspaceId.equals(project.getWorkspaceId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流项目已失效");
        }
        GenerationTask existing = taskMapper.selectOne(new LambdaQueryWrapper<GenerationTask>()
                .eq(GenerationTask::getWorkspaceId, workspaceId)
                .eq(GenerationTask::getClientRequestId, command.clientRequestId()));
        return existing == null ? createInternal(command, workspaceId, userId, null, 3) : view(existing);
    }

    /**
     * 查询任务详情及其生成资产、供应商调用日志。
     */
    @Override
    public TaskView detail(String taskNo, Long userId) {
        GenerationTask task = requireTask(taskNo);
        accessService.assertReadable(task.getWorkspaceId(), userId);
        return view(task);
    }

    /**
     * 分页查询工作区生成任务，可按任务状态筛选。
     */
    @Override
    public Page<GenerationTask> list(Long workspaceId, Integer status, long pageNum, long pageSize, Long userId) {
        accessService.assertReadable(workspaceId, userId);
        if (pageNum < 1 || pageSize < 1) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE, "分页参数必须大于 0");
        }
        return taskMapper.selectPage(Page.of(pageNum, Math.min(pageSize, 100)),
                new LambdaQueryWrapper<GenerationTask>()
                        .eq(GenerationTask::getWorkspaceId, workspaceId)
                        .eq(status != null, GenerationTask::getTaskStatus, status)
                        .orderByDesc(GenerationTask::getCreateTime));
    }

    /**
     * 请求取消任务；已提交到供应商的任务会同时尝试执行原厂取消。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public TaskView cancel(String taskNo, Long userId) {
        GenerationTask task = requireTask(taskNo);
        accessService.assertReadable(task.getWorkspaceId(), userId);
        if (terminal(task.getTaskStatus())) {
            return view(task);
        }
        task.setCancelRequested(1);
        taskMapper.updateById(task);
        if (task.getProviderRequestId() != null) {
            ModelDefinition model = modelCatalogService.requireModel(task.getModelDefinitionId());
            LocalDateTime started = LocalDateTime.now();
            ProviderResult result = cancelProvider(task, model);
            writeCallLog(task, model, 3, started, result);
        }
        finishCancelled(task, "USER_CANCELLED", "用户取消生成任务");
        return view(taskMapper.selectById(task.getId()));
    }

    /**
     * 基于失败或已取消任务的快照创建一个新的重试任务。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public TaskView retry(String taskNo, String clientRequestId, Long userId) {
        GenerationTask original = requireTask(taskNo);
        accessService.assertReadable(original.getWorkspaceId(), userId);
        if (original.getTaskStatus() != FAILED && original.getTaskStatus() != CANCELLED) {
            throw new BizException(ErrorCode.CONFLICT, "只有失败或已取消任务可以重试");
        }
        JsonNode config = readTree(original.getRequestConfig());
        int imageCount = config != null && config.has("imageCount") ? config.get("imageCount").asInt(1) : 1;
        CreateTaskCommand command = new CreateTaskCommand(clientRequestId, original.getProjectItemId(),
                original.getCanvasId(), original.getCanvasNodeId(), original.getModelDefinitionId(),
                original.getPrompt(), imageCount, config);
        validateCreate(command);
        return createInternal(command, original.getWorkspaceId(), userId, original.getId(), 2);
    }

    /**
     * 批量领取到期任务并驱动一次提交或轮询。
     */
    @Override
    public void runDueTasks() {
        String owner = "worker-" + UUID.randomUUID();
        for (GenerationTask candidate : taskMapper.selectRunnable(Math.max(1, batchSize))) {
            // 数据库租约保证多实例部署时同一任务在一个周期内只被一个工作线程处理。
            if (taskMapper.claim(candidate.getId(), owner, Math.max(10, leaseSeconds)) == 1) {
                try {
                    processClaimed(taskMapper.selectById(candidate.getId()));
                } catch (Exception e) {
                    // 当前租约到期后任务会重新进入候选集，单个任务异常不会阻塞整批任务。
                    log.error("Failed to process generation task id={}", candidate.getId(), e);
                }
            }
        }
    }

    /**
     * 创建工作流、步骤、生成任务并预占积分；调用方事务保证这些记录原子落库。
     */
    private TaskView createInternal(CreateTaskCommand command, Long workspaceId, Long userId,
                                    Long retryOfTaskId, int triggerType) {
        Canvas canvas = canvasMapper.selectById(command.canvasId());
        if (canvas == null || canvas.getCanvasStatus() != 1
                || !canvas.getProjectItemId().equals(command.projectItemId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "画布不存在或不属于指定项目");
        }
        CanvasNode node = nodeMapper.selectById(command.canvasNodeId());
        if (node == null || node.getNodeStatus() != 1 || !node.getCanvasId().equals(canvas.getId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "有效画布节点不存在");
        }
        ModelQuote quote = modelCatalogService.quote(command.modelDefinitionId(), command.imageCount(), command.prompt());
        if (node.getNodeType() == null || quote.model().getModelType() == null
                || !node.getNodeType().equals(quote.model().getModelType())) {
            throw new BizException(ErrorCode.CONFLICT, "画布节点类型与模型类型不一致");
        }
        PointAccount pointAccount = pointAccountMapper.selectOne(new LambdaQueryWrapper<PointAccount>()
                .eq(PointAccount::getWorkspaceId, workspaceId));
        if (pointAccount == null) {
            throw new BizException(ErrorCode.POINTS_INSUFFICIENT);
        }
        String runNo = "WR" + compactUuid();
        // 即使当前仅执行一个节点，也保留工作流和步骤快照，便于后续扩展多步骤编排。
        WorkflowRun run = new WorkflowRun();
        run.setRunNo(runNo);
        run.setProjectItemId(command.projectItemId());
        run.setCanvasId(command.canvasId());
        run.setTriggerUserId(userId);
        run.setPointAccountId(pointAccount.getId());
        run.setTriggerType(triggerType);
        run.setRunStatus(1);
        run.setWorkflowSnapshot(json(Map.of("canvasNodeId", command.canvasNodeId(),
                "modelDefinitionId", command.modelDefinitionId(), "prompt", command.prompt())));
        run.setEstimatedPoints(quote.points());
        run.setConsumedPoints(0L);
        run.setRefundedPoints(0L);
        run.setTotalSteps(1);
        run.setSuccessSteps(0);
        run.setFailedSteps(0);
        workflowRunMapper.insert(run);

        WorkflowStepRun step = new WorkflowStepRun();
        step.setStepNo("WS" + compactUuid());
        step.setWorkflowRunId(run.getId());
        step.setCanvasNodeId(node.getId());
        step.setNodeKey(node.getNodeKey());
        step.setNodeType(node.getNodeType());
        step.setSequenceNo(1);
        step.setAttemptNo(1);
        step.setStepStatus(1);
        step.setModelDefinitionId(quote.model().getId());
        step.setProviderCode(quote.model().getProviderCode());
        step.setModelCode(quote.model().getModelCode());
        step.setModelName(quote.model().getModelName());
        step.setInputSnapshot(json(Map.of("prompt", command.prompt())));
        step.setRequestPayload(safeRequestJson(command));
        step.setPriceRuleId(quote.priceRule().getId());
        step.setPriceSnapshot(quote.priceSnapshot());
        step.setEstimatedPoints(quote.points());
        step.setConsumedPoints(0L);
        step.setRefundedPoints(0L);
        step.setQueuedAt(LocalDateTime.now());
        stepRunMapper.insert(step);

        GenerationTask task = new GenerationTask();
        task.setTaskNo("GT" + compactUuid());
        task.setClientRequestId(command.clientRequestId());
        task.setWorkspaceId(workspaceId);
        task.setCreatorUserId(userId);
        task.setProjectItemId(command.projectItemId());
        task.setCanvasId(command.canvasId());
        task.setCanvasNodeId(command.canvasNodeId());
        task.setWorkflowRunId(run.getId());
        task.setWorkflowStepRunId(step.getId());
        task.setModelDefinitionId(quote.model().getId());
        task.setPriceRuleId(quote.priceRule().getId());
        task.setRetryOfTaskId(retryOfTaskId);
        task.setTaskStatus(QUEUED);
        task.setProgress(0);
        task.setAttemptNo(0);
        task.setPrompt(command.prompt());
        task.setRequestConfig(requestConfig(command));
        task.setCancelRequested(0);
        task.setNextPollAt(LocalDateTime.now());
        task.setExpiresAt(LocalDateTime.now().plusSeconds(quote.model().getTimeoutSeconds()));
        taskMapper.insert(task);

        // 任务记录创建后立即预占报价积分；预占失败会触发整个创建事务回滚。
        PointBizOrder order = pointService.reserve(new ReserveCommand(workspaceId, task.getTaskNo(),
                command.projectItemId(), run.getId(), step.getId(), quote.model().getId(),
                quote.priceRule().getId(), quote.model().getModelCode(), quote.points(), quote.priceSnapshot()), userId);
        task.setPointBizOrderId(order.getId());
        taskMapper.updateById(task);
        step.setPointBizOrderId(order.getId());
        stepRunMapper.updateById(step);
        return view(task);
    }

    /**
     * 处理一个已获得租约的任务，根据当前状态执行首次提交或后续轮询。
     */
    private void processClaimed(GenerationTask task) {
        if (task == null || terminal(task.getTaskStatus())) {
            return;
        }
        if (task.getCancelRequested() == 1) {
            transactionTemplate.executeWithoutResult(status ->
                    finishCancelled(task, "USER_CANCELLED", "用户取消生成任务"));
            return;
        }
        if (LocalDateTime.now().isAfter(task.getExpiresAt())) {
            if (task.getProviderRequestId() != null) {
                ModelDefinition expiredModel = modelCatalogService.requireModel(task.getModelDefinitionId());
                LocalDateTime cancelStarted = LocalDateTime.now();
                ProviderResult cancelResult = cancelProvider(task, expiredModel);
                transactionTemplate.executeWithoutResult(status ->
                        writeCallLog(task, expiredModel, 3, cancelStarted, cancelResult));
            }
            transactionTemplate.executeWithoutResult(status ->
                    finishFailed(task, "TASK_TIMEOUT", "生成任务执行超时"));
            return;
        }
        ModelDefinition model = modelCatalogService.requireModel(task.getModelDefinitionId());
        ModelProvider provider = providerRegistry.require(model.getAdapterCode());
        int action = task.getTaskStatus() == SUBMITTING ? 1 : 2;
        LocalDateTime started = LocalDateTime.now();
        // 供应商网络调用不包在数据库长事务内，避免远程延迟长期占用数据库连接和行锁。
        ProviderResult result;
        if (action == 1) {
            pointService.markRunning(task.getTaskNo());
            JsonNode config = readTree(task.getRequestConfig());
            int imageCount = config != null && config.has("imageCount") ? config.get("imageCount").asInt(1) : 1;
            result = provider.submit(model, new GenerationRequest(task.getPrompt(), imageCount, config));
        } else {
            result = provider.poll(model, task.getProviderRequestId());
        }
        // 异步提交成功后先持久化供应商任务号，后续日志或业务更新失败也只会重新轮询，不会重复提交。
        if (action == 1 && result.status() == ProviderStatus.PROCESSING
                && result.providerRequestId() != null) {
            task.setProviderRequestId(result.providerRequestId());
            task.setTaskStatus(PROCESSING);
            task.setNextPollAt(LocalDateTime.now().plusSeconds(Math.max(1, pollSeconds)));
            releaseLease(task);
            taskMapper.updateById(task);
        }
        transactionTemplate.executeWithoutResult(status -> {
            writeCallLog(task, model, action, started, result);
            applyProviderResult(task, model, result);
        });
    }

    /**
     * 将标准化供应商结果映射为任务、步骤、资产和积分状态。
     */
    @Transactional(rollbackFor = Exception.class)
    protected void applyProviderResult(GenerationTask task, ModelDefinition model, ProviderResult result) {
        GenerationTask current = taskMapper.selectForUpdate(task.getId());
        if (current == null || terminal(current.getTaskStatus())
                || (task.getLockOwner() != null && current.getLockOwner() != null
                && !task.getLockOwner().equals(current.getLockOwner()))) {
            return;
        }
        task = current;
        if (result.providerRequestId() != null) {
            task.setProviderRequestId(result.providerRequestId());
        }
        if (result.status() == ProviderStatus.PROCESSING) {
            // 异步任务保留供应商任务号并释放租约，等待 nextPollAt 再次领取。
            task.setTaskStatus(PROCESSING);
            task.setProgress(Math.max(task.getProgress(), 20));
            task.setNextPollAt(LocalDateTime.now().plusSeconds(Math.max(1, pollSeconds)));
            releaseLease(task);
            taskMapper.updateById(task);
            WorkflowStepRun step = stepRunMapper.selectById(task.getWorkflowStepRunId());
            step.setStepStatus(2);
            step.setProviderRequestId(task.getProviderRequestId());
            step.setStartedAt(task.getStartedAt());
            stepRunMapper.updateById(step);
            return;
        }
        if (result.status() == ProviderStatus.SUCCEEDED) {
            if (result.assets().isEmpty()) {
                finishFailed(task, "EMPTY_PROVIDER_RESULT", "供应商成功响应中没有生成结果");
                return;
            }
            // 资产落库和积分按实际结果结算处于同一事务，任一步失败都会整体回滚。
            List<GeneratedAsset> assets = saveAssets(task, result.assets(), model);
            int actualCount = number(result.usage().get("outputCount"),
                    number(result.usage().get("imageCount"), assets.size()));
            long actualPoints = actualPoints(task.getPriceRuleId(), actualCount, result);
            PointBizOrder order = pointService.settle(task.getTaskNo(), actualPoints, json(result.usage()));
            generatedOutputService.publish(task, assets);
            task.setTaskStatus(SUCCEEDED);
            task.setProgress(100);
            task.setResultPayload(json(assets));
            task.setFinishedAt(LocalDateTime.now());
            releaseLease(task);
            taskMapper.updateById(task);
            finishWorkflow(task, 3, 3, actualPoints, Math.max(0, order.getCostPoints() - actualPoints),
                    null, null, result);
            return;
        }
        if (result.status() == ProviderStatus.CANCELLED) {
            finishCancelled(task, first(result.errorCode(), "PROVIDER_CANCELLED"), result.errorMessage());
        } else {
            finishFailed(task, first(result.errorCode(), "PROVIDER_FAILED"), result.errorMessage());
        }
    }

    /**
     * 将任务标记为失败，并释放全部预占积分。
     */
    @Transactional(rollbackFor = Exception.class)
    protected void finishFailed(GenerationTask task, String code, String message) {
        pointService.refund(task.getTaskNo(), code, message);
        task.setTaskStatus(FAILED);
        task.setProgress(100);
        task.setErrorCode(code);
        task.setErrorMessage(message);
        task.setFinishedAt(LocalDateTime.now());
        releaseLease(task);
        taskMapper.updateById(task);
        finishWorkflow(task, 5, 4, 0, reservedPoints(task), code, message, null);
    }

    /**
     * 将任务标记为已取消，并释放全部预占积分。
     */
    @Transactional(rollbackFor = Exception.class)
    protected void finishCancelled(GenerationTask task, String code, String message) {
        pointService.refund(task.getTaskNo(), code, message);
        task.setTaskStatus(CANCELLED);
        task.setProgress(100);
        task.setErrorCode(code);
        task.setErrorMessage(message);
        task.setFinishedAt(LocalDateTime.now());
        releaseLease(task);
        taskMapper.updateById(task);
        finishWorkflow(task, 6, 5, 0, reservedPoints(task), code, message, null);
    }

    /**
     * 先保存可追溯的生成历史，再将媒体登记到素材目录。
     */
    private List<GeneratedAsset> saveAssets(GenerationTask task, List<ProviderAsset> providerAssets,
                                            ModelDefinition model) {
        int index = 0;
        java.util.ArrayList<GeneratedAsset> saved = new java.util.ArrayList<>();
        WorkflowStepRun step = stepRunMapper.selectById(task.getWorkflowStepRunId());
        if (step == null || step.getNodeType() == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "生成步骤类型快照不存在");
        }
        int assetType = assetTypeForNode(step.getNodeType());
        for (ProviderAsset providerAsset : providerAssets) {
            index++;
            GeneratedAsset asset = new GeneratedAsset();
            asset.setAssetNo("GA" + task.getWorkflowStepRunId() + "-" + index);
            asset.setAssetType(assetType);
            asset.setAssetSource(1);
            asset.setWorkspaceId(task.getWorkspaceId());
            asset.setProjectItemId(task.getProjectItemId());
            asset.setCanvasId(task.getCanvasId());
            asset.setCanvasNodeId(task.getCanvasNodeId());
            asset.setWorkflowRunId(task.getWorkflowRunId());
            asset.setStepRunId(task.getWorkflowStepRunId());
            asset.setOutputIndex(index);
            asset.setCreatorUserId(task.getCreatorUserId());
            asset.setModelDefinitionId(task.getModelDefinitionId());
            asset.setStorageUrl(providerAsset.url());
            asset.setTextContent(providerAsset.textContent());
            asset.setMimeType(first(providerAsset.mimeType(), defaultMimeType(assetType)));
            asset.setFileSize(0L);
            asset.setWidth(providerAsset.width());
            asset.setHeight(providerAsset.height());
            asset.setDurationMs(providerAsset.durationMs());
            asset.setPromptSnapshot(task.getPrompt());
            asset.setGenerationConfig(task.getRequestConfig());
            asset.setAssetMetadata(json(Map.of("providerCode", model.getProviderCode(),
                    "modelCode", model.getModelCode())));
            asset.setAssetStatus(1);
            generatedAssetMapper.insertIgnore(asset);
            GeneratedAsset persisted = generatedAssetMapper.selectOne(new LambdaQueryWrapper<GeneratedAsset>()
                    .eq(GeneratedAsset::getStepRunId, asset.getStepRunId())
                    .eq(GeneratedAsset::getOutputIndex, asset.getOutputIndex()));
            if (persisted == null) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "生成结果写入失败");
            }
            asset.setId(persisted.getId());
            asset.setAssetNo(persisted.getAssetNo());
            asset.setAssetId(persisted.getAssetId());
            generatedAssetMapper.updateById(asset);
            saved.add(asset);
        }
        return saved;
    }

    /**
     * 历史类型使用创建任务时保存的步骤类型快照，不从 URL 后缀或供应商参数推断。
     */
    private int assetTypeForNode(Integer nodeType) {
        return switch (nodeType) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            default -> throw new BizException(ErrorCode.CONFLICT, "当前节点类型不支持生成历史");
        };
    }
    private void finishWorkflow(GenerationTask task, int runStatus, int stepStatus, long consumed,
                                long refunded, String errorCode, String errorMessage,
                                ProviderResult result) {
        LocalDateTime now = LocalDateTime.now();
        WorkflowRun run = workflowRunMapper.selectById(task.getWorkflowRunId());
        run.setRunStatus(runStatus);
        run.setConsumedPoints(consumed);
        run.setRefundedPoints(refunded);
        run.setSuccessSteps(runStatus == 3 ? 1 : 0);
        run.setFailedSteps(runStatus == 5 ? 1 : 0);
        run.setStartedAt(task.getStartedAt());
        run.setFinishedAt(now);
        workflowRunMapper.updateById(run);

        WorkflowStepRun step = stepRunMapper.selectById(task.getWorkflowStepRunId());
        step.setStepStatus(stepStatus);
        step.setProviderRequestId(task.getProviderRequestId());
        step.setResponsePayload(result == null ? null : result.rawPayload());
        step.setOutputSnapshot(task.getResultPayload());
        step.setConsumedPoints(consumed);
        step.setRefundedPoints(refunded);
        step.setErrorCode(errorCode);
        step.setErrorMessage(errorMessage);
        step.setStartedAt(task.getStartedAt());
        step.setFinishedAt(now);
        if (task.getStartedAt() != null) {
            step.setDurationMs(Duration.between(task.getStartedAt(), now).toMillis());
        }
        stepRunMapper.updateById(step);
    }

    /**
     * 记录一次提交、轮询或取消调用，供问题定位和用量审计使用。
     */
    private void writeCallLog(GenerationTask task, ModelDefinition model, int action,
                              LocalDateTime started, ProviderResult result) {
        ModelCallLog log = new ModelCallLog();
        log.setGenerationTaskId(task.getId());
        log.setAttemptNo(task.getAttemptNo());
        log.setActionType(action);
        log.setProviderCode(model.getProviderCode());
        log.setModelCode(model.getModelCode());
        log.setProviderRequestId(result.providerRequestId());
        log.setCallStatus(result.status() == ProviderStatus.FAILED ? 2 : 1);
        log.setRequestPayload(action == 1 ? json(Map.of("prompt", task.getPrompt(),
                "config", task.getRequestConfig()))
                : json(Map.of("providerRequestId", String.valueOf(task.getProviderRequestId()))));
        log.setResponsePayload(result.rawPayload());
        log.setUsageSnapshot(json(result.usage()));
        log.setErrorCode(result.errorCode());
        log.setErrorMessage(result.errorMessage());
        log.setStartedAt(started);
        log.setFinishedAt(LocalDateTime.now());
        log.setDurationMs(Duration.between(started, log.getFinishedAt()).toMillis());
        callLogMapper.insert(log);
    }

    /**
     * 调用供应商取消接口并转换成可审计的统一结果。
     */
    private ProviderResult cancelProvider(GenerationTask task, ModelDefinition model) {
        try {
            providerRegistry.require(model.getAdapterCode()).cancel(model, task.getProviderRequestId());
            return new ProviderResult(ProviderStatus.CANCELLED, task.getProviderRequestId(),
                    List.of(), Map.of(), null, null, null);
        } catch (Exception e) {
            return new ProviderResult(ProviderStatus.FAILED, task.getProviderRequestId(),
                    List.of(), Map.of(), null, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * 清除任务租约字段，使任务可在下一调度周期重新领取。
     */
    private void releaseLease(GenerationTask task) {
        task.setLockOwner(null);
        task.setLeaseUntil(null);
    }

    /**
     * 根据价格规则和供应商实际用量计算最终消耗积分。
     */
    private long actualPoints(Long priceRuleId, int outputCount, ProviderResult result) {
        ModelPriceRule rule = priceRuleMapper.selectById(priceRuleId);
        if (rule == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "任务价格快照对应的规则不存在");
        }
        long durationMs = numberLong(result.usage().get("durationMs"), 0L);
        if (durationMs <= 0) {
            durationMs = result.assets().stream()
                    .map(ProviderAsset::durationMs)
                    .filter(java.util.Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0L);
        }
        Map<String, Object> actualUsage = new LinkedHashMap<>(result.usage());
        if (durationMs > 0) {
            // 视频供应商常把时长放在资产而非 usage 中，统一补入计费用量后再结算。
            actualUsage.putIfAbsent("durationMs", durationMs);
        }
        return pointCalculator.actual(rule, outputCount, actualUsage, durationMs);
    }

    /**
     * 返回各资产类型在供应商未提供格式时使用的默认 MIME 类型。
     */
    private String defaultMimeType(int assetType) {
        return switch (assetType) {
            case 1 -> "text/plain";
            case 2 -> "image/*";
            case 3 -> "video/*";
            case 4 -> "audio/*";
            default -> "application/octet-stream";
        };
    }

    /**
     * 从工作流步骤快照读取任务最初预占的积分。
     */
    private long reservedPoints(GenerationTask task) {
        WorkflowStepRun step = stepRunMapper.selectById(task.getWorkflowStepRunId());
        return step == null || step.getEstimatedPoints() == null ? 0 : step.getEstimatedPoints();
    }

    /**
     * 校验项目访问权限并返回其所属工作区。
     */
    private Long requireProjectWorkspace(Long projectId, Long userId) {
        ProjectItem project = projectService.detail(projectId, userId);
        return project.getWorkspaceId();
    }

    /**
     * 按业务任务号查询任务，不存在时抛出统一业务异常。
     */
    private GenerationTask requireTask(String taskNo) {
        GenerationTask task = taskMapper.selectOne(new LambdaQueryWrapper<GenerationTask>()
                .eq(GenerationTask::getTaskNo, taskNo));
        if (task == null) {
            throw new BizException(ErrorCode.JOB_NOT_FOUND, "生成任务不存在");
        }
        return task;
    }

    /**
     * 聚合任务、生成资产和调用日志为接口视图。
     */
    private TaskView view(GenerationTask task) {
        List<GeneratedAsset> assets = generatedAssetMapper.selectList(new LambdaQueryWrapper<GeneratedAsset>()
                .eq(GeneratedAsset::getStepRunId, task.getWorkflowStepRunId())
                .orderByAsc(GeneratedAsset::getId));
        List<ModelCallLog> logs = callLogMapper.selectList(new LambdaQueryWrapper<ModelCallLog>()
                .eq(ModelCallLog::getGenerationTaskId, task.getId())
                .orderByAsc(ModelCallLog::getId));
        return new TaskView(task, assets, logs);
    }

    /**
     * 校验创建任务所需字段及长度、数量边界。
     */
    private void validateCreate(CreateTaskCommand command) {
        if (command == null || blank(command.clientRequestId()) || command.clientRequestId().length() > 128
                || command.projectItemId() == null || command.canvasId() == null
                || command.canvasNodeId() == null || command.modelDefinitionId() == null
                || blank(command.prompt()) || command.prompt().length() > 10000
                || command.imageCount() == null || command.imageCount() < 1 || command.imageCount() > 16) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模型生成任务参数无效");
        }
    }

    /**
     * 判断任务状态是否已经进入不可继续处理的终态。
     */
    private boolean terminal(Integer status) {
        return status != null && status >= SUCCEEDED;
    }

    /**
     * 判断字符串是否为空或仅包含空白字符。
     */
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 将用量字段安全转换为整数，类型不匹配时使用默认值。
     */
    private int number(Object value, int defaultValue) {
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    /**
     * 将用量字段安全转换为长整数，类型不匹配时使用默认值。
     */
    private long numberLong(Object value, long defaultValue) {
        return value instanceof Number number ? number.longValue() : defaultValue;
    }

    /**
     * 将输出数量和模型参数序列化为任务请求配置快照。
     */
    private String requestConfig(CreateTaskCommand command) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("imageCount", command.imageCount());
        data.put("config", command.config());
        return json(data);
    }

    /**
     * 构造不含供应商密钥的步骤请求审计快照。
     */
    private String safeRequestJson(CreateTaskCommand command) {
        return json(Map.of("prompt", command.prompt(), "imageCount", command.imageCount(),
                "config", command.config() == null ? objectMapper.createObjectNode() : command.config()));
    }

    /**
     * 解析任务配置，并把外层输出数量合并回供应商可见配置。
     */
    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            // 数据库存储为 {imageCount, config}，适配器接收扁平配置，因此读取时进行合并。
            return root.has("config") && root.get("config").isObject()
                    ? ((com.fasterxml.jackson.databind.node.ObjectNode) root.get("config")).set("imageCount", root.get("imageCount"))
                    : root;
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "任务配置 JSON 损坏");
        }
    }

    /**
     * 将业务快照序列化为 JSON，失败时转换为统一内部错误。
     */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "业务快照序列化失败");
        }
    }

    /**
     * 生成不含连字符的随机业务编号片段。
     */
    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 返回首个非空字符串，否则使用兜底值。
     */
    private String first(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }
}
