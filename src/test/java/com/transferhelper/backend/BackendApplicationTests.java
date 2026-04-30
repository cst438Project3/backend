package com.transferhelper.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void homeReturnsHelloWorld() {
		HomeController controller = new HomeController();

		assertEquals("hello world", controller.helloWorld());
	}

}
