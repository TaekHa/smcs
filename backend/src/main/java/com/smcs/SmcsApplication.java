package com.smcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmcsApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmcsApplication.class, args);
	}

}
