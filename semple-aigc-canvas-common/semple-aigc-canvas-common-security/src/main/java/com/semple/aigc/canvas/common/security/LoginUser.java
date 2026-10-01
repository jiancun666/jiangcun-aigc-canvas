package com.semple.aigc.canvas.common.security;

import lombok.Data;

import java.io.Serializable;
import java.util.Set;

/**
 * Login user information.
 *
 * @author aofaming
 */
@Data
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private String username;

    private String phone;

    private String email;

    private Set<String> roles;

    private Set<String> permissions;
}
