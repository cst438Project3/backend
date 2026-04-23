package com.transferhelper.backend;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackendUnitTests {

	@Test
	@Tag("unit")
	void junitUnitTestIsConfigured() {
		assertEquals(4, 2 + 2);
	}
}
