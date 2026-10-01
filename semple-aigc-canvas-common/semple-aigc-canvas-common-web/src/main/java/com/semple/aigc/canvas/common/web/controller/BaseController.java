package com.semple.aigc.canvas.common.web.controller;

import com.semple.aigc.canvas.common.core.domain.R;

/**
 * Base controller helpers.
 *
 * @author aofaming
 */
public abstract class BaseController {

    protected <T> R<T> success(T data) {
        return R.ok(data);
    }

    protected R<Void> success() {
        return R.ok();
    }

    protected R<Void> error(String message) {
        return R.fail(message);
    }
}
