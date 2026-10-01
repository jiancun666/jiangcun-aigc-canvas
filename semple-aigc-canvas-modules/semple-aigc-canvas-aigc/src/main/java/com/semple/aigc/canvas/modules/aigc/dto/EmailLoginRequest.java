package com.semple.aigc.canvas.modules.aigc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 邮箱验证码登录请求。 */
@Data
@Schema(description = "使用邮箱及验证码登录的请求")
public class EmailLoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "已接收验证码的邮箱地址，必须符合邮箱格式；服务端去除首尾空白并转为小写", example = "test@example.com")
    @NotBlank(message = "邮箱不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "邮箱格式不正确")
    private String email;

    @Schema(description = "邮箱收到的 6 位数字验证码，必须在有效期内且未使用", example = "123456")
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码必须为6位数字")
    private String captcha;
}
