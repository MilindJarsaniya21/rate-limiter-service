package dev.milind.ratelimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
public class RateLimiterServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RateLimiterServiceApplication.class, args);
	}

}
