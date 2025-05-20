package org.kasbench.globeco_execution_service;

import org.springframework.boot.SpringApplication;

public class TestGlobecoExecutionServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(GlobecoExecutionServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
