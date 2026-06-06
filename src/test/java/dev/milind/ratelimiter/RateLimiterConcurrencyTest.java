package dev.milind.ratelimiter;

import dev.milind.ratelimiter.config.RateLimiterFactory;
import dev.milind.ratelimiter.core.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Concurrency test to verify the atomicity and thread-safety of our rate limiters.
 * We simulate a "thundering herd" of requests hitting the limiter at the exact same time.
 */
@SpringBootTest
@ActiveProfiles("cloud") // Use the 'cloud' profile to test against the real Redis instance
class RateLimiterConcurrencyTest {

    @Autowired
    private RateLimiterFactory rateLimiterFactory;

    @Test
    void when_multipleThreadsRequestAccess_then_respectsSlidingWindowLimit() throws InterruptedException {
        // --- Test Parameters ---
        final int totalRequests = 100; // 100 users trying to get access
        final String planName = "premium"; // Using the plan defined in your application.yaml
        final int expectedAllowed = 10; // The capacity of the "premium" plan

        final String clientId = "concurrent-user-sliding";

        // --- Test Setup ---
        final RateLimiter rateLimiter = rateLimiterFactory.getRateLimiter(planName);
        final ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        final CountDownLatch startLatch = new CountDownLatch(1); // Gate to start all threads at once
        final CountDownLatch finishLatch = new CountDownLatch(totalRequests); // Gate to wait for all threads to finish
        final Queue<Boolean> results = new ConcurrentLinkedQueue<>(); // Thread-safe queue to store results

        // --- Create and Run Threads ---
        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    // Wait until the start signal is given
                    startLatch.await();

                    // The core action: ask for permission
                    boolean allowed = rateLimiter.isAllowed(clientId);
                    results.add(allowed);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    // Signal that this thread has finished
                    finishLatch.countDown();
                }
            });
        }

        // --- Execute Test ---
        // This opens the gate, and all 100 threads will rush to call isAllowed()
        startLatch.countDown();

        // Wait for all threads to complete their work
        finishLatch.await();

        // --- Assert Results ---
        long actualAllowed = results.stream().filter(Boolean::booleanValue).count();

        System.out.printf("Concurrency Test Results for '%s':\n", planName);
        System.out.printf("Total Requests: %d\n", totalRequests);
        System.out.printf("Expected Allowed: %d\n", expectedAllowed);
        System.out.printf("Actual Allowed: %d\n", actualAllowed);

        assertEquals(expectedAllowed, actualAllowed, "The number of allowed requests should match the plan's capacity under concurrent load.");

        executor.shutdown();
    }
}