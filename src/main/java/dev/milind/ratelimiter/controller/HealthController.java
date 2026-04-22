package dev.milind.ratelimiter.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    public final StringRedisTemplate redisTemplate;

    public HealthController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }

    @GetMapping("/redis-test")
    public String redisTest() {
        String key = "rate:test";
        redisTemplate.opsForValue().set(key, "1");
        return "Redis OK + value = " + redisTemplate.opsForValue().get(key);
    }
}
