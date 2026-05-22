package dev.milind.ratelimiter.controller;

import dev.milind.ratelimiter.aop.RateLimited;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final StringRedisTemplate redisTemplate;

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

    @GetMapping("/api/v1/health")
    @RateLimited(plan = "free") // This enforces 10 requests per minute based on application.yaml
    public String checkHealth() {
        return "Service is up and running!";
    }

    @GetMapping("/api/v1/premium-data")
    @RateLimited(plan = "premium") // This allows 100 requests per second
    public String getPremiumData() {
        return "Here is your premium data.";
    }
}
