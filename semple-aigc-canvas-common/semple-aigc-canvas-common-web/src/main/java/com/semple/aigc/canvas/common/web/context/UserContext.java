package com.semple.aigc.canvas.common.web.context;

/** 旧业务代码兼容层；新代码使用 UserKit。 */
public final class UserContext {

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        UserKit.setUserId(userId);
    }

    public static Long getUserId() {
        return UserKit.getUserId();
    }

    public static Long requireUserId() {
        return UserKit.requireUserId();
    }

    public static void setWorkspaceId(Long workspaceId) {
        UserKit.setWorkspaceId(workspaceId);
    }

    public static Long getWorkspaceId() {
        return UserKit.getWorkspaceId();
    }

    public static Long requireWorkspaceId() {
        return UserKit.requireWorkspaceId();
    }

    public static void setWorkspaceType(String workspaceType) {
        UserKit.setWorkspaceType(workspaceType);
    }

    public static String getWorkspaceType() {
        return UserKit.getWorkspaceType();
    }

    public static void clear() {
        UserKit.clear();
    }
}
