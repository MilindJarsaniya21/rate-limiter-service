package dev.milind.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@ConfigurationProperties(prefix = "rate-limiting")
public class RateLimitingProperties {

    private Map<String, Plan> plans;

    public Map<String, Plan> getPlans() {
        return plans;
    }

    public void setPlans(Map<String, Plan> plans) {
        this.plans = plans;
    }

    public static class Plan{
        private int capacity;
        private int refillRate;
        private long refillPeriod;
        private TimeUnit timeUnit;

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }

        public int getRefillRate() { return refillRate; }
        public void setRefillRate(int refillRate) { this.refillRate = refillRate; }

        public long getRefillPeriod() { return refillPeriod; }
        public void setRefillPeriod(long refillPeriod) { this.refillPeriod = refillPeriod; }

        public TimeUnit getTimeUnit() { return timeUnit; }
        public void setTimeUnit(TimeUnit timeUnit) { this.timeUnit = timeUnit; }
    }
}
