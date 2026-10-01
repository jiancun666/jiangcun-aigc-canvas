package com.semple.aigc.canvas.modules.aigc.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.semple.aigc.canvas.api.aigc.domain.TeamMember;
import com.semple.aigc.canvas.api.aigc.domain.UserAccount;
import com.semple.aigc.canvas.api.aigc.domain.Workspace;
import com.semple.aigc.canvas.api.aigc.mapper.TeamMemberMapper;
import com.semple.aigc.canvas.api.aigc.mapper.UserAccountMapper;
import com.semple.aigc.canvas.api.aigc.mapper.WorkspaceMapper;
import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.constant.SecurityConstants;
import com.semple.aigc.canvas.common.core.domain.R;
import com.semple.aigc.canvas.common.core.utils.JsonUtils;
import com.semple.aigc.canvas.common.security.JwtUtils;
import com.semple.aigc.canvas.common.security.JwtValidateStatus;
import com.semple.aigc.canvas.common.web.context.LoginContext;
import com.semple.aigc.canvas.common.web.context.UserKit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/** 校验JWT及其当前空间权限，并将登录信息写入UserKit。 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final UserAccountMapper userAccountMapper;
    private final WorkspaceMapper workspaceMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final String internalToken;

    public LoginInterceptor(UserAccountMapper userAccountMapper, WorkspaceMapper workspaceMapper,
                            TeamMemberMapper teamMemberMapper,
                            @Value("${security.internal-token:}") String internalToken) {
        this.userAccountMapper = userAccountMapper;
        this.workspaceMapper = workspaceMapper;
        this.teamMemberMapper = teamMemberMapper;
        this.internalToken = internalToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (handler instanceof ResourceHttpRequestHandler) {
            return true;
        }

        UserKit.clear();
        // 内部 Feign 请求继续使用项目既有的内部调用标记校验。
        if (isTrustedInternal(request)
                && request.getRequestURI().startsWith("/points/internal/")) {
            return true;
        }

        String authorization = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            writeError(response, ErrorCode.UNAUTHORIZED);
            return false;
        }

        String token = authorization.substring(SecurityConstants.TOKEN_PREFIX.length());
        JwtValidateStatus tokenStatus = JwtUtils.validateTokenStatus(token);
        if (tokenStatus == JwtValidateStatus.EXPIRED_WITHIN_WINDOW) {
            writeError(response, ErrorCode.TOKEN_AUTH_EXPIRED);
            return false;
        }
        if (tokenStatus != JwtValidateStatus.VALID) {
            writeError(response, ErrorCode.UNAUTHORIZED);
            return false;
        }

        try {
            Long userId = JwtUtils.getUserIdFromToken(token);
            String username = JwtUtils.getUsernameFromToken(token);
            Long workspaceId = JwtUtils.getWorkspaceIdFromToken(token);
            String workspaceType = JwtUtils.getWorkspaceTypeFromToken(token);
            if (userId == null || workspaceId == null || !StringUtils.hasText(username)
                    || !StringUtils.hasText(workspaceType)) {
                writeError(response, ErrorCode.UNAUTHORIZED);
                return false;
            }

            UserAccount user = userAccountMapper.selectById(userId);
            if (user == null || !"ACTIVE".equals(user.getStatus())) {
                writeError(response, ErrorCode.ACCOUNT_DISABLED);
                return false;
            }

            ErrorCode authorizationError = validateWorkspace(userId, workspaceId, workspaceType);
            if (authorizationError != null) {
                writeError(response, authorizationError);
                return false;
            }

            UserKit.setLoginContext(new LoginContext(userId, username, user.getEmail(), workspaceId, workspaceType));
            return true;
        } catch (Exception exception) {
            UserKit.clear();
            writeError(response, ErrorCode.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        UserKit.clear();
    }

    private ErrorCode validateWorkspace(Long userId, Long workspaceId, String workspaceType) {
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null || !"ACTIVE".equals(workspace.getStatus())
                || !Objects.equals(workspace.getWorkspaceType(), workspaceType)) {
            return ErrorCode.FORBIDDEN;
        }

        if ("PERSONAL".equals(workspaceType)) {
            return Objects.equals(workspace.getOwnerUserId(), userId) ? null : ErrorCode.FORBIDDEN;
        }
        if (!"TEAM".equals(workspaceType)) {
            return ErrorCode.FORBIDDEN;
        }

        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamWorkspaceId, workspaceId)
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getMemberStatus, "ACTIVE"));
        return member == null ? ErrorCode.FORBIDDEN : null;
    }

    /** 内部标记和部署时配置的共享令牌必须同时匹配。 */
    private boolean isTrustedInternal(HttpServletRequest request) {
        String supplied = request.getHeader(SecurityConstants.INTERNAL_TOKEN_HEADER);
        return SecurityConstants.INNER.equals(request.getHeader(SecurityConstants.FROM_SOURCE))
                && StringUtils.hasText(internalToken) && StringUtils.hasText(supplied)
                && MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode == ErrorCode.FORBIDDEN
                ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JsonUtils.toJson(R.fail(errorCode.getCode(), errorCode.getMessage())));
    }
}
