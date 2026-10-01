package com.semple.aigc.canvas.modules.aigc.service;

import com.semple.aigc.canvas.modules.aigc.dto.EmailLoginRequest;
import com.semple.aigc.canvas.modules.aigc.dto.EmailLoginResponse;

public interface AuthService {
    EmailLoginResponse emailLogin(EmailLoginRequest request);
}
