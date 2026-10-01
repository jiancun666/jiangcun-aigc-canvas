package com.semple.aigc.canvas.modules.aigc.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** AIGC 在线接口文档与 JWT 调试配置。 */
@Configuration
public class OpenApiConfig {

    /** 提供网关、服务直连两种调试地址，以及 Bearer Token 输入。 */
    @Bean
    public OpenAPI aigcOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Semple AIGC Canvas API")
                        .description("认证、项目、画布、模型、生成任务、素材与积分接口")
                        .version("1.0.0"))
                .addServersItem(new Server().url("/aigc").description("通过网关调用"))
                .addServersItem(new Server().url("/").description("AIGC 服务直连"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .description("业务接口 JWT：填入邮箱登录返回的 token，无需填写 Bearer 前缀；内部积分接口使用独立请求头认证")
                                .scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
