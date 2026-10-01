package com.semple.aigc.canvas.common.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.semple.aigc.canvas.common.security.config.JwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * JWT工具类，基于nimbus-jose-jwt实现.
 *
 * @author feilong
 * @date 2026-05-27
 * @desc JWT令牌生成、解析与验证工具
 */
@Slf4j
@Component
public class JwtUtils {

    private static JwtProperties jwtProperties;

    @Autowired
    public void setJwtProperties(JwtProperties jwtProperties) {
        JwtUtils.jwtProperties = jwtProperties;
    }

    /**
     * 生成JWT令牌.
     *
     * @param userId   用户ID
     * @param username 用户名
     * @return JWT令牌字符串
     */
    public static String generateToken(Long userId, String username) {
        return generateToken(userId, username, null, null, getJwtProperties().getExpirationMinutes());
    }

    /**
     * 生成包含当前工作空间上下文的JWT令牌。
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param workspaceId 当前工作空间ID
     * @param workspaceType 当前工作空间类型
     * @return JWT令牌字符串
     */
    public static String generateToken(Long userId, String username, Long workspaceId, String workspaceType) {
        return generateToken(userId, username, workspaceId, workspaceType,
                getJwtProperties().getExpirationMinutes());
    }

    /**
     * 生成JWT令牌（自定义过期时间）.
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param expireMinutes 过期时间（分钟）
     * @return JWT令牌字符串
     */
    public static String generateToken(Long userId, String username, long expireMinutes) {
        return generateToken(userId, username, null, null, expireMinutes);
    }

    private static String generateToken(Long userId, String username, Long workspaceId, String workspaceType,
                                        long expireMinutes) {
        try {
            JWSSigner signer = new MACSigner(getJwtProperties().getSecret().getBytes());

            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .subject(username)
                    .claim("userId", userId)
                    .claim("username", username)
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + Duration.ofMinutes(expireMinutes).toMillis()))
                    .jwtID(UUID.randomUUID().toString());
            if (workspaceId != null) {
                claimsBuilder.claim("workspaceId", workspaceId);
            }
            if (workspaceType != null) {
                claimsBuilder.claim("workspaceType", workspaceType);
            }
            JWTClaimsSet claimsSet = claimsBuilder.build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (JOSEException e) {
            log.error("生成JWT令牌失败，异常信息：{}", ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("生成JWT令牌失败", e);
        }
    }

    /**
     * 解析并验证JWT令牌.
     *
     * @param token JWT令牌字符串
     * @return JWT声明集合
     */
    public static JWTClaimsSet parseToken(String token) {
        try {
            JWTClaimsSet claimsSet = parseAndVerifyToken(token);
            if (isExpired(claimsSet)) {
                RuntimeException ex = new RuntimeException("JWT令牌已过期");
                log.error("JWT令牌已过期，异常信息：{}", ExceptionUtils.getStackTrace(ex));
                throw ex;
            }

            return claimsSet;
        } catch (ParseException | JOSEException e) {
            log.error("解析JWT令牌失败，异常信息：{}", ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("解析JWT令牌失败", e);
        }
    }

    /**
     * 从令牌中提取用户ID.
     *
     * @param token JWT令牌字符串
     * @return 用户ID
     */
    public static Long getUserIdFromToken(String token) {
        try {
            JWTClaimsSet claimsSet = parseToken(token);
            return claimsSet.getLongClaim("userId");
        } catch (ParseException e) {
            log.error("从令牌中提取用户ID失败，异常信息：{}", ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("从令牌中提取用户ID失败", e);
        }
    }

    /**
     * 从令牌中提取用户名.
     *
     * @param token JWT令牌字符串
     * @return 用户名
     */
    public static String getUsernameFromToken(String token) {
        try {
            JWTClaimsSet claimsSet = parseToken(token);
            return claimsSet.getStringClaim("username");
        } catch (ParseException e) {
            log.error("从令牌中提取用户名失败，异常信息：{}", ExceptionUtils.getStackTrace(e));
            throw new RuntimeException("从令牌中提取用户名失败", e);
        }
    }

    /** 从令牌中提取当前工作空间ID。 */
    public static Long getWorkspaceIdFromToken(String token) {
        try {
            return parseToken(token).getLongClaim("workspaceId");
        } catch (ParseException e) {
            throw new RuntimeException("从令牌中提取工作空间ID失败", e);
        }
    }

    /** 从令牌中提取当前工作空间类型。 */
    public static String getWorkspaceTypeFromToken(String token) {
        try {
            return parseToken(token).getStringClaim("workspaceType");
        } catch (ParseException e) {
            throw new RuntimeException("从令牌中提取工作空间类型失败", e);
        }
    }

    /**
     * 验证令牌是否有效（签名和过期时间）.
     *
     * @param token JWT令牌字符串
     * @return 有效返回true
     */
    public static boolean validateToken(String token) {
        return JwtValidateStatus.VALID.equals(validateTokenStatus(token));
    }

    /**
     * 校验令牌状态.
     *
     * @param token JWT令牌字符串
     * @return 令牌校验状态
     */
    public static JwtValidateStatus validateTokenStatus(String token) {
        try {
            JWTClaimsSet claimsSet = parseAndVerifyToken(token);
            if (!isExpired(claimsSet)) {
                return JwtValidateStatus.VALID;
            }
            return isExpiredWithinCodeWindow(claimsSet)
                    ? JwtValidateStatus.EXPIRED_WITHIN_WINDOW
                    : JwtValidateStatus.EXPIRED_OUT_OF_WINDOW;
        } catch (Exception e) {
            log.error("验证令牌状态失败，异常信息：{}", ExceptionUtils.getStackTrace(e));
            return JwtValidateStatus.INVALID;
        }
    }

    /**
     * 解析并验证令牌签名，不校验过期时间.
     *
     * @param token JWT令牌字符串
     * @return JWT声明集合
     * @throws ParseException 解析异常
     * @throws JOSEException  签名校验异常
     */
    private static JWTClaimsSet parseAndVerifyToken(String token) throws ParseException, JOSEException {
        SignedJWT signedJWT = SignedJWT.parse(token);
        JWSVerifier verifier = new MACVerifier(getJwtProperties().getSecret().getBytes());

        if (!signedJWT.verify(verifier)) {
            RuntimeException ex = new RuntimeException("JWT签名验证失败");
            log.error("JWT签名验证失败，异常信息：{}", ExceptionUtils.getStackTrace(ex));
            throw ex;
        }
        return signedJWT.getJWTClaimsSet();
    }

    /**
     * 判断令牌是否已过期.
     *
     * @param claimsSet JWT声明集合
     * @return 是否已过期
     */
    private static boolean isExpired(JWTClaimsSet claimsSet) {
        Date expirationTime = claimsSet.getExpirationTime();
        return expirationTime == null || expirationTime.before(new Date());
    }

    /**
     * 判断令牌是否在过期识别窗口内.
     *
     * @param claimsSet JWT声明集合
     * @return 是否在过期识别窗口内
     */
    private static boolean isExpiredWithinCodeWindow(JWTClaimsSet claimsSet) {
        Date expirationTime = claimsSet.getExpirationTime();
        if (expirationTime == null) {
            return false;
        }
        long expiredMillis = System.currentTimeMillis() - expirationTime.getTime();
        long windowMillis = Duration.ofMinutes(getJwtProperties().getExpiredCodeWindowMinutes()).toMillis();
        return expiredMillis >= 0 && expiredMillis <= windowMillis;
    }

    /**
     * 获取 JWT 配置。
     *
     * @return JWT 配置
     */
    private static JwtProperties getJwtProperties() {
        if (jwtProperties == null) {
            throw new IllegalStateException("JWT配置未初始化，请检查Spring扫描范围或JwtProperties配置");
        }
        return jwtProperties;
    }
}
