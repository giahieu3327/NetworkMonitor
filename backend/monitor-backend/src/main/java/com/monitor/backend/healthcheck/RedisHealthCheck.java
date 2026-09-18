package com.monitor.backend.healthcheck;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisHealthCheck {

    private final StringRedisTemplate redisTemplate;

    public RedisHealthCheck(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean checkConnection() {
        try {
            String key = "healthcheck";
            String value = "ok";

            redisTemplate.opsForValue().set(key, value);

            String result = redisTemplate.opsForValue().get(key);

            return value.equals(result);
        } catch (Exception e) {
            return false;
        }
    }
}