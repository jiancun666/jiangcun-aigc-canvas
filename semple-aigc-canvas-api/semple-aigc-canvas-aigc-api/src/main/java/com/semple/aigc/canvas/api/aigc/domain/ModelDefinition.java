package com.semple.aigc.canvas.api.aigc.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.semple.aigc.canvas.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型供应商接入定义。密钥字段只保存环境变量或密钥中心引用，不保存密钥明文。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aigc_model_definition")
public class ModelDefinition extends BaseEntity {
    /**
     * 供应商编码，例如 ARK、DASHSCOPE。
     */
    private String providerCode;
    /**
     * 供应商侧模型或推理接入点编码。
     */
    private String modelCode;
    /**
     * 前端展示名称。
     */
    private String modelName;
    /**
     * 模型类型：1文本，2图片，3视频，4音频。
     */
    private Integer modelType;
    /**
     * 服务端适配器编码。
     */
    private String adapterCode;
    /**
     * 自定义调用地址；为空时使用 SDK 默认地址。
     */
    private String endpointUrl;
    /**
     * 密钥引用，例如 env:ARK_API_KEY。
     */
    @JsonIgnore
    private String credentialRef;
    /**
     * 模型支持的尺寸、数量等能力 JSON。
     */
    private String capabilityConfig;
    /**
     * 默认请求参数 JSON。
     */
    private String defaultRequestConfig;
    /**
     * 单次任务超时秒数。
     */
    private Integer timeoutSeconds;
    /**
     * 是否启用：0否，1是。
     */
    private Integer enabled;
}
