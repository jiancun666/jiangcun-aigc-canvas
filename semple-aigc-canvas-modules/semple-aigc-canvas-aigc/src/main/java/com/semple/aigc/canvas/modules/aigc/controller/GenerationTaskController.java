package com.semple.aigc.canvas.modules.aigc.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.semple.aigc.canvas.api.aigc.domain.GenerationTask;
import com.semple.aigc.canvas.common.core.domain.R;
import com.semple.aigc.canvas.common.web.context.UserKit;
import com.semple.aigc.canvas.common.web.controller.BaseController;
import com.semple.aigc.canvas.modules.aigc.service.GenerationTaskService;
import com.semple.aigc.canvas.modules.aigc.service.GenerationTaskService.CreateTaskCommand;
import com.semple.aigc.canvas.modules.aigc.service.GenerationTaskService.TaskProgress;
import com.semple.aigc.canvas.modules.aigc.service.GenerationTaskService.TaskView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文本、图片、视频与音频生成任务 REST 接口。
 */
@RestController
@RequestMapping("/generation/tasks")
@RequiredArgsConstructor
@Tag(name = "模型生成任务", description = "创建、查询、取消、重试及进度查询")
public class GenerationTaskController extends BaseController {
    private final GenerationTaskService generationTaskService;

    /**
     * 创建任务，价格由服务端模型价格规则计算并立即预占积分。
     */
    @Operation(summary = "创建模型生成任务", description = "同一工作区 clientRequestId 重复提交不会重复扣费；imageCount 暂作为兼容字段表示期望输出数量")
    @PostMapping
    public R<TaskView> create(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "创建生成任务所需的项目、画布、节点、模型及提示词；服务端计算并预占积分", required = true)
            @RequestBody
            CreateTaskRequest request) {
        CreateTaskCommand command = new CreateTaskCommand(request.clientRequestId(), request.projectItemId(),
                request.canvasId(), request.canvasNodeId(), request.modelDefinitionId(), request.prompt(),
                request.imageCount(), request.config());
        return success(generationTaskService.create(command, UserKit.requireUserId()));
    }

    /**
     * 查询任务以及关联的生成资产、调用日志。
     */
    @Operation(summary = "查询模型生成任务")
    @GetMapping("/{taskNo}")
    public R<TaskView> detail(
            @Parameter(description = "生成任务编号，从创建任务或重试响应的 task.taskNo 获取；不是数据库 ID", example = "GT55679a4e11d44a25ac4880a2fa94c4d8", required = true)
            @PathVariable
            String taskNo) {
        return success(generationTaskService.detail(taskNo, UserKit.requireUserId()));
    }

    /**
     * 分页查询当前工作区的生成任务。
     */
    @Operation(summary = "分页查询模型生成任务")
    @GetMapping
    public R<Page<GenerationTask>> list(
            @Parameter(
                    description = "任务状态：1 等待，2 提交中，3 供应商处理中，4 成功，5 失败，6 取消；省略查询全部",
                    example = "4",
                    schema = @Schema(allowableValues = {"1", "2", "3", "4", "5", "6"}))
            @RequestParam(required = false)
            Integer status,
            @Parameter(description = "页码，从 1 开始，默认 1", example = "1", schema = @Schema(minimum = "1", defaultValue = "1"))
            @RequestParam(defaultValue = "1")
            long pageNum,
            @Parameter(description = "每页条数，默认 20，须大于 0；超过 100 按 100 查询", example = "20", schema = @Schema(minimum = "1", defaultValue = "20"))
            @RequestParam(defaultValue = "20")
            long pageSize) {
        return success(generationTaskService.list(UserKit.requireWorkspaceId(), status, pageNum, pageSize,
                UserKit.requireUserId()));
    }

    /**
     * 查询轻量进度，适合前端轮询。
     */
    @Operation(summary = "查询任务进度")
    @GetMapping("/{taskNo}/progress")
    public R<TaskProgress> progress(
            @Parameter(description = "生成任务编号，从创建任务或重试响应的 task.taskNo 获取；不是数据库 ID", example = "GT55679a4e11d44a25ac4880a2fa94c4d8", required = true)
            @PathVariable
            String taskNo) {
        GenerationTask task = generationTaskService.detail(taskNo, UserKit.requireUserId()).task();
        return success(new TaskProgress(task.getTaskNo(), task.getTaskStatus(), task.getProgress(),
                task.getErrorCode(), task.getErrorMessage()));
    }

    /**
     * 取消等待中或执行中的任务并释放预占积分。
     */
    @Operation(summary = "取消模型生成任务")
    @PostMapping("/{taskNo}/cancel")
    public R<TaskView> cancel(
            @Parameter(description = "生成任务编号，从创建任务或重试响应的 task.taskNo 获取；不是数据库 ID", example = "GT55679a4e11d44a25ac4880a2fa94c4d8", required = true)
            @PathVariable
            String taskNo) {
        return success(generationTaskService.cancel(taskNo, UserKit.requireUserId()));
    }

    /**
     * 使用新的幂等请求号重试失败或已取消任务。
     */
    @Operation(summary = "重试模型生成任务")
    @PostMapping("/{taskNo}/retry")
    public R<TaskView> retry(
            @Parameter(description = "生成任务编号，从创建任务或重试响应的 task.taskNo 获取；不是数据库 ID", example = "GT55679a4e11d44a25ac4880a2fa94c4d8", required = true)
            @PathVariable
            String taskNo,
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "失败或取消任务的重试参数；使用新的客户端请求号", required = true)
            @RequestBody
            RetryTaskRequest request) {
        return success(generationTaskService.retry(taskNo, request.clientRequestId(), UserKit.requireUserId()));
    }

    /**
     * 模型生成任务请求。
     */
    @Schema(description = "模型生成任务请求；积分价格由服务端计算")
    public record CreateTaskRequest(
            @Schema(description = "客户端幂等请求号，同一工作区重复提交返回原任务；每次新测试使用新值，长度 1–128", example = "qwen-test-001")
            @NotBlank @Size(max = 128) String clientRequestId,
            @Schema(description = "任务所属的有效项目 ID，必须为当前用户有权限的项目", example = "1000")
            @NotNull Long projectItemId,
            @Schema(description = "项目下的有效画布 ID，须属于 projectItemId", example = "1000")
            @NotNull Long canvasId,
            @Schema(description = "画布内的有效节点数据库 ID，须属于 canvasId；节点类型须与模型类型一致", example = "1000")
            @NotNull Long canvasNodeId,
            @Schema(description = "启用的模型定义 ID，从 GET /models 返回的 id 获取", example = "1000")
            @NotNull Long modelDefinitionId,
            @Schema(description = "发送给模型的提示词或输入文本，非空，最多 10000 个字符", example = "请用一句中文介绍你自己。")
            @NotBlank @Size(max = 10000) String prompt,
            @Schema(description = "期望输出数量，范围 1–16；文本、视频、音频也沿用 imageCount 字段，文本测试填 1", example = "1")
            @NotNull @Min(1) @Max(16) Integer imageCount,
            @Schema(description = "可选的模型参数 JSON 对象，与模型默认参数合并；可包含 max_tokens、systemPrompt、referenceImages 等，具体键由适配器决定",
                    type = "object", example = "{\"max_tokens\":128}")
            JsonNode config) {
    }

    /**
     * 任务重试请求。
     */
    @Schema(description = "任务重试请求")
    public record RetryTaskRequest(
            @Schema(description = "本次重试的新客户端请求号，长度 1–128；请与原任务及之前测试使用的值区分", example = "qwen-test-002")
            @NotBlank @Size(max = 128) String clientRequestId) {
    }
}
