package dev.milind.ratelimiter.config;

import dev.milind.ratelimiter.core.RateLimiter;
import dev.milind.ratelimiter.core.RedisSlidingWindowRateLimiter;
import dev.milind.ratelimiter.core.RedisTokenBucketRateLimiter;
import dev.milind.ratelimiter.core.TokenBucketRateLimiter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class RateLimiterFactory {

    private final Map<String, RateLimiter> rateLimiters = new HashMap<>();
    private final StringRedisTemplate stringRedisTemplate;

    public RateLimiterFactory (RateLimitingProperties properties, StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;

        if(properties.getPlans() != null) {
            properties.getPlans().forEach((planName, plan) -> {
                rateLimiters.put(planName, createRateLimiter(plan));
            });
        }
    }

    private RateLimiter createRateLimiter(RateLimitingProperties.Plan plan) {
        String algorithm = Objects.requireNonNull(plan.getAlgorithm(), "Algorithm must be specified for plan");

        return switch (algorithm.toUpperCase()) {
            case "SLIDING_WINDOW_REDIS" -> new RedisSlidingWindowRateLimiter(
                    stringRedisTemplate,
                    plan.getCapacity(),
                    plan.getRefillPeriod(),
                    plan.getTimeUnit()
            );
            case "TOKEN_BUCKET_REDIS" -> new RedisTokenBucketRateLimiter(
                    stringRedisTemplate,
                    plan.getCapacity(),
                    plan.getRefillRate(),
                    plan.getRefillPeriod(),
                    plan.getTimeUnit()
            );
            case "TOKEN_BUCKET_LOCAL" -> new TokenBucketRateLimiter(
                    plan.getCapacity(),
                    plan.getRefillRate(),
                    plan.getRefillPeriod(),
                    plan.getTimeUnit()
            );
            default -> throw new IllegalArgumentException("Unknown rate limiting algorithm: " + algorithm);
        };
    }

    public RateLimiter getRateLimiter(String planName) {
        RateLimiter limiter = rateLimiters.get(planName);

        if(limiter == null) {
            throw new IllegalArgumentException("No rate limiter configured for plan: " + planName);
        }

        return limiter;
    }
}
