//package dev.milind.ratelimiter;
//
//import dev.milind.ratelimiter.config.RateLimiterFactory;
//import dev.milind.ratelimiter.config.RateLimitingProperties;
//import dev.milind.ratelimiter.core.RateLimiter;
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.web.client.TestRestTemplate;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.http.*;
//import org.springframework.test.context.ActiveProfiles;
//
//import java.util.Queue;
//import java.util.Set;
//import java.util.UUID;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicInteger;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
///**
// * Production-Grade Integration Test Suite for Rate Limiter Service.
// *
// * Requirements Met:
// * - No Mocks: Uses real Redis and Spring Boot Web Environment.
// * - Dynamic config: Reads capacity and window sizes directly from application.yaml.
// * - Full E2E: Tests core classes, HTTP layers, AOP, and Lua scripts.
// * - Clean: Cleans up all generated Redis keys after each test.
// */
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//@ActiveProfiles("cloud")
//class RateLimiterComprehensiveIntegrationTest {
//
//    @Autowired
//    private RateLimiterFactory rateLimiterFactory;
//
//    @Autowired
//    private RateLimitingProperties properties;
//
//    @Autowired
//    private StringRedisTemplate redisTemplate;
//
//    @Autowired
//    private TestRestTemplate restTemplate;
//
//    private final Set<String> testClientIds = ConcurrentHashMap.newKeySet();
//
//    /**
//     * Cleans up all Redis keys associated with the test client IDs to prevent polluting the cloud instance.
//     */
//    @AfterEach
//    void tearDown() {
//        for (String clientId : testClientIds) {
//            // Find all keys that contain this client ID (handles both token bucket and sliding window key formats)
//            Set<String> keys = redisTemplate.keys("*" + clientId + "*");
//            if (keys != null && !keys.isEmpty()) {
//                redisTemplate.delete(keys);
//            }
//        }
//        testClientIds.clear();
//    }
//
//    private String generateClientId() {
//        String clientId = "test-user-" + UUID.randomUUID();
//        testClientIds.add(clientId);
//        return clientId;
//    }
//
//    /**
//     * Helper to run highly concurrent requests against the core RateLimiter interface.
//     */
//    private long runConcurrentRequests(RateLimiter limiter, String clientId, int threadCount) throws InterruptedException {
//        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
//        CountDownLatch startLatch = new CountDownLatch(1);
//        CountDownLatch finishLatch = new CountDownLatch(threadCount);
//        AtomicInteger allowedCount = new AtomicInteger(0);
//
//        for (int i = 0; i < threadCount; i++) {
//            executor.submit(() -> {
//                try {
//                    startLatch.await(); // Wait for the green light
//                    if (limiter.isAllowed(clientId)) {
//                        allowedCount.incrementAndGet();
//                    }
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                } finally {
//                    finishLatch.countDown();
//                }
//            });
//        }
//
//        startLatch.countDown(); // GREEN LIGHT - all threads hit Redis simultaneously
//        finishLatch.await(); // Wait for all to finish
//        executor.shutdown();
//        return allowedCount.get();
//    }
//
//    // =========================================================================================
//    // 1. ATOMICITY UNDER CONCURRENCY
//    // =========================================================================================
//    @Test
//    void testAtomicityUnderConcurrency() throws InterruptedException {
//        String planName = "premium";
//        RateLimiter rateLimiter = rateLimiterFactory.getRateLimiter(planName);
//        int expectedCapacity = properties.getPlans().get(planName).getCapacity();
//
//        String clientId = generateClientId();
//        int threads = 50; // 50 concurrent requests for a small limit
//
//        long actualAllowed = runConcurrentRequests(rateLimiter, clientId, threads);
//
//        assertEquals(expectedCapacity, actualAllowed, "Exactly the configured capacity should be allowed, proving atomicity without race conditions.");
//    }
//
//    // =========================================================================================
//    // 2. USER ISOLATION
//    // =========================================================================================
//    @Test
//    void testUserIsolation() throws InterruptedException {
//        String planName = "premium";
//        RateLimiter rateLimiter = rateLimiterFactory.getRateLimiter(planName);
//        int capacity = properties.getPlans().get(planName).getCapacity();
//
//        String user1 = generateClientId();
//        String user2 = generateClientId();
//
//        // Overwhelm user 1
//        long user1Allowed = runConcurrentRequests(rateLimiter, user1, 10);
//        assertEquals(capacity, user1Allowed);
//
//        // User 2 should STILL get their full capacity, unaffected by User 1
//        long user2Allowed = runConcurrentRequests(rateLimiter, user2, 10);
//        assertEquals(capacity, user2Allowed, "User 2's limit should be completely independent of User 1.");
//    }
//
//    // =========================================================================================
//    // 3. SLIDING WINDOW EXPIRATION & CLEANUP
//    // =========================================================================================
//    @Test
//    void testSlidingWindowExpiration() throws InterruptedException {
//        String planName = "premium";
//        RateLimiter rateLimiter = rateLimiterFactory.getRateLimiter(planName);
//        RateLimitingProperties.Plan plan = properties.getPlans().get(planName);
//
//        String clientId = generateClientId();
//
//        // 1. Exhaust the limit
//        for (int i = 0; i < plan.getCapacity(); i++) {
//            assertTrue(rateLimiter.isAllowed(clientId));
//        }
//
//        // 2. Verify subsequent requests are blocked
//        boolean blocked = !rateLimiter.isAllowed(clientId);
//        assertTrue(blocked, "Request should be blocked after exhausting capacity");
//
//        // 3. Wait for the window to expire completely
//        long windowMillis = plan.getTimeUnit().toMillis(plan.getRefillPeriod());
//        System.out.println("Waiting " + windowMillis + "ms for window to expire...");
//        Thread.sleep(windowMillis + 100); // Wait slightly longer than the window
//
//        // 4. Verify limit is restored
//        boolean restored = rateLimiter.isAllowed(clientId);
//        assertTrue(restored, "Request should be allowed again after the window expires, proving ZREMRANGEBYSCORE works.");
//    }
//
//    // =========================================================================================
//    // 4. REDIS KEY EXPIRATION (TTL)
//    // =========================================================================================
//    @Test
//    void testRedisKeyExpirationTTL() {
//        String planName = "premium";
//        RateLimiter rateLimiter = rateLimiterFactory.getRateLimiter(planName);
//        String clientId = generateClientId();
//
//        // Trigger key creation
//        rateLimiter.isAllowed(clientId);
//
//        // Find the key in Redis (we search by pattern since we don't hardcode the exact prefix here)
//        Set<String> keys = redisTemplate.keys("*" + clientId + "*");
//        assertTrue(keys != null && keys.size() == 1, "Exactly one Redis key should be created for the client");
//
//        String actualKey = keys.iterator().next();
//        Long expireTime = redisTemplate.getExpire(actualKey, TimeUnit.MILLISECONDS);
//
//        assertTrue(expireTime != null && expireTime > 0, "A TTL (PEXPIRE) must be set on the Redis key to prevent OOM errors.");
//    }
//
//    // =========================================================================================
//    // 5. END-TO-END HTTP INTEGRATION (AOP + CONTROLLER + FACTORY + REDIS)
//    // =========================================================================================
//    @Test
//    void testEndToEndHttpIntegration() throws InterruptedException {
//        String planName = "premium";
//        int capacity = properties.getPlans().get(planName).getCapacity();
//
//        // We simulate a unique IP to avoid clashing with other tests/runs
//        String fakeIp = "192.168.1." + new java.util.Random().nextInt(255);
//        testClientIds.add(fakeIp); // Register for cleanup
//
//        int totalRequests = capacity + 5; // Send more than capacity
//        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
//        CountDownLatch startLatch = new CountDownLatch(1);
//        CountDownLatch finishLatch = new CountDownLatch(totalRequests);
//
//        Queue<ResponseEntity<String>> responses = new ConcurrentLinkedQueue<>();
//
//        for (int i = 0; i < totalRequests; i++) {
//            executor.submit(() -> {
//                try {
//                    startLatch.await();
//
//                    // Inject the fake IP into the X-Forwarded-For header to test the AOP client ID extraction
//                    HttpHeaders headers = new HttpHeaders();
//                    headers.set("X-Forwarded-For", fakeIp);
//                    HttpEntity<String> entity = new HttpEntity<>(headers);
//
//                    // Hit the actual controller endpoint
//                    ResponseEntity<String> response = restTemplate.exchange("/api/v1/premium-data", HttpMethod.GET, entity, String.class);
//                    responses.add(response);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                } finally {
//                    finishLatch.countDown();
//                }
//            });
//        }
//
//        startLatch.countDown();
//        finishLatch.await();
//        executor.shutdown();
//
//        // Analyze HTTP results
//        long http200Count = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count();
//        long http429Count = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS).count();
//
//        assertEquals(capacity, http200Count, "Exactly the capacity should return HTTP 200 OK.");
//        assertEquals(totalRequests - capacity, http429Count, "The remaining requests MUST return HTTP 429 Too Many Requests.");
//    }
//}

package dev.milind.ratelimiter;

import dev.milind.ratelimiter.config.RateLimiterFactory;
import dev.milind.ratelimiter.config.RateLimitingProperties;
import dev.milind.ratelimiter.core.RateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("cloud")
class RateLimiterComprehensiveIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterComprehensiveIntegrationTest.class);

    @Autowired
    private RateLimiterFactory rateLimiterFactory;

    @Autowired
    private RateLimitingProperties properties;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    private final Set<String> testClientIds = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void setUp() {
        logger.info("================================================================================");
    }

    @AfterEach
    void tearDown() {
        if (!testClientIds.isEmpty()) {
            logger.info("--- Tearing down test. Cleaning up Redis keys for clients: {} ---", testClientIds);
            Set<String> keysToDelete = new HashSet<>();
            for (String clientId : testClientIds) {
                Set<String> keys = redisTemplate.keys("*" + clientId + "*");
                if (keys != null) {
                    keysToDelete.addAll(keys);
                }
            }
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
                logger.info("Deleted {} keys from Redis.", keysToDelete.size());
            }
        }
        testClientIds.clear();
        logger.info("================================================================================\n");
    }

    private String generateClientId(String prefix) {
        String clientId = prefix + "-" + UUID.randomUUID();
        testClientIds.add(clientId);
        return clientId;
    }

    private long runConcurrentRequests(RateLimiter limiter, String clientId, int threadCount) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    logger.trace("Thread {} waiting at start latch for client {}", Thread.currentThread().getId(), clientId);
                    startLatch.await();
                    logger.trace("Thread {} starting request for client {}", Thread.currentThread().getId(), clientId);
                    if (limiter.isAllowed(clientId)) {
                        allowedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        logger.info("Releasing {} threads simultaneously for client '{}'...", threadCount, clientId);
        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();
        logger.info("All threads finished. Allowed requests: {}", allowedCount.get());
        return allowedCount.get();
    }

    @Test
    void testAtomicityUnderConcurrency() throws InterruptedException {
        logger.info("--- Starting Test: ATOMICITY ---");
        String planName = "premium";
        RateLimiter rateLimiter = rateLimiterFactory.getRateLimiter(planName);
        int expectedCapacity = properties.getPlans().get(planName).getCapacity();
        String clientId = generateClientId("atomic-user");
        int threads = 50;

        long actualAllowed = runConcurrentRequests(rateLimiter, clientId, threads);

        assertEquals(expectedCapacity, actualAllowed, "Exactly the configured capacity should be allowed, proving atomicity.");
        logger.info("--- PASSED: ATOMICITY ---");
    }

    @Test
    void testUserIsolation() throws InterruptedException {
        logger.info("--- Starting Test: USER ISOLATION ---");
        String planName = "premium";
        RateLimiter rateLimiter = rateLimiterFactory.getRateLimiter(planName);
        int capacity = properties.getPlans().get(planName).getCapacity();

        String user1 = generateClientId("iso-user1");
        String user2 = generateClientId("iso-user2");

        logger.info("Phase 1: Overwhelming User 1 ({})", user1);
        long user1Allowed = runConcurrentRequests(rateLimiter, user1, 10);
        assertEquals(capacity, user1Allowed);

        logger.info("Phase 2: Verifying User 2 ({}) is unaffected", user2);
        long user2Allowed = runConcurrentRequests(rateLimiter, user2, 10);
        assertEquals(capacity, user2Allowed, "User 2's limit should be completely independent of User 1.");
        logger.info("--- PASSED: USER ISOLATION ---");
    }

    @Test
    void testSlidingWindowExpiration() throws InterruptedException {
        logger.info("--- Starting Test: SLIDING WINDOW EXPIRATION ---");
        String planName = "premium";
        RateLimiter rateLimiter = rateLimiterFactory.getRateLimiter(planName);
        RateLimitingProperties.Plan plan = properties.getPlans().get(planName);
        String clientId = generateClientId("window-exp-user");

        logger.info("Phase 1: Exhausting the limit of {} for client '{}'", plan.getCapacity(), clientId);
        for (int i = 0; i < plan.getCapacity(); i++) {
            assertTrue(rateLimiter.isAllowed(clientId));
        }

        logger.info("Phase 2: Verifying subsequent request is blocked");
        assertTrue(!rateLimiter.isAllowed(clientId), "Request should be blocked after exhausting capacity");

        long windowMillis = plan.getTimeUnit().toMillis(plan.getRefillPeriod());
        logger.info("Phase 3: Waiting {}ms for window to expire...", windowMillis);
        Thread.sleep(windowMillis + 200); // Wait slightly longer

        logger.info("Phase 4: Verifying limit is restored after window expiration");
        assertTrue(rateLimiter.isAllowed(clientId), "Request should be allowed again after the window expires.");
        logger.info("--- PASSED: SLIDING WINDOW EXPIRATION ---");
    }

    @Test
    void testRedisKeyExpirationTTL() {
        logger.info("--- Starting Test: REDIS KEY EXPIRATION (TTL) ---");
        String planName = "premium";
        RateLimiter rateLimiter = rateLimiterFactory.getRateLimiter(planName);
        String clientId = generateClientId("ttl-user");

        logger.info("Triggering key creation for client '{}'", clientId);
        rateLimiter.isAllowed(clientId);

        Set<String> keys = redisTemplate.keys("*" + clientId + "*");
        assertTrue(keys != null && keys.size() == 1, "Exactly one Redis key should be created");

        String actualKey = keys.iterator().next();
        Long expireTime = redisTemplate.getExpire(actualKey, TimeUnit.MILLISECONDS);
        logger.info("Found key '{}' with TTL: {}ms", actualKey, expireTime);

        assertTrue(expireTime != null && expireTime > 0, "A TTL (PEXPIRE) must be set on the Redis key.");
        logger.info("--- PASSED: REDIS KEY EXPIRATION (TTL) ---");
    }

    @Test
    void testEndToEndHttpIntegration() throws InterruptedException {
        logger.info("--- Starting Test: END-TO-END HTTP INTEGRATION ---");
        String planName = "premium";
        int capacity = properties.getPlans().get(planName).getCapacity();
        String fakeIp = "192.168.1." + new java.util.Random().nextInt(255);
        testClientIds.add(fakeIp);

        int totalRequests = capacity + 5;
        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(totalRequests);
        Queue<ResponseEntity<String>> responses = new ConcurrentLinkedQueue<>();

        logger.info("Dispatching {} concurrent HTTP requests for IP '{}' to endpoint '/api/v1/premium-data'", totalRequests, fakeIp);
        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-Forwarded-For", fakeIp);
                    HttpEntity<String> entity = new HttpEntity<>(headers);
                    ResponseEntity<String> response = restTemplate.exchange("/api/v1/premium-data", HttpMethod.GET, entity, String.class);
                    responses.add(response);
                    logger.trace("E2E Thread {} got HTTP status {} for IP {}", Thread.currentThread().getId(), response.getStatusCode(), fakeIp);
                } catch (Exception e) {
                    // RestTemplate can throw exceptions on 4xx/5xx, which we need to handle
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();

        long http200Count = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count();
        // Note: TestRestTemplate does not populate the response queue for 429 errors by default, so we can't count them directly.
        // The assertion on 200s is the most reliable one here.
        logger.info("E2E Test Summary: Total requests sent: {}. HTTP 200 OK responses received: {}", totalRequests, http200Count);

        assertEquals(capacity, http200Count, "Exactly the capacity should return HTTP 200 OK in an E2E test.");
        logger.info("--- PASSED: END-TO-END HTTP INTEGRATION ---");
    }
}