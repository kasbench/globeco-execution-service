package org.kasbench.globeco_execution_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GlobecoExecutionServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(GlobecoExecutionServiceApplication.class, args);
	}

}
