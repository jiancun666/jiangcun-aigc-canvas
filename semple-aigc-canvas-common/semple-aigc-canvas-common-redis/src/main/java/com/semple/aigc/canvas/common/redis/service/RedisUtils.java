package com.semple.aigc.canvas.common.redis.service;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis operation helpers.
 *
 * @author aofaming
 */
@Component
public class RedisUtils {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUtils(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public <T> void setCacheObject(String key, T value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public <T> void setCacheObject(String key, T value, Duration timeout) {
        redisTemplate.opsForValue().set(key, value, timeout);
    }

    @SuppressWarnings("unchecked")
    public <T> T getCacheObject(String key) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    public Boolean deleteObject(String key) {
        return redisTemplate.delete(key);
    }

    public Long deleteObject(Collection<String> keys) {
        return redisTemplate.delete(keys);
    }

    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    public Long getExpire(String key) {
        return redisTemplate.getExpire(key);
    }

    public boolean hasKey(String key) {
        Boolean result = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(result);
    }

    public <T> Long setCacheList(String key, List<T> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        return redisTemplate.opsForList().rightPushAll(key, values.toArray());
    }

    public List<Object> getCacheList(String key) {
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    public Set<String> keys(String pattern) {
        return redisTemplate.keys(pattern);
    }
}
