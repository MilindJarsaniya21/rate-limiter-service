package dev.milind.ratelimiter.core;

public interface RateLimiter {
    boolean isAllowed(String clientId);
}
