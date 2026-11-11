package com.pucp.skillb_ia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SkillbIaApplication {

	public static void main(String[] args) {
		SpringApplication.run(SkillbIaApplication.class, args);
	}

}