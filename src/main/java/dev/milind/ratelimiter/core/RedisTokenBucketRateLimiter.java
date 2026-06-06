package dev.milind.ratelimiter.core;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class RedisTokenBucketRateLimiter implements RateLimiter {
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> redisScript;

    private final int capacity;
    private final int refillRate;
    private final long refillPeriod;

    private static final String LUA_SCRIPT =
            "local key = KEYS[1]\n" +
            "local capacity = tonumber(ARGV[1])\n" +
            "local refill_rate = tonumber(ARGV[2])\n" +
            "local refill_period_millis = tonumber(ARGV[3])\n" +
            "local now = tonumber(ARGV[4])\n" +

            "local bucket = redis.call('HGETALL', key)\n" +
            "local tokens\n" +
            "local last_refill_timestamp\n" +

            "if #bucket == 0 then\n" +
            "    -- First request, create a full bucket\n" +
            "    tokens = capacity\n" +
            "    last_refill_timestamp = now\n" +
            "else\n" +
            "    -- Existing bucket, parse values\n" +
            "    tokens = tonumber(bucket[2])\n" +
            "    last_refill_timestamp = tonumber(bucket[4])\n" +
            "end\n" +

            "local millis_since_last_refill = now - last_refill_timestamp\n" +
            "if millis_since_last_refill >= refill_period_millis then\n" +
            "    local periods_since_last = math.floor(millis_since_last_refill / refill_period_millis)\n" +
            "    local new_tokens = periods_since_last * refill_rate\n" +
            "    tokens = math.min(tokens + new_tokens, capacity)\n" +
            "    last_refill_timestamp = last_refill_timestamp + (periods_since_last * refill_period_millis)\n" +
            "end\n" +

            "if tokens > 0 then\n" +
            "    tokens = tokens - 1\n" +
            "    redis.call('HSET', key, 'tokens', tokens, 'last_refill_timestamp', last_refill_timestamp)\n" +
            "    redis.call('PEXPIRE', key, refill_period_millis * (capacity / refill_rate) * 2) -- Set a reasonable TTL\n" +
            "    return 1 -- Allowed\n" +
            "else\n" +
            "    return 0 -- Denied\n" +
            "end";

    public RedisTokenBucketRateLimiter(StringRedisTemplate redisTemplate, int capacity, int refillRate, long refillPeriod, TimeUnit timeUnit){
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        if (refillRate <= 0) throw new IllegalArgumentException("Refill rate must be positive");
        if (refillPeriod <= 0) throw new IllegalArgumentException("Refill period must be positive");

        this.redisTemplate = redisTemplate;
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.refillPeriod = timeUnit.toMillis(refillPeriod);

        this.redisScript = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
    }

    @Override
    public boolean isAllowed(String clintId) {
        String key = "rate_limit:token_bucket:" + clintId;

        long now = System.currentTimeMillis();

        Long result = redisTemplate.execute(
                redisScript,
                Collections.singletonList(key),
                String.valueOf(capacity),
                String.valueOf(refillRate),
                String.valueOf(refillPeriod),
                String.valueOf(now)
        );

        return result == 1L;
    }
}
