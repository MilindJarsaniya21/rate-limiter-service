package dev.milind.ratelimiter.core;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class TokenBucketRateLimiter implements RateLimiter {

    private final int capacity;
    private final int refillRate;
    private final long refillPeriodMillis;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static class Bucket {
        private long tokens;
        private long lastRefillTimestamp;

        Bucket(long tokens, long lastRefillTimestamp) {
            this.tokens = tokens;
            this.lastRefillTimestamp = lastRefillTimestamp;
        }

        public long getTokens() {
            return tokens;
        }

        public void setTokens(long tokens) {
            this.tokens = tokens;
        }

        public long getLastRefillTimestamp() {
            return this.lastRefillTimestamp;
        }

        public void setLastRefillTimestamp(long lastRefillTimestamp) {
            this.lastRefillTimestamp = lastRefillTimestamp;
        }
    }

    public TokenBucketRateLimiter(int capacity, int refillRate, long refillPeriod, TimeUnit timeUnit) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        if (refillRate <= 0) throw new IllegalArgumentException("Refill rate must be positive");
        if (refillPeriod <= 0) throw new IllegalArgumentException("Refill period must be positive");

        this.capacity = capacity;
        this.refillRate = refillRate;
        this.refillPeriodMillis = timeUnit.toMillis(refillPeriod);
    }

    @Override
    public boolean isAllowed(String clientId) {
        // Get-or-create the bucket for the client atomically.
        Bucket bucket = buckets.computeIfAbsent(clientId, k -> new Bucket(capacity, System.currentTimeMillis()));

        // Synchronize on the specific client's bucket to handle concurrent requests for the SAME client.
        synchronized (bucket) {
            refill(bucket);

            if (bucket.getTokens() > 0) {
                bucket.setTokens(bucket.getTokens() - 1);
                return true;
            }
        }
        return false;
    }

    private void refill(Bucket bucket){
        long now = System.currentTimeMillis();
        long millisSinceLastRefill = now - bucket.getLastRefillTimestamp();

        if(millisSinceLastRefill >= refillPeriodMillis){
            long periodSinceLast = millisSinceLastRefill / refillPeriodMillis;
            long newTokens = periodSinceLast * refillRate;

            long updatedTokens = Math.min(bucket.getTokens() + newTokens, capacity);
            bucket.setTokens(updatedTokens);

            bucket.setLastRefillTimestamp(bucket.getLastRefillTimestamp() + (periodSinceLast * refillPeriodMillis));
        }
    }
}
