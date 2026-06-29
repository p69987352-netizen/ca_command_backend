package com.caCommand.caCommand;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableJpaAuditing
public class CaCommandApplication {

	public static void main(String[] args)	 {
		SpringApplication.run(CaCommandApplication.class, args);
	}
}
