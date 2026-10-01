package com.semple.aigc.canvas.modules.aigc.controller;

import com.semple.aigc.canvas.api.aigc.domain.Canvas;
import com.semple.aigc.canvas.api.aigc.domain.ProjectItem;
import com.semple.aigc.canvas.common.core.domain.R;
import com.semple.aigc.canvas.common.web.context.UserKit;
import com.semple.aigc.canvas.common.web.controller.BaseController;
import com.semple.aigc.canvas.modules.aigc.service.CanvasService;
import com.semple.aigc.canvas.modules.aigc.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目与文件夹 REST 接口。当前用户由网关认证后写入 {@link UserKit}。
 */
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
@Tag(name = "项目管理", description = "个人/团队空间下的项目、文件夹与回收站接口")
public class ProjectController extends BaseController {
    private final ProjectService projectService;
    private final CanvasService canvasService;

    /**
     * 查询指定个人/团队空间下的项目与文件夹，支持目录、关键字和回收站筛选。
     */
    @Operation(summary = "查询项目列表", description = "按空间、父目录、关键字及回收站状态查询项目和文件夹")
    @GetMapping
    public R<List<ProjectItem>> list(
            @Parameter(description = "当前工作区父文件夹 ID；省略表示根目录，查询回收站时忽略此参数", example = "1000")
            @RequestParam(required = false)
            Long parentId,
            @Parameter(description = "项目或文件夹名称的模糊搜索关键字；省略或空白表示不按名称筛选", example = "测试")
            @RequestParam(required = false)
            String keyword,
            @Parameter(description = "是否查询回收站：false 查询正常项目项，true 查询回收站项目项；默认 false", example = "false", schema = @Schema(defaultValue = "false"))
            @RequestParam(defaultValue = "false")
            boolean recycle) {
        return success(projectService.list(UserKit.requireWorkspaceId(), parentId, keyword, recycle,
                UserKit.requireUserId()));
    }

    /**
     * 查询项目或文件夹详情，并校验当前用户的空间读取权限。
     */
    @Operation(summary = "查询项目详情")
    @GetMapping("/{id}")
    public R<ProjectItem> detail(
            @Parameter(description = "项目或文件夹 ID；画布相关操作须传入项目 ID", example = "1000", required = true)
            @PathVariable
            Long id) {
        return success(projectService.detail(id, UserKit.requireUserId()));
    }

    /**
     * 创建文件夹或项目；创建项目时会在同一事务内自动创建默认画布。
     */
    @Operation(summary = "创建项目或文件夹", description = "创建项目时同步创建默认画布")
    @PostMapping
    public R<ProjectItem> create(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "创建项目或文件夹的类型、名称及可选父目录、封面", required = true)
            @RequestBody
            CreateRequest request) {
        return success(projectService.create(UserKit.requireWorkspaceId(), request.itemType(),
                request.parentId(), request.name(), request.coverUrl(), UserKit.requireUserId()));
    }

    /**
     * 修改项目或文件夹名称，名称按 Unicode 字符数限制为最多 10 个字符。
     */
    @Operation(summary = "重命名项目或文件夹")
    @PatchMapping("/{id}/name")
    public R<ProjectItem> rename(
            @Parameter(description = "项目或文件夹 ID；画布相关操作须传入项目 ID", example = "1000", required = true)
            @PathVariable
            Long id,
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "项目或文件夹的新名称", required = true)
            @RequestBody
            RenameRequest request) {
        return success(projectService.rename(id, request.name(), UserKit.requireUserId()));
    }

    /**
     * 更新项目或文件夹封面。
     */
    @Operation(summary = "更新项目或文件夹封面")
    @PatchMapping("/{id}/cover")
    public R<ProjectItem> cover(
            @Parameter(description = "项目或文件夹 ID；画布相关操作须传入项目 ID", example = "1000", required = true)
            @PathVariable
            Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "新封面地址；null 表示清除封面", required = true)
            @RequestBody
            CoverRequest request) {
        return success(projectService.updateCover(id, request.coverUrl(), UserKit.requireUserId()));
    }

    /**
     * 移动项目或文件夹，parentId 为空时移到空间根目录。
     */
    @Operation(summary = "移动项目或文件夹", description = "目标父目录为空时移到空间根目录")
    @PatchMapping("/{id}/parent")
    public R<ProjectItem> move(
            @Parameter(description = "项目或文件夹 ID；画布相关操作须传入项目 ID", example = "1000", required = true)
            @PathVariable
            Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "目标父文件夹；null 表示根目录", required = true)
            @RequestBody
            MoveRequest request) {
        return success(projectService.move(id, request.parentId(), UserKit.requireUserId()));
    }

    /**
     * 将项目或文件夹移入回收站，默认保留 30 天。
     */
    @Operation(summary = "移入回收站")
    @DeleteMapping("/{id}")
    public R<Void> recycle(
            @Parameter(description = "项目或文件夹 ID；画布相关操作须传入项目 ID", example = "1000", required = true)
            @PathVariable
            Long id) {
        projectService.recycle(id, UserKit.requireUserId());
        return success();
    }

    /**
     * 从回收站恢复；原目录失效时恢复到空间根目录。
     */
    @Operation(summary = "从回收站恢复", description = "原父目录失效时恢复到空间根目录")
    @PostMapping("/{id}/restore")
    public R<ProjectItem> restore(
            @Parameter(description = "项目或文件夹 ID；画布相关操作须传入项目 ID", example = "1000", required = true)
            @PathVariable
            Long id) {
        return success(projectService.restore(id, UserKit.requireUserId()));
    }

    @Operation(summary = "批量恢复回收站项目或文件夹")
    @PostMapping("/restore")
    public R<List<ProjectItem>> restoreBatch(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "需要恢复的项目或文件夹 ID 列表", required = true)
            @RequestBody
            RestoreBatchRequest request) {
        return success(projectService.restoreBatch(request.ids(), UserKit.requireUserId()));
    }

    /**
     * 查询项目中的有效画布。
     */
    @Operation(summary = "查询项目画布")
    @GetMapping("/{id}/canvases")
    public R<List<Canvas>> canvases(
            @Parameter(description = "项目或文件夹 ID；画布相关操作须传入项目 ID", example = "1000", required = true)
            @PathVariable
            Long id) {
        return success(canvasService.listByProject(id, UserKit.requireUserId()));
    }

    /**
     * 在项目中创建画布。
     */
    @Operation(summary = "创建项目画布")
    @PostMapping("/{id}/canvases")
    public R<Canvas> createCanvas(
            @Parameter(description = "项目或文件夹 ID；画布相关操作须传入项目 ID", example = "1000", required = true)
            @PathVariable
            Long id,
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "新画布的名称", required = true)
            @RequestBody
            CreateCanvasRequest request) {
        return success(canvasService.create(id, request.name(), UserKit.requireUserId()));
    }

    /**
     * 切换项目默认画布。
     */
    @Operation(summary = "切换默认画布")
    @PatchMapping("/{id}/default-canvas")
    public R<ProjectItem> defaultCanvas(
            @Parameter(description = "项目或文件夹 ID；画布相关操作须传入项目 ID", example = "1000", required = true)
            @PathVariable
            Long id,
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "项目下将设为默认画布的 ID", required = true)
            @RequestBody
            DefaultCanvasRequest request) {
        return success(projectService.setDefaultCanvas(id, request.canvasId(), UserKit.requireUserId()));
    }

    /**
     * 创建画布请求。
     */
    @Schema(description = "创建项目画布请求")
    public record CreateCanvasRequest(
            @Schema(description = "画布名称，非空，最多 40 个字符", example = "测试画布")
            @NotBlank @Size(max = 40) String name) {
    }

    /**
     * 切换默认画布请求。
     */
    @Schema(description = "切换默认画布请求")
    public record DefaultCanvasRequest(
            @Schema(description = "将设为默认画布的 ID，须为当前项目下的有效画布", example = "1000")
            @NotNull Long canvasId) {
    }

    /**
     * 创建项目项请求。itemType：1 文件夹，2 项目。
     */
    @Schema(description = "创建项目或文件夹请求")
    public record CreateRequest(
            @Schema(description = "项目项类型：1 文件夹，2 项目；创建项目时自动创建默认画布", allowableValues = {"1", "2"}, example = "2")
            @NotNull Integer itemType,
            @Schema(description = "当前工作区的父文件夹 ID；省略或 null 表示根目录", example = "1000")
            Long parentId,
            @Schema(description = "项目或文件夹名称，去除首尾空白后为 1–10 个 Unicode 字符", example = "模型链路测试")
            @NotBlank String name,
            @Schema(description = "可选封面 URL", example = "https://example.com/cover.png")
            String coverUrl) {
    }

    /**
     * 重命名请求。
     */
    @Schema(description = "项目或文件夹重命名请求")
    public record RenameRequest(
            @Schema(description = "新名称，去除首尾空白后为 1–10 个 Unicode 字符", example = "新项目名称")
            @NotBlank String name) {
    }

    /**
     * 封面更新请求，传空值表示清除封面。
     */
    @Schema(description = "项目封面更新请求")
    public record CoverRequest(
            @Schema(description = "新的封面 URL；传 null 表示清除封面", example = "https://example.com/cover.png")
            String coverUrl) {
    }

    /**
     * 移动请求，parentId 为空表示根目录。
     */
    @Schema(description = "项目或文件夹移动请求")
    public record MoveRequest(
            @Schema(description = "目标父文件夹 ID，须属于当前工作区；省略或 null 表示移到根目录", example = "1000")
            Long parentId) {
    }

    @Schema(description = "批量恢复请求，最多 100 项")
    public record RestoreBatchRequest(
            @Schema(description = "需要恢复的项目或文件夹 ID 列表，1–100 项，每项非 null", example = "[1000,1001]")
            @NotNull @Size(min = 1, max = 100) List<@NotNull Long> ids) {
    }
}
