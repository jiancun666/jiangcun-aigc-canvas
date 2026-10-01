package com.semple.aigc.canvas.modules.aigc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.semple.aigc.canvas.api.aigc.domain.CanvasNode;
import com.semple.aigc.canvas.api.aigc.domain.CanvasWorkflowExecution;
import com.semple.aigc.canvas.api.aigc.domain.CanvasWorkflowExecutionStep;
import com.semple.aigc.canvas.api.aigc.domain.GeneratedAsset;
import com.semple.aigc.canvas.api.aigc.domain.GenerationTask;
import com.semple.aigc.canvas.api.aigc.domain.ProjectItem;
import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasWorkflowExecutionMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.CanvasWorkflowExecutionStepMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.GeneratedAssetMapper;
import com.semple.aigc.canvas.modules.aigc.mapper.GenerationTaskMapper;
import com.semple.aigc.canvas.modules.aigc.service.CanvasService;
import com.semple.aigc.canvas.modules.aigc.service.CanvasWorkflowPlanner;
import com.semple.aigc.canvas.modules.aigc.service.GenerationTaskService;
import com.semple.aigc.canvas.modules.aigc.service.ModelCatalogService;
import com.semple.aigc.canvas.modules.aigc.service.ProjectService;
import com.semple.aigc.canvas.modules.aigc.service.SpaceAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 根据画布连线调度节点，并复用现有生成任务完成积分预占与结算。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CanvasWorkflowExecutionService {
    private final CanvasService canvasService;
    private final ProjectService projectService;
    private final SpaceAccessService accessService;
    private final ModelCatalogService modelCatalogService;
    private final GenerationTaskService generationTaskService;
    private final CanvasWorkflowExecutionMapper executionMapper;
    private final CanvasWorkflowExecutionStepMapper stepMapper;
    private final GenerationTaskMapper taskMapper;
    private final GeneratedAssetMapper generatedAssetMapper;
    private final ObjectMapper objectMapper;

    /**
     * 画布执行记录及其步骤状态。
     */
    public record ExecutionView(CanvasWorkflowExecution execution, List<CanvasWorkflowExecutionStep> steps) {
    }

    /**
     * 冻结目标节点及上游节点配置，创建待调度的执行记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public ExecutionView start(Long canvasId, String targetNodeKey, Long userId) {
        CanvasService.CanvasDocument document = canvasService.get(canvasId, userId);
        ProjectItem project = projectService.writableDetail(document.canvas().getProjectItemId(), userId);
        if (!Integer.valueOf(2).equals(project.getItemType()) || !Integer.valueOf(1).equals(project.getItemStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "项目不可执行");
        }
        List<CanvasWorkflowPlanner.PlannedNode> plan = CanvasWorkflowPlanner.plan(
                document.nodes(), document.edges(), targetNodeKey);
        for (CanvasWorkflowPlanner.PlannedNode planned : plan) {
            CanvasNode node = planned.node();
            if (node.getNodeType() == null || node.getNodeType() == 7) {
                throw new BizException(ErrorCode.PARAM_ERROR, "工作流包含不可执行节点");
            }
            if (node.getNodeType() <= 4) {
                String prompt = prompt(node);
                if (node.getModelDefinitionId() == null || prompt == null || prompt.isBlank()) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "生成节点缺少模型或提示词");
                }
                JsonNode config = parse(node.getModelConfig());
                int count = config.path("imageCount").asInt(1);
                if (!Integer.valueOf(node.getNodeType()).equals(
                        modelCatalogService.quote(node.getModelDefinitionId(), count, prompt).model().getModelType())) {
                    throw new BizException(ErrorCode.CONFLICT, "节点模型类型不匹配");
                }
            }
        }
        CanvasWorkflowExecution execution = new CanvasWorkflowExecution();
        execution.setWorkspaceId(project.getWorkspaceId());
        execution.setProjectItemId(project.getId());
        execution.setCanvasId(canvasId);
        execution.setCreatorUserId(userId);
        execution.setCanvasRevision(document.canvas().getLockVersion());
        execution.setTargetNodeKey(targetNodeKey);
        execution.setExecutionStatus(1);
        executionMapper.insert(execution);
        int sequence = 0;
        for (CanvasWorkflowPlanner.PlannedNode planned : plan) {
            CanvasNode node = planned.node();
            CanvasWorkflowExecutionStep step = new CanvasWorkflowExecutionStep();
            step.setExecutionId(execution.getId());
            step.setCanvasNodeId(node.getId());
            step.setNodeKey(node.getNodeKey());
            step.setNodeType(node.getNodeType());
            step.setSequenceNo(++sequence);
            step.setModelDefinitionId(node.getModelDefinitionId());
            step.setPrompt(prompt(node));
            step.setModelConfig(node.getModelConfig());
            step.setDependencies(json(planned.dependencies()));
            step.setStepStatus(1);
            if (node.getNodeType() >= 5) {
                step.setOutputSnapshot(sourceSnapshot(node));
            }
            stepMapper.insert(step);
        }
        return view(execution);
    }

    /**
     * 查询画布执行状态，并校验工作区读取权限。
     */
    public ExecutionView get(Long executionId, Long userId) {
        CanvasWorkflowExecution execution = executionMapper.selectById(executionId);
        if (execution == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "画布运行不存在");
        }
        accessService.assertReadable(execution.getWorkspaceId(), userId);
        return view(execution);
    }

    /**
     * 定时推进处于运行中的画布执行。
     */
    @Scheduled(fixedDelayString = "${semple-aigc-canvas.generation.workflow-poll-ms:3000}")
    public void runDue() {
        List<CanvasWorkflowExecution> active = executionMapper.selectList(
                new LambdaQueryWrapper<CanvasWorkflowExecution>()
                        .eq(CanvasWorkflowExecution::getExecutionStatus, 1)
                        .orderByAsc(CanvasWorkflowExecution::getCreateTime));
        for (CanvasWorkflowExecution execution : active) {
            try {
                advance(execution);
            } catch (Exception error) {
                log.error("Failed to advance canvas execution id={}", execution.getId(), error);
            }
        }
    }

    private void advance(CanvasWorkflowExecution execution) {
        List<CanvasWorkflowExecutionStep> steps = steps(execution.getId());
        Map<String, CanvasWorkflowExecutionStep> byKey = new HashMap<>();
        steps.forEach(step -> byKey.put(step.getNodeKey(), step));
        for (CanvasWorkflowExecutionStep step : steps) {
            if (step.getStepStatus() == 2) {
                GenerationTask task = taskMapper.selectOne(new LambdaQueryWrapper<GenerationTask>()
                        .eq(GenerationTask::getTaskNo, step.getTaskNo()));
                if (task == null) {
                    continue;
                }
                if (task.getTaskStatus() == 4) {
                    List<GeneratedAsset> outputs = generatedAssetMapper.selectList(
                            new LambdaQueryWrapper<GeneratedAsset>()
                                    .eq(GeneratedAsset::getStepRunId, task.getWorkflowStepRunId())
                                    .orderByAsc(GeneratedAsset::getOutputIndex));
                    step.setOutputSnapshot(json(outputs));
                    step.setStepStatus(3);
                    stepMapper.updateById(step);
                } else if (task.getTaskStatus() == 5 || task.getTaskStatus() == 6) {
                    step.setStepStatus(4);
                    step.setErrorMessage(task.getErrorMessage());
                    stepMapper.updateById(step);
                }
            }
            if (step.getStepStatus() != 1) {
                continue;
            }
            List<String> dependencies = dependencyKeys(step);
            if (dependencies.stream().map(byKey::get)
                    .anyMatch(parent -> parent == null || parent.getStepStatus() == 4 || parent.getStepStatus() == 5)) {
                step.setStepStatus(5);
                step.setErrorMessage("上游步骤失败");
                stepMapper.updateById(step);
                continue;
            }
            if (dependencies.stream().map(byKey::get).anyMatch(parent -> parent.getStepStatus() != 3)) {
                continue;
            }
            if (step.getNodeType() >= 5) {
                step.setStepStatus(3);
                stepMapper.updateById(step);
                continue;
            }
            try {
                ObjectNode config = configWithReferences(step, byKey);
                String fullPrompt = promptWithReferences(step, byKey);
                GenerationTaskService.TaskView task = generationTaskService.createFromWorkflow(
                        new GenerationTaskService.CreateTaskCommand(
                                "canvas-workflow:" + execution.getId() + ":" + step.getId(),
                                execution.getProjectItemId(), execution.getCanvasId(),
                                step.getCanvasNodeId(), step.getModelDefinitionId(), fullPrompt,
                                config.path("imageCount").asInt(1), config),
                        execution.getWorkspaceId(), execution.getCreatorUserId());
                step.setTaskNo(task.task().getTaskNo());
                step.setStepStatus(2);
                stepMapper.updateById(step);
            } catch (BizException error) {
                // 积分不足时等待接口发放后重试，不把整个工作流立即标记为失败。
                if (error.getCode() == ErrorCode.POINTS_INSUFFICIENT.getCode()) {
                    continue;
                }
                step.setStepStatus(4);
                step.setErrorMessage(error.getMessage());
                stepMapper.updateById(step);
            }
        }
        if (steps.stream().allMatch(step -> step.getStepStatus() >= 3)) {
            long succeeded = steps.stream().filter(step -> step.getStepStatus() == 3).count();
            int status = succeeded == steps.size() ? 2 : succeeded > 0 ? 3 : 4;
            execution.setExecutionStatus(status);
            executionMapper.updateById(execution);
        }
    }

    /**
     * 把上游媒体结果转换为模型请求中的参考素材参数。
     */
    private ObjectNode configWithReferences(CanvasWorkflowExecutionStep step,
                                            Map<String, CanvasWorkflowExecutionStep> byKey) {
        JsonNode base = parse(step.getModelConfig());
        ObjectNode config = base instanceof ObjectNode object ? object.deepCopy() : objectMapper.createObjectNode();
        ArrayNode images = config.withArray("referenceImages");
        for (String key : dependencyKeys(step)) {
            for (JsonNode output : parse(byKey.get(key).getOutputSnapshot())) {
                String url = output.path("storageUrl").asText(output.path("url").asText(""));
                int type = output.path("assetType").asInt(2);
                if (url.isBlank()) {
                    continue;
                }
                if (type == 3) {
                    config.put("referenceVideo", url);
                } else if (type == 4) {
                    config.put("referenceAudio", url);
                } else {
                    images.add(url);
                }
            }
        }
        return config;
    }

    /**
     * 将上游文本结果追加到当前节点提示词。
     */
    private String promptWithReferences(CanvasWorkflowExecutionStep step,
                                        Map<String, CanvasWorkflowExecutionStep> byKey) {
        StringBuilder prompt = new StringBuilder(step.getPrompt());
        for (String key : dependencyKeys(step)) {
            for (JsonNode output : parse(byKey.get(key).getOutputSnapshot())) {
                String text = output.path("textContent").asText(output.path("text").asText(""));
                if (!text.isBlank()) {
                    prompt.append("\n\n上游节点 ").append(key).append(" 的结果：\n").append(text);
                }
            }
        }
        if (prompt.length() > 10000) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE, "组合提示词超过10000字");
        }
        return prompt.toString();
    }

    /**
     * 提取上传或历史素材节点的资源快照。
     */
    private String sourceSnapshot(CanvasNode node) {
        JsonNode data = parse(node.getNodeData());
        String url = data.path("url").asText(data.path("storageUrl").asText(data.path("assetUrl").asText("")));
        String text = data.path("text").asText("");
        if (url.isBlank() && text.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "上游素材节点缺少资源地址或文本");
        }
        ObjectNode output = objectMapper.createObjectNode();
        if (!url.isBlank()) {
            output.put("storageUrl", url);
        }
        if (!text.isBlank()) {
            output.put("textContent", text);
        }
        String mime = data.path("mimeType").asText("");
        String explicitType = data.path("assetType").asText("");
        int assetType = mime.startsWith("video/") || "VIDEO".equalsIgnoreCase(explicitType) ? 3
                : mime.startsWith("audio/") || "AUDIO".equalsIgnoreCase(explicitType) ? 4 : 2;
        output.put("assetType", assetType);
        ArrayNode array = objectMapper.createArrayNode();
        array.add(output);
        return array.toString();
    }

    private String prompt(CanvasNode node) {
        JsonNode input = parse(node.getInputConfig());
        String prompt = input.path("prompt").asText("");
        if (prompt.isBlank()) {
            prompt = parse(node.getNodeData()).path("prompt").asText("");
        }
        return prompt;
    }

    private List<String> dependencyKeys(CanvasWorkflowExecutionStep step) {
        List<String> keys = new ArrayList<>();
        for (JsonNode node : parse(step.getDependencies())) {
            keys.add(node.asText());
        }
        return keys;
    }

    private JsonNode parse(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception error) {
            throw new BizException(ErrorCode.PARAM_ERROR, "画布节点配置不是有效 JSON");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "工作流快照序列化失败");
        }
    }

    private List<CanvasWorkflowExecutionStep> steps(Long executionId) {
        return stepMapper.selectList(new LambdaQueryWrapper<CanvasWorkflowExecutionStep>()
                .eq(CanvasWorkflowExecutionStep::getExecutionId, executionId)
                .orderByAsc(CanvasWorkflowExecutionStep::getSequenceNo));
    }

    private ExecutionView view(CanvasWorkflowExecution execution) {
        return new ExecutionView(execution, steps(execution.getId()));
    }
}
