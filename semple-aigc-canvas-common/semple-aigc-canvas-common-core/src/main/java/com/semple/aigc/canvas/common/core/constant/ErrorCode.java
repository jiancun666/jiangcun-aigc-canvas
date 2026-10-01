package com.semple.aigc.canvas.common.core.constant;

/**
 * 全局异常错误码枚举.
 *
 * @author aofaming
 */
public enum ErrorCode {

    // ========== 通用错误 ==========
    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    UNAUTHORIZED(401, "未认证，请先登录"),
    TOKEN_AUTH_EXPIRED(601, "登录状态已失效，请重新登录"),
    FORBIDDEN(403, "无权限访问该资源"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不被允许"),
    CONFLICT(409, "资源冲突"),
    INTERNAL_ERROR(500, "系统开小猜了,请稍后再试"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用"),

    // ========== 参数校验错误 1xxx ==========
    PARAM_ERROR(1000, "参数校验失败"),
    PARAM_MISSING(1001, "缺少必要参数"),
    PARAM_TYPE_ERROR(1002, "参数类型错误"),
    PARAM_OUT_OF_RANGE(1003, "参数超出范围"),

    // ========== 认证授权错误 2xxx ==========
    LOGIN_FAILED(2000, "登录失败，用户名或密码错误"),
    ACCOUNT_DISABLED(2001, "账号已被禁用"),
    ACCOUNT_LOCKED(2002, "账号已被锁定"),
    TOKEN_EXPIRED(2003, "令牌已过期"),
    TOKEN_INVALID(2004, "无效令牌"),
    REFRESH_TOKEN_EXPIRED(2005, "刷新令牌已过期"),
    SMS_SEND_FAILED(2006, "发送短信失败,请稍后再试"),
    CAPTCHA_ERROR(2007, "验证码错误或已过期"),
    EMAIL_SEND_FAILED(2010, "邮件发送失败,请稍后再试"),
    EMAIL_SEND_TOO_FREQUENT(2011, "邮件发送过于频繁,请1分钟后再试"),
    EMAIL_FORMAT_ERROR(2012, "邮箱格式不正确"),

    // ========== 用户模块错误 3xxx ==========
    USER_NOT_FOUND(3000, "用户不存在"),
    USER_ALREADY_EXISTS(3001, "用户已存在"),
    USER_PASSWORD_ERROR(3002, "用户密码错误"),
    USER_ROLE_NOT_FOUND(3003, "用户角色不存在"),

    // ========== 文件模块错误 4xxx ==========
    FILE_NOT_FOUND(4000, "文件不存在"),
    FILE_UPLOAD_FAILED(4001, "文件上传失败"),
    FILE_TYPE_NOT_SUPPORTED(4002, "不支持的文件类型"),
    FILE_SIZE_EXCEEDED(4003, "文件大小超出限制"),

    // ========== AIGC 模块错误 5xxx ==========
    AIGC_REQUEST_FAILED(5000, "AIGC 请求失败"),
    AIGC_SERVICE_TIMEOUT(5001, "AIGC 服务超时"),

    // ========== 任务模块错误 6xxx ==========
    JOB_NOT_FOUND(6000, "任务不存在"),
    JOB_CREATE_FAILED(6001, "任务创建失败"),
    JOB_ALREADY_RUNNING(6002, "任务已在运行中"),

    // ========== 积分模块错误 7xxx ==========
    POINTS_USER_NOT_FOUND(7000, "用户不存在，无法变更积分"),
    POINTS_INSUFFICIENT(7001, "用户积分不足"),
    POINTS_UPDATE_FAILED(7002, "更新用户积分失败"),
    POINTS_DETAIL_NOT_FOUND(7003, "积分明细不存在"),
    POINTS_DETAIL_ID_REQUIRED(7004, "积分明细ID不能为空"),
    POINTS_DETAIL_UPDATE_FAILED(7005, "修改积分明细失败");


    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
