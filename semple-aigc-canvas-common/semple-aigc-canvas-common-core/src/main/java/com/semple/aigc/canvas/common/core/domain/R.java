package com.semple.aigc.canvas.common.core.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * Standard API response.
 *
 * @author aofaming
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int SUCCESS = 200;

    public static final int FAIL = 500;

    private int code;

    private String msg;

    private T data;

    public R() {
    }

    public R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return restResult(null, SUCCESS, "success");
    }

    public static <T> R<T> ok(T data) {
        return restResult(data, SUCCESS, "success");
    }

    public static <T> R<T> ok(T data, String msg) {
        return restResult(data, SUCCESS, msg);
    }

    public static <T> R<T> fail(String msg) {
        return restResult(null, FAIL, msg);
    }

    public static <T> R<T> fail(int code, String msg) {
        return restResult(null, code, msg);
    }

    public static <T> R<T> restResult(T data, int code, String msg) {
        return new R<>(code, msg, data);
    }

    public boolean isSuccess() {
        return code == SUCCESS;
    }
}
