package com.semple.aigc.canvas.modules.aigc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 邮箱验证码发送请求。 */
@Data
@Schema(description = "发送邮箱登录验证码的请求")
public class EmailCaptchaRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "接收验证码的邮箱地址，必须符合邮箱格式；服务端去除首尾空白并转为小写", example = "test@example.com")
    @NotBlank(message = "邮箱不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "邮箱格式不正确")
    private String email;
}
