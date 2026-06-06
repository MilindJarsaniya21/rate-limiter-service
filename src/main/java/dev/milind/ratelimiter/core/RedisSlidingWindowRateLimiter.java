package dev.milind.ratelimiter.core;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RedisSlidingWindowRateLimiter implements RateLimiter{

    private final StringRedisTemplate redisTemplate;
    private final int capacity;
    private final long windowSizeMillis;
    private final DefaultRedisScript<Long> redisScript;

    // The Lua script that executes atomically on the Redis server
    private static final String LUA_SCRIPT =
        "local key = KEYS[1]\n" +
                "local now = tonumber(ARGV[1])\n" +
                "local window_start = tonumber(ARGV[2])\n" +
                "local capacity = tonumber(ARGV[3])\n" +
                "local member = ARGV[4]\n" +
                "local ttl = tonumber(ARGV[5])\n" +

                "-- 1. Remove old records outside the current window\n" +
                "redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)\n" +

                "-- 2. Count the remaining records (requests in current window)\n" +
                "local current_count = redis.call('ZCARD', key)\n" +

                "-- 3. Decide to allow or reject\n" +
                "if current_count < capacity then\n" +
                "    -- Allow: Add the new request timestamp\n" +
                "    redis.call('ZADD', key, now, member)\n" +
                "    -- Set TTL so the key expires if the user stops making requests (memory cleanup)\n" +
                "    redis.call('PEXPIRE', key, ttl)\n" +
                "    return 1\n" +
                "else\n" +
                "    -- Reject\n" +
                "    return 0\n" +
                "end";

    public RedisSlidingWindowRateLimiter(StringRedisTemplate redisTemplate, int  capacity, long windowPeriod, TimeUnit timeUnit){
        if(capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        if(windowPeriod <= 0) throw new IllegalArgumentException("Window Period must be positive");

        this.redisTemplate = redisTemplate;
        this.capacity = capacity;
        this.windowSizeMillis = timeUnit.toMillis(windowPeriod);

        // Initialize the script exactly once
        this.redisScript = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
    }

    @Override
    public boolean isAllowed(String clientId) {
        String key = "rate_limit:sliding_window:" + clientId;
        long now = System.currentTimeMillis();
        long windowStart = now - windowSizeMillis;

        // A unique identifier for this specific request.
        // We append a UUID to the timestamp to guarantee uniqueness even if two requests happen in the exact same millisecond.
        String member = now + "-" + UUID.randomUUID().toString();

        // Execute the Lua script atomically on Redis
        Long result = redisTemplate.execute(
                redisScript,
                Collections.singletonList(key),
                String.valueOf(now),
                String.valueOf(windowStart),
                String.valueOf(capacity),
                member,
                String.valueOf(windowSizeMillis)
        );

        return result == 1L;
    }
}
