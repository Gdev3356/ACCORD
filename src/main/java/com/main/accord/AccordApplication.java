package com.main.accord;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.core.userdetails.UserDetailsService;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class AccordApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccordApplication.class, args);
	}

	@Bean
	public UserDetailsService userDetailsService() {
		// Satisfies Spring Security's requirement — actual auth is handled by JwtAuthFilter
		return username -> { throw new UnsupportedOperationException("Use JWT auth"); };
	}

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}
}