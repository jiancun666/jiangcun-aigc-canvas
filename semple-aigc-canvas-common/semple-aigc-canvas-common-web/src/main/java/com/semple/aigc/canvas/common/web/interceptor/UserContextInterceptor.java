package com.semple.aigc.canvas.common.web.interceptor;

import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.common.web.context.UserKit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户上下文拦截器。
 *
 * @author zengzhewen
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {

    private static final String USER_ID_HEADER = "userId";
    private static final String USERNAME_HEADER = "username";
    private static final String WORKSPACE_TYPE_HEADER = "workspaceType";

    /**
     * 请求进入 Controller 前解析请求头中的用户 ID。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param handler  处理器对象
     * @return 是否继续执行后续处理
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserKit.clear();
        String userId = request.getHeader(USER_ID_HEADER);
        if (StringUtils.hasText(userId)) {
            try {
                UserKit.setUserId(Long.valueOf(userId.trim()));
            } catch (NumberFormatException ex) {
                throw new BizException(ErrorCode.PARAM_TYPE_ERROR, "请求头 userId 必须是数字");
            }
        }
        String workspaceType = request.getHeader(WORKSPACE_TYPE_HEADER);
        if (StringUtils.hasText(workspaceType)) {
            UserKit.setWorkspaceType(workspaceType.trim());
        }
        String username = request.getHeader(USERNAME_HEADER);
        if (StringUtils.hasText(username)) {
            UserKit.setUsername(username.trim());
        }
        return true;
    }

    /**
     * 请求结束后清理用户上下文，避免线程复用时串用户。
     *
     * @param request  当前请求
     * @param response 当前响应
     * @param handler  处理器对象
     * @param ex       处理异常
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserKit.clear();
    }
}
