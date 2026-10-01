package com.semple.aigc.canvas.modules.aigc.controller;

import com.semple.aigc.canvas.common.core.domain.R;
import com.semple.aigc.canvas.common.web.context.UserKit;
import com.semple.aigc.canvas.common.web.controller.BaseController;
import com.semple.aigc.canvas.modules.aigc.service.CanvasService;
import com.semple.aigc.canvas.modules.aigc.service.CanvasService.CanvasDocument;
import com.semple.aigc.canvas.modules.aigc.service.CanvasService.SaveCanvasCommand;
import com.semple.aigc.canvas.modules.aigc.service.impl.CanvasWorkflowExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 画布读取与全量快照保存接口。
 */
@RestController
@RequestMapping("/canvases")
@RequiredArgsConstructor
@Tag(name = "画布管理", description = "画布、节点与连线的读取和快照保存接口")
public class CanvasController extends BaseController {
    private final CanvasService canvasService;
    private final CanvasWorkflowExecutionService workflowExecutionService;

    /**
     * 读取画布、节点和连线组成的完整画布文档。
     */
    @Operation(summary = "获取画布详情", description = "返回画布元数据、节点和连线组成的完整文档")
    @GetMapping("/{id}")
    public R<CanvasDocument> get(
            @Parameter(description = "画布 ID，从项目画布列表获取", example = "1000", required = true)
            @PathVariable
            Long id) {
        return success(canvasService.get(id, UserKit.requireUserId()));
    }

    /**
     * 保存完整画布快照；请求中的 revision 必须与服务端版本一致。
     */
    @Operation(summary = "保存画布快照", description = "按节点键差量更新并保持节点 ID 稳定，通过 revision 防止并发覆盖")
    @PutMapping("/{id}")
    public R<CanvasDocument> save(
            @Parameter(description = "画布 ID，从项目画布列表获取", example = "1000", required = true)
            @PathVariable
            Long id,
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "画布完整快照及当前版本号；省略节点或连线列表将移除已有内容", required = true)
            @RequestBody
            SaveCanvasCommand command) {
        return success(canvasService.save(id, command, UserKit.requireUserId()));
    }

    /**
     * 按依赖顺序执行目标节点及其上游节点。
     */
    @Operation(summary = "启动画布节点工作流")
    @PostMapping("/{id}/runs")
    public R<CanvasWorkflowExecutionService.ExecutionView> run(
            @Parameter(description = "画布 ID，从项目画布列表获取", example = "1000", required = true)
            @PathVariable
            Long id,
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "目标节点键；执行目标节点及其上游依赖", required = true)
            @RequestBody
            RunRequest request) {
        return success(workflowExecutionService.start(id, request.targetNodeKey(), UserKit.requireUserId()));
    }

    /**
     * 查询画布工作流及各节点执行状态。
     */
    @Operation(summary = "查询画布工作流")
    @GetMapping("/runs/{executionId}")
    public R<CanvasWorkflowExecutionService.ExecutionView> runDetail(
            @Parameter(description = "画布工作流执行 ID，从启动工作流响应的 execution.id 获取", example = "1000", required = true)
            @PathVariable
            Long executionId) {
        return success(workflowExecutionService.get(executionId, UserKit.requireUserId()));
    }

    /**
     * 删除非默认画布。
     */
    @Operation(summary = "删除画布")
    @DeleteMapping("/{id}")
    public R<Void> delete(
            @Parameter(description = "画布 ID，从项目画布列表获取", example = "1000", required = true)
            @PathVariable
            Long id) {
        canvasService.delete(id, UserKit.requireUserId());
        return success();
    }

    /**
     * 查询画布历史版本。
     */
    @Operation(summary = "查询画布版本列表")
    @GetMapping("/{id}/versions")
    public R<List<CanvasService.VersionInfo>> versions(
            @Parameter(description = "画布 ID，从项目画布列表获取", example = "1000", required = true)
            @PathVariable
            Long id) {
        return success(canvasService.versions(id, UserKit.requireUserId()));
    }

    /**
     * 预览指定版本的画布内容。
     */
    @Operation(summary = "预览画布版本")
    @GetMapping("/{id}/versions/{revision}")
    public R<SaveCanvasCommand> version(
            @Parameter(description = "画布 ID，从项目画布列表获取", example = "1000", required = true)
            @PathVariable
            Long id,
            @Parameter(description = "需要预览或恢复的历史版本号，从画布版本列表获取；不是当前版本号", example = "0", required = true)
            @PathVariable
            Integer revision) {
        return success(canvasService.version(id, revision, UserKit.requireUserId()));
    }

    /**
     * 将历史版本恢复为当前画布的新版本。
     */
    @Operation(summary = "恢复画布版本")
    @PostMapping("/{id}/versions/{revision}/restore")
    public R<CanvasDocument> restore(
            @Parameter(description = "画布 ID，从项目画布列表获取", example = "1000", required = true)
            @PathVariable
            Long id,
            @Parameter(description = "需要预览或恢复的历史版本号，从画布版本列表获取；不是当前版本号", example = "0", required = true)
            @PathVariable
            Integer revision,
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "当前画布版本号，用于防止恢复操作覆盖并发修改", required = true)
            @RequestBody
            RestoreRequest request) {
        return success(canvasService.restore(id, revision, request.expectedRevision(), UserKit.requireUserId()));
    }

    /**
     * 画布节点工作流启动请求。
     */
    @Schema(description = "画布节点工作流启动请求")
    public record RunRequest(
            @Schema(description = "目标节点的 nodeKey，非空且须存在于画布；执行时包含该节点的上游依赖，使用节点键而非数据库 ID", example = "qwen-test")
            @NotBlank String targetNodeKey) {
    }

    /**
     * 画布版本恢复请求。
     */
    @Schema(description = "画布版本恢复请求")
    public record RestoreRequest(
            @Schema(description = "当前画布版本号，从 GET /canvases/{id} 的 canvas.lockVersion 获取；须匹配当前版本以防覆盖并发修改", example = "1")
            @NotNull Integer expectedRevision) {
    }
}
