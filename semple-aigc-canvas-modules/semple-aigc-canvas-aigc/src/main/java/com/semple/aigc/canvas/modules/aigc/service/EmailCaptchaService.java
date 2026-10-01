package com.semple.aigc.canvas.modules.aigc.service;

public interface EmailCaptchaService {
    void sendCaptcha(String email);
    boolean verifyCaptcha(String email, String captcha);
}
