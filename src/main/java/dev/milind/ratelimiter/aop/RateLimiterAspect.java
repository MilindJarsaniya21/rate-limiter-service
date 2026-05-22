package dev.milind.ratelimiter.aop;

import dev.milind.ratelimiter.config.RateLimiterFactory;
import dev.milind.ratelimiter.core.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class RateLimiterAspect {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterAspect.class);

    private final RateLimiterFactory rateLimiterFactory;

    public RateLimiterAspect(RateLimiterFactory rateLimiterFactory) {
        this.rateLimiterFactory = rateLimiterFactory;
        logger.info("RateLimiterAspect initialized.");
    }

    @Around("@annotation(rateLimited)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        logger.info("--- AOP: Rate limiting aspect triggered for method: {} ---", joinPoint.getSignature().toShortString());

        String planName = rateLimited.plan();
        RateLimiter rateLimiter = rateLimiterFactory.getRateLimiter(planName);
        logger.info("Using plan: '{}'", planName);

        String clientId = getClientId();
        logger.info("Client ID: '{}'", clientId);

        boolean allowed = rateLimiter.isAllowed(clientId);
        logger.info("Is request allowed? {}", allowed);

        if (!allowed) {
            logger.warn("Rate limit EXCEEDED for client '{}' on plan '{}'", clientId, planName);
            throw new RateLimitExceededException("Rate limit exceeded. Try again later.");
        }

        logger.info("--- AOP: Request allowed, proceeding with method execution. ---");
        return joinPoint.proceed();
    }

    private String getClientId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            return ip;
        }
        // This is a fallback and should not happen in a web context
        return "unknown-client";
    }
}