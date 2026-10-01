package com.semple.aigc.canvas.common.web.context;

import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;

/** 当前请求用户上下文工具；请求结束后必须清理线程变量。 */
public final class UserKit {

    private static final ThreadLocal<LoginContext> LOGIN_CONTEXT = new ThreadLocal<>();

    private UserKit() {
    }

    public static Long getUserId() {
        LoginContext context = LOGIN_CONTEXT.get();
        return context == null ? null : context.getUserId();
    }

    public static Long requireUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "当前请求没有已认证用户");
        }
        return userId;
    }

    public static String getUsername() {
        LoginContext context = LOGIN_CONTEXT.get();
        return context == null ? null : context.getUsername();
    }

    public static String getEmail() {
        LoginContext context = LOGIN_CONTEXT.get();
        return context == null ? null : context.getEmail();
    }

    public static Long getWorkspaceId() {
        LoginContext context = LOGIN_CONTEXT.get();
        return context == null ? null : context.getWorkspaceId();
    }

    public static Long requireWorkspaceId() {
        Long workspaceId = getWorkspaceId();
        if (workspaceId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "当前请求没有已选工作空间");
        }
        return workspaceId;
    }

    public static String getWorkspaceType() {
        LoginContext context = LOGIN_CONTEXT.get();
        return context == null ? null : context.getWorkspaceType();
    }

    public static LoginContext getLoginContext() {
        return LOGIN_CONTEXT.get();
    }

    public static void setLoginContext(LoginContext context) {
        if (context == null) {
            LOGIN_CONTEXT.remove();
        } else {
            LOGIN_CONTEXT.set(context);
        }
    }

    public static void setUserId(Long userId) {
        LoginContext context = currentOrNew();
        context.setUserId(userId);
        LOGIN_CONTEXT.set(context);
    }

    public static void setUsername(String username) {
        LoginContext context = currentOrNew();
        context.setUsername(username);
        LOGIN_CONTEXT.set(context);
    }

    public static void setWorkspaceId(Long workspaceId) {
        LoginContext context = currentOrNew();
        context.setWorkspaceId(workspaceId);
        LOGIN_CONTEXT.set(context);
    }

    public static void setWorkspaceType(String workspaceType) {
        LoginContext context = currentOrNew();
        context.setWorkspaceType(workspaceType);
        LOGIN_CONTEXT.set(context);
    }

    public static void clear() {
        LOGIN_CONTEXT.remove();
    }

    public static void remove() {
        clear();
    }

    private static LoginContext currentOrNew() {
        LoginContext context = LOGIN_CONTEXT.get();
        return context == null ? new LoginContext() : context;
    }
}
