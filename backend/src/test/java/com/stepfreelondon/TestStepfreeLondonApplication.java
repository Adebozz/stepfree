package com.stepfreelondon;

import org.springframework.boot.SpringApplication;

public class TestStepfreeLondonApplication {

	public static void main(String[] args) {
		SpringApplication.from(StepfreeLondonApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
