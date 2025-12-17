package com.sc1hub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class SC1HubApplicationTests {

	@Test
	void applicationClassLoads() {
		org.junit.jupiter.api.Assertions.assertNotNull(SC1HubApplication.class);
	}

	@Test
	void applicationHasSpringBootApplicationAnnotation() {
		org.junit.jupiter.api.Assertions
				.assertTrue(SC1HubApplication.class.isAnnotationPresent(SpringBootApplication.class));
	}
}
