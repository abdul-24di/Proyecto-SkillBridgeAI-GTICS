package com.pucp.skillb_ia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class SkillbIaApplication {

	public static final String ZONA_HORARIA = "America/Lima";

	public static void main(String[] args) {
		// El servidor de la nube está en UTC: sin esto las fechas salen 5 horas adelantadas y, desde las
		// 7 p. m. de Perú, LocalDate.now() ya devuelve el día siguiente.
		TimeZone.setDefault(TimeZone.getTimeZone(ZONA_HORARIA));
		SpringApplication.run(SkillbIaApplication.class, args);
	}

}