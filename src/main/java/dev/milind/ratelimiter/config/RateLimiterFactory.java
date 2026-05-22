package dev.milind.ratelimiter.config;

import dev.milind.ratelimiter.core.RateLimiter;
import dev.milind.ratelimiter.core.TokenBucketRateLimiter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RateLimiterFactory {

    private final Map<String, RateLimiter> rateLimiters = new HashMap<>();

    public RateLimiterFactory (RateLimitingProperties properties) {

        if(properties.getPlans() != null) {
            properties.getPlans().forEach((planName, plan) -> {
                RateLimiter limiter = new TokenBucketRateLimiter(
                        plan.getCapacity(),
                        plan.getRefillRate(),
                        plan.getRefillPeriod(),
                        plan.getTimeUnit()
                );

                rateLimiters.put(planName, limiter);
            });
        }
    }

    public RateLimiter getRateLimiter(String planName) {
        RateLimiter limiter = rateLimiters.get(planName);

        if(limiter == null) {
            throw new IllegalArgumentException("No rate limiter configured for plan: " + planName);
        }

        return limiter;
    }
}
