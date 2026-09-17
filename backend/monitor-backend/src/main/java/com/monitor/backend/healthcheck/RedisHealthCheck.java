package com.monitor.backend.healthcheck;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedisHealthCheck {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("/health/redis")
    public String checkRedis() {
        try {
            redisTemplate.opsForValue().set("healthcheck", "ok");
            String value = redisTemplate.opsForValue().get("healthcheck");
            return "Redis connection OK, value=" + value;
        } catch (Exception e) {
            return "Redis connection FAILED: " + e.getMessage();
        }
    }
}
