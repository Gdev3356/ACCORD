package com.main.accord;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AccordApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccordApplication.class, args);
	}

}
