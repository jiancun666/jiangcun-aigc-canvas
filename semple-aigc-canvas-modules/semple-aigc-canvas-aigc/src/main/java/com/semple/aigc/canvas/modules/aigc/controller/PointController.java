package com.semple.aigc.canvas.modules.aigc.controller;

import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.constant.SecurityConstants;
import com.semple.aigc.canvas.common.core.domain.R;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.common.web.context.UserKit;
import com.semple.aigc.canvas.common.web.controller.BaseController;
import com.semple.aigc.canvas.api.aigc.domain.PointAccount;
import com.semple.aigc.canvas.api.aigc.domain.PointBizOrder;
import com.semple.aigc.canvas.api.aigc.domain.PointLedger;
import com.semple.aigc.canvas.modules.aigc.service.PointService;
import com.semple.aigc.canvas.modules.aigc.service.PointService.LedgerPage;
import com.semple.aigc.canvas.modules.aigc.service.impl.PointDisplayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人/团队积分账户、流水与生成任务计费接口。
 */
@Tag(name = "积分管理", description = "个人/团队积分账户、流水与生成任务计费接口")
@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
public class PointController extends BaseController {
    private final PointService pointService;
    private final PointDisplayService pointDisplayService;
    @Value("${security.internal-token:89ec60de2636bd8ae6b345bfa6fad07ef502a006c9f685be69974b2aa88f1ac5}")
    private String internalToken;

    /**
     * 查询积分账户；账户尚未建立时返回余额为零的只读视图。
     */
    @Operation(summary = "查询积分账户", description = "账户尚未建立时返回余额为零的只读视图")
    @GetMapping("/account")
    public R<PointAccount> account() {
        return success(pointService.account(UserKit.requireWorkspaceId(), UserKit.requireUserId()));
    }

    /**
     * 分页查询积分流水，并返回同一筛选条件下的积分变动汇总。
     */
    @Operation(summary = "查询积分流水", description = "支持流水类型、团队成员、时间范围筛选，并返回净变动汇总")
    @GetMapping("/ledgers")
    public R<LedgerPage> ledgers(
            @Parameter(
                    description = "流水类型：1 发放，2 预占，3 结算，4 释放预占，5 清零，6 调整；省略查询全部",
                    example = "1",
                    schema = @Schema(allowableValues = {"1", "2", "3", "4", "5", "6"}))
            @RequestParam(required = false)
            Integer ledgerType,
            @Parameter(description = "按产生积分记录的成员用户 ID 筛选；省略表示不按成员筛选", example = "1")
            @RequestParam(required = false)
            Long memberUserId,
            @Parameter(description = "开始时间（包含边界），ISO 日期时间格式，无时区后缀；省略表示不限制开始时间", example = "2026-09-30T00:00:00")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime start,
            @Parameter(description = "结束时间（包含边界），ISO 日期时间格式，无时区后缀；省略表示不限制结束时间", example = "2026-09-30T23:59:59")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime end,
            @Parameter(description = "页码，从 1 开始，默认 1", example = "1", schema = @Schema(minimum = "1", defaultValue = "1"))
            @RequestParam(defaultValue = "1")
            long pageNum,
            @Parameter(description = "每页条数，默认 20，须大于 0；超过 100 按 100 查询", example = "20", schema = @Schema(minimum = "1", defaultValue = "20"))
            @RequestParam(defaultValue = "20")
            long pageSize) {
        return success(pointService.ledgers(UserKit.requireWorkspaceId(), ledgerType, memberUserId, start, end,
                pageNum, pageSize, UserKit.requireUserId()));
    }

    /**
     * 按蓝湖原型展示获取、实际消耗和返还记录；底层账务流水仍由 /ledgers 提供。
     */
    @Operation(summary = "查询积分展示明细", description = "category 为 ACQUIRED、CONSUMED 或 RETURNED")
    @GetMapping("/details")
    public R<PointDisplayService.DetailPage> details(
            @Parameter(
                    description = "积分明细分类：ACQUIRED 获取，CONSUMED 实际消耗，RETURNED 返还",
                    example = "CONSUMED",
                    schema = @Schema(allowableValues = {"ACQUIRED", "CONSUMED", "RETURNED"}))
            @RequestParam
            PointDisplayService.Category category,
            @Parameter(description = "按产生积分记录的成员用户 ID 筛选；省略表示不按成员筛选", example = "1")
            @RequestParam(required = false)
            Long memberUserId,
            @Parameter(description = "开始时间（包含边界），ISO 日期时间格式，无时区后缀；省略表示不限制开始时间", example = "2026-09-30T00:00:00")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime start,
            @Parameter(description = "结束时间（包含边界），ISO 日期时间格式，无时区后缀；省略表示不限制结束时间", example = "2026-09-30T23:59:59")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime end,
            @Parameter(description = "页码，从 1 开始，默认 1", example = "1", schema = @Schema(minimum = "1", defaultValue = "1"))
            @RequestParam(defaultValue = "1")
            long pageNum,
            @Parameter(description = "每页条数，默认 20，范围 1–100", example = "20", schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20")
            long pageSize) {
        return success(pointDisplayService.details(UserKit.requireWorkspaceId(), UserKit.requireUserId(),
                category, memberUserId, start, end, pageNum, pageSize));
    }

    /**
     * 内部接口：向个人或团队账户发放积分。
     */
    @Operation(summary = "内部发放积分", description = "需要 from-source=inner 和与服务配置匹配的 X-Internal-Token；JWT 不能代替内部令牌")
    @SecurityRequirements
    @Parameters({
            @Parameter(name = SecurityConstants.FROM_SOURCE, in = ParameterIn.HEADER, required = true,
                    description = "内部服务调用标识，固定填写 inner",
                    schema = @Schema(type = "string", allowableValues = {"inner"}, defaultValue = "inner")),
            @Parameter(name = SecurityConstants.INTERNAL_TOKEN_HEADER, in = ParameterIn.HEADER, required = true,
                    description = "服务端 INTERNAL_SERVICE_TOKEN 配置值", schema = @Schema(type = "string"))
    })
    @PostMapping("/internal/grants")
    public R<PointLedger> grant(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "积分发放目标工作区、数量、幂等键及备注", required = true)
            @RequestBody
            GrantRequest body,
            @Parameter(hidden = true)
            HttpServletRequest request) {
        assertInternal(request);
        return success(pointService.grant(body.workspaceId(), body.amount(), body.idempotencyKey(),
                body.remark(), UserKit.getUserId()));
    }

    /**
     * 内部接口：生成失败后原路返还该任务已扣积分。
     */
    @Operation(summary = "内部失败退款", description = "按生成任务原路返还积分；需要 from-source=inner 和与服务配置匹配的 X-Internal-Token")
    @SecurityRequirements
    @Parameters({
            @Parameter(name = SecurityConstants.FROM_SOURCE, in = ParameterIn.HEADER, required = true,
                    description = "内部服务调用标识，固定填写 inner",
                    schema = @Schema(type = "string", allowableValues = {"inner"}, defaultValue = "inner")),
            @Parameter(name = SecurityConstants.INTERNAL_TOKEN_HEADER, in = ParameterIn.HEADER, required = true,
                    description = "服务端 INTERNAL_SERVICE_TOKEN 配置值", schema = @Schema(type = "string"))
    })
    @PostMapping("/internal/charges/{taskId}/refund")
    public R<PointBizOrder> refund(
            @Parameter(description = "生成任务编号 taskNo，用于查找对应积分业务单；不是任务数据库 ID", example = "GT55679a4e11d44a25ac4880a2fa94c4d8", required = true)
            @PathVariable
            String taskId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "失败原因信息；退款目标由路径中的生成任务编号指定", required = true)
            @RequestBody
            RefundRequest body,
            @Parameter(hidden = true)
            HttpServletRequest request) {
        assertInternal(request);
        return success(pointService.refund(taskId, body.failureCode(), body.failureMessage()));
    }

    /**
     * 内部接口：团队解散时清零并关闭团队积分账户。
     */
    @Operation(summary = "内部清零工作区积分", description = "工作区解散时使用；需要 from-source=inner 和与服务配置匹配的 X-Internal-Token")
    @SecurityRequirements
    @Parameters({
            @Parameter(name = SecurityConstants.FROM_SOURCE, in = ParameterIn.HEADER, required = true,
                    description = "内部服务调用标识，固定填写 inner",
                    schema = @Schema(type = "string", allowableValues = {"inner"}, defaultValue = "inner")),
            @Parameter(name = SecurityConstants.INTERNAL_TOKEN_HEADER, in = ParameterIn.HEADER, required = true,
                    description = "服务端 INTERNAL_SERVICE_TOKEN 配置值", schema = @Schema(type = "string"))
    })
    @PostMapping("/internal/workspaces/{workspaceId}/clear")
    public R<PointLedger> clearWorkspace(
            @Parameter(description = "需要清零并关闭积分账户的个人或团队工作区 ID", example = "1", required = true)
            @PathVariable
            Long workspaceId,
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "清零操作的幂等键", required = true)
            @RequestBody
            ClearRequest body,
            @Parameter(hidden = true)
            HttpServletRequest request) {
        assertInternal(request);
        return success(pointService.clearWorkspace(workspaceId, body.idempotencyKey(), UserKit.getUserId()));
    }

    /**
     * 校验调用来自受信任的内部服务，外部请求不得直接发放、退款或清零积分。
     */
    private void assertInternal(HttpServletRequest request) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "内部服务令牌未配置，请设置 INTERNAL_SERVICE_TOKEN 或 security.internal-token");
        }
        String supplied = request.getHeader(SecurityConstants.INTERNAL_TOKEN_HEADER);
        boolean trusted = SecurityConstants.INNER.equals(request.getHeader(SecurityConstants.FROM_SOURCE))
                && internalToken != null && !internalToken.isBlank()
                && supplied != null && !supplied.isBlank()
                && MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
        if (!trusted) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "内部接口要求 from-source=inner，且 X-Internal-Token 与服务端配置一致");
        }
    }

    /**
     * 积分发放请求。idempotencyKey 应由调用方按业务动作稳定生成。
     */
    @Schema(description = "内部积分发放请求")
    public record GrantRequest(
            @Schema(description = "积分发放目标工作区 ID，可为个人或团队工作区", example = "1")
            @NotNull Long workspaceId,
            @Schema(description = "发放积分数量，必须为正整数", example = "100")
            @NotNull @Positive Long amount,
            @Schema(description = "业务幂等键，非空；同一工作区重复提交相同键不会重复发放", example = "test-grant-001")
            @NotBlank String idempotencyKey,
            @Schema(description = "可选的发放原因或备注", example = "模型链路测试积分")
            String remark) {
    }

    /**
     * 失败退款信息。
     */
    @Schema(description = "生成任务失败退款信息")
    public record RefundRequest(
            @Schema(description = "可选的失败原因编码，保存到退款记录", example = "PROVIDER_ERROR")
            String failureCode,
            @Schema(description = "可选的失败原因说明，保存到退款记录", example = "供应商调用失败")
            String failureMessage) {
    }

    /**
     * 团队积分清零请求。
     */
    @Schema(description = "团队积分清零请求")
    public record ClearRequest(
            @Schema(description = "本次清零操作的业务幂等键，非空；重复提交相同键不会重复清零", example = "workspace-clear-001")
            @NotBlank String idempotencyKey) {
    }
}
