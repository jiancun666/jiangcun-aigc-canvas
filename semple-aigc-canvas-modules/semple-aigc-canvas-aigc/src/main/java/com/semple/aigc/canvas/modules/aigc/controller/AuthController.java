package com.semple.aigc.canvas.modules.aigc.controller;

import com.semple.aigc.canvas.common.core.domain.R;
import com.semple.aigc.canvas.modules.aigc.dto.EmailCaptchaRequest;
import com.semple.aigc.canvas.modules.aigc.dto.EmailLoginRequest;
import com.semple.aigc.canvas.modules.aigc.dto.EmailLoginResponse;
import com.semple.aigc.canvas.modules.aigc.service.AuthService;
import com.semple.aigc.canvas.modules.aigc.service.EmailCaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * 邮箱认证接口。
 */
@Tag(name = "邮箱认证", description = "邮箱验证码发送与登录")
@SecurityRequirements
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final EmailCaptchaService emailCaptchaService;
    private final AuthService authService;

    public AuthController(EmailCaptchaService emailCaptchaService, AuthService authService) {
        this.emailCaptchaService = emailCaptchaService;
        this.authService = authService;
    }

    /**
     * 向邮箱发送登录验证码，验证码有效期、发送频率及单次使用规则由验证码服务控制。
     */
    @Operation(summary = "发送邮箱验证码")
    @PostMapping("/email/captcha")
    public R<Void> sendEmailCaptcha(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "发送验证码所需的邮箱信息", required = true)
            @RequestBody
            EmailCaptchaRequest request) {
        emailCaptchaService.sendCaptcha(request.getEmail().trim().toLowerCase(Locale.ROOT));
        return R.ok();
    }

    /**
     * 校验邮箱验证码并登录；首次使用的邮箱验证通过后按认证服务规则自动注册。
     */
    @Operation(summary = "邮箱验证码登录")
    @PostMapping("/login/email")
    public R<EmailLoginResponse> emailLogin(
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "邮箱地址及收到的验证码", required = true)
            @RequestBody
            EmailLoginRequest request) {
        return R.ok(authService.emailLogin(request));
    }
}
