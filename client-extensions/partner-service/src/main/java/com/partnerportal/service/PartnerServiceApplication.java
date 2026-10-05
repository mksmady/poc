package com.partnerportal.service;

import com.liferay.client.extension.util.spring.boot.ClientExtensionUtilSpringBootComponentScan;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@Import(ClientExtensionUtilSpringBootComponentScan.class)
@SpringBootApplication
public class PartnerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PartnerServiceApplication.class, args);
	}

	/**
	 * Every time-dependent rule (expiry, reminders, SLA) reads this clock so
	 * tests can move it.
	 */
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
