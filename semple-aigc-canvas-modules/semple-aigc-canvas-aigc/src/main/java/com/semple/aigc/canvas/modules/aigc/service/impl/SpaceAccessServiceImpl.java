package com.semple.aigc.canvas.modules.aigc.service.impl;

import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.api.aigc.domain.ProjectItem;
import com.semple.aigc.canvas.common.web.context.UserKit;
import com.semple.aigc.canvas.modules.aigc.service.SpaceAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 个人与团队空间权限适配器。
 * 工作区 ID 从认证上下文读取；workspaceRole 由受信任网关清除外部同名请求头后重新注入。
 */
@Service
public class SpaceAccessServiceImpl implements SpaceAccessService {
    /**
     * 校验当前登录上下文中的工作区 ID 与目标工作区一致。
     */
    @Override
    public void assertReadable(Long workspaceId, Long userId) {
        if (workspaceContextMatches(workspaceId)) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }

    /**
     * 校验写权限：团队负责人/管理员可写全部，普通成员仅可写自己创建的数据。
     */
    @Override
    public void assertWritable(ProjectItem item, Long userId) {
        assertReadable(item.getWorkspaceId(), userId);
        if (item.getCreatorUserId().equals(userId) || isWorkspaceManager()) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "团队成员只能操作自己创建的项目或文件夹");
    }

    /**
     * 判断登录上下文中的工作区 ID 是否与目标空间一致。
     */
    private boolean workspaceContextMatches(Long workspaceId) {
        return workspaceId != null && workspaceId.equals(UserKit.getWorkspaceId());
    }

    /**
     * 判断当前团队角色是否为负责人或管理员。
     */
    private boolean isWorkspaceManager() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return false;
        }
        String role = request.getHeader("workspaceRole");
        return "OWNER".equalsIgnoreCase(role) || "MANAGER".equalsIgnoreCase(role);
    }

    /**
     * 获取当前 HTTP 请求；非 Web 调用场景返回空。
     */
    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
