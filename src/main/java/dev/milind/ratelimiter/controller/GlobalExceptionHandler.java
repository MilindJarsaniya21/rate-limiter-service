package dev.milind.ratelimiter.controller;

import dev.milind.ratelimiter.aop.RateLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitException(RateLimitExceededException ex) {
        Map<String, Object> response = new HashMap<>();

        response.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        response.put("error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        response.put("message", ex.getMessage());

        return new ResponseEntity<>(response, HttpStatus.TOO_MANY_REQUESTS);
    }
}
