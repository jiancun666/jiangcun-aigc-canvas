package com.semple.aigc.canvas.modules.aigc.service.impl;

import com.semple.aigc.canvas.common.core.constant.ErrorCode;
import com.semple.aigc.canvas.common.core.exception.BizException;
import com.semple.aigc.canvas.modules.aigc.config.EmailProperties;
import com.semple.aigc.canvas.modules.aigc.service.EmailCaptchaService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Properties;

/** 邮箱验证码发送和验证。 */
@Slf4j
@Service
public class EmailCaptchaServiceImpl implements EmailCaptchaService {

    private static final String CAPTCHA_KEY_PREFIX = "semple-aigc-canvas:auth:email:captcha:";
    private static final String LIMIT_KEY_PREFIX = "semple-aigc-canvas:auth:email:limit:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final StringRedisTemplate redisTemplate;
    private final EmailProperties emailProperties;

    public EmailCaptchaServiceImpl(StringRedisTemplate redisTemplate, EmailProperties emailProperties) {
        this.redisTemplate = redisTemplate;
        this.emailProperties = emailProperties;
    }

    @Override
    public void sendCaptcha(String email) {
        String limitKey = LIMIT_KEY_PREFIX + email;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(limitKey, "1", Duration.ofMinutes(1));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BizException(ErrorCode.EMAIL_SEND_TOO_FREQUENT);
        }

        String captcha = String.format("%06d", RANDOM.nextInt(1_000_000));
        try {
            sendEmail(email, captcha);
            redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + email, captcha, Duration.ofMinutes(5));
        } catch (Exception e) {
            redisTemplate.delete(limitKey);
            log.error("发送邮件验证码失败，邮箱：{}", email, e);
            throw new BizException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    @Override
    public boolean verifyCaptcha(String email, String captcha) {
        String storedCaptcha = redisTemplate.opsForValue().getAndDelete(CAPTCHA_KEY_PREFIX + email);
        return storedCaptcha != null && storedCaptcha.equals(captcha);
    }

    private void sendEmail(String email, String captcha) throws MessagingException, UnsupportedEncodingException {
        if (emailProperties.getHost() == null || emailProperties.getHost().isBlank()
                || emailProperties.getUsername() == null || emailProperties.getUsername().isBlank()
                || emailProperties.getPassword() == null || emailProperties.getPassword().isBlank()) {
            throw new IllegalStateException("邮件SMTP配置不完整");
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(emailProperties.getHost());
        sender.setPort(emailProperties.getPort());
        sender.setUsername(emailProperties.getUsername());
        sender.setPassword(emailProperties.getPassword());
        sender.setDefaultEncoding("UTF-8");
        Properties mailProperties = sender.getJavaMailProperties();
        mailProperties.put("mail.transport.protocol", "smtp");
        mailProperties.put("mail.smtp.auth", "true");
        mailProperties.put("mail.smtp.timeout", "5000");
        if (Boolean.TRUE.equals(emailProperties.getSslEnabled())) {
            mailProperties.put("mail.smtp.ssl.enable", "true");
            mailProperties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        }

        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        String from = emailProperties.getFrom() == null || emailProperties.getFrom().isBlank()
                ? emailProperties.getUsername() : emailProperties.getFrom();
        if (emailProperties.getFromName() == null || emailProperties.getFromName().isBlank()) {
            helper.setFrom(from);
        } else {
            helper.setFrom(from, emailProperties.getFromName());
        }
        helper.setTo(email);
        helper.setSubject("邮箱验证码");
        helper.setText(buildEmailContent(captcha), true);
        sender.send(message);
    }

    private String buildEmailContent(String captcha) {
        return "<div style='padding:20px;background:#f5f5f5'><div style='max-width:600px;margin:auto;" +
                "background:#fff;padding:30px;border-radius:10px'><h2 style='text-align:center'>登录验证码</h2>" +
                "<p>您的验证码是：</p><p style='font-size:32px;font-weight:bold;text-align:center;letter-spacing:5px'>" +
                captcha + "</p><p style='color:#999;text-align:center'>验证码5分钟内有效，请勿向他人提供。</p></div></div>";
    }
}
