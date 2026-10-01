package com.semple.aigc.canvas.modules.aigc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.semple.aigc.canvas.api.aigc.domain.TeamMember;
import com.semple.aigc.canvas.api.aigc.domain.UserAccount;
import com.semple.aigc.canvas.api.aigc.domain.Workspace;
import com.semple.aigc.canvas.api.aigc.mapper.TeamMemberMapper;
import com.semple.aigc.canvas.api.aigc.mapper.UserAccountMapper;
import com.semple.aigc.canvas.api.aigc.mapper.WorkspaceMapper;
import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.common.security.JwtUtils;
import com.semple.aigc.canvas.common.security.config.JwtProperties;
import com.semple.aigc.canvas.modules.aigc.mapper.PointAccountMapper;
import com.semple.aigc.canvas.modules.aigc.service.AuthService;
import com.semple.aigc.canvas.modules.aigc.service.EmailCaptchaService;
import com.semple.aigc.canvas.modules.aigc.dto.EmailLoginRequest;
import com.semple.aigc.canvas.modules.aigc.dto.EmailLoginResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.security.SecureRandom;

/** 邮箱验证码登录、首次注册及工作空间初始化。 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final String USERNAME_SUFFIX_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailCaptchaService emailCaptchaService;
    private final UserAccountMapper userAccountMapper;
    private final WorkspaceMapper workspaceMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final PointAccountMapper pointAccountMapper;
    private final JwtProperties jwtProperties;

    public AuthServiceImpl(EmailCaptchaService emailCaptchaService, UserAccountMapper userAccountMapper,
                           WorkspaceMapper workspaceMapper, TeamMemberMapper teamMemberMapper,
                           PointAccountMapper pointAccountMapper, JwtProperties jwtProperties) {
        this.emailCaptchaService = emailCaptchaService;
        this.userAccountMapper = userAccountMapper;
        this.workspaceMapper = workspaceMapper;
        this.teamMemberMapper = teamMemberMapper;
        this.pointAccountMapper = pointAccountMapper;
        this.jwtProperties = jwtProperties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmailLoginResponse emailLogin(EmailLoginRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!emailCaptchaService.verifyCaptcha(email, request.getCaptcha())) {
            throw new BizException(ErrorCode.CAPTCHA_ERROR);
        }

        UserAccount user = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getEmail, email));
        if (user == null) {
            user = registerUser(email);
        } else if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        Workspace personalWorkspace = getOrCreatePersonalWorkspace(user);
        Workspace currentWorkspace = resolveCurrentWorkspace(user, personalWorkspace);
        if (!currentWorkspace.getId().equals(user.getLastWorkspaceId())) {
            user.setLastWorkspaceId(currentWorkspace.getId());
            userAccountMapper.updateById(user);
        }

        String token = JwtUtils.generateToken(user.getId(), user.getUsername(),
                currentWorkspace.getId(), currentWorkspace.getWorkspaceType());
        EmailLoginResponse response = new EmailLoginResponse();
        response.setToken(token);
        response.setTokenType("Bearer");
        response.setExpireAt(LocalDateTime.now().plusMinutes(jwtProperties.getExpirationMinutes()));
        response.setWorkspaceId(currentWorkspace.getId());
        response.setWorkspaceType(currentWorkspace.getWorkspaceType());
        response.setWorkspaceName(currentWorkspace.getName());
        return response;
    }

    private UserAccount registerUser(String email) {
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setUsername(generateDefaultUsername());
        user.setStatus("ACTIVE");
        userAccountMapper.insert(user);

        Workspace personalWorkspace = new Workspace();
        personalWorkspace.setWorkspaceType("PERSONAL");
        personalWorkspace.setName("个人空间");
        personalWorkspace.setOwnerUserId(user.getId());
        personalWorkspace.setStatus("ACTIVE");
        workspaceMapper.insert(personalWorkspace);
        user.setLastWorkspaceId(personalWorkspace.getId());
        userAccountMapper.updateById(user);

        ensurePointAccount(personalWorkspace.getId());
        return user;
    }

    private Workspace getOrCreatePersonalWorkspace(UserAccount user) {
        Workspace workspace = workspaceMapper.selectOne(new LambdaQueryWrapper<Workspace>()
                .eq(Workspace::getWorkspaceType, "PERSONAL")
                .eq(Workspace::getOwnerUserId, user.getId())
                .eq(Workspace::getStatus, "ACTIVE"));
        if (workspace == null) {
            workspace = new Workspace();
            workspace.setWorkspaceType("PERSONAL");
            workspace.setName("个人空间");
            workspace.setOwnerUserId(user.getId());
            workspace.setStatus("ACTIVE");
            workspaceMapper.insert(workspace);
        }
        ensurePointAccount(workspace.getId());
        return workspace;
    }

    private void ensurePointAccount(Long workspaceId) {
        pointAccountMapper.insertIgnore(workspaceId);
    }

    private Workspace resolveCurrentWorkspace(UserAccount user, Workspace personalWorkspace) {
        if (user.getLastWorkspaceId() == null) {
            return personalWorkspace;
        }
        Workspace lastWorkspace = workspaceMapper.selectById(user.getLastWorkspaceId());
        if (lastWorkspace == null || !"ACTIVE".equals(lastWorkspace.getStatus())) {
            return personalWorkspace;
        }
        if ("PERSONAL".equals(lastWorkspace.getWorkspaceType())) {
            return lastWorkspace.getOwnerUserId().equals(user.getId()) ? lastWorkspace : personalWorkspace;
        }
        if (!"TEAM".equals(lastWorkspace.getWorkspaceType())) {
            return personalWorkspace;
        }
        TeamMember membership = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamWorkspaceId, lastWorkspace.getId())
                .eq(TeamMember::getUserId, user.getId())
                .eq(TeamMember::getMemberStatus, "ACTIVE"));
        return membership == null ? personalWorkspace : lastWorkspace;
    }

    /** 首次注册时生成“用户”加 6 位小写字母或数字的默认用户名。 */
    private String generateDefaultUsername() {
        StringBuilder username = new StringBuilder("用户");
        for (int i = 0; i < 6; i++) {
            username.append(USERNAME_SUFFIX_CHARS.charAt(
                    SECURE_RANDOM.nextInt(USERNAME_SUFFIX_CHARS.length())));
        }
        return username.toString();
    }
}
