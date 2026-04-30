package com.transferhelper.backend.assist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
class AssistAgreementControllerTests {

	@Test
	void agreementEndpoint_returnsPayload() {
		AssistAgreementService assistAgreementService = mock(AssistAgreementService.class);
		AssistAgreementController controller = new AssistAgreementController(assistAgreementService);

		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("year", 76);
		meta.put("to_id", 12);
		meta.put("from_id", 133);

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("meta", meta);
		payload.put("summary", Map.of("total_rows", 10));

		when(assistAgreementService.fetchSinglePair(76, 12L, 133L)).thenReturn(payload);

		Map<String, Object> result = controller.getSingleAgreement(76, 12L, 133L);

		@SuppressWarnings("unchecked")
		Map<String, Object> resultMeta = (Map<String, Object>) result.get("meta");
		@SuppressWarnings("unchecked")
		Map<String, Object> resultSummary = (Map<String, Object>) result.get("summary");

		assertEquals(76, resultMeta.get("year"));
		assertEquals(12, resultMeta.get("to_id"));
		assertEquals(133, resultMeta.get("from_id"));
		assertEquals(10, resultSummary.get("total_rows"));
	}

	@Test
	void agreementEndpoint_whenServiceFails_throwsFromControllerMethod() {
		AssistAgreementService assistAgreementService = mock(AssistAgreementService.class);
		AssistAgreementController controller = new AssistAgreementController(assistAgreementService);

		when(assistAgreementService.fetchSinglePair(76, 12L, 133L))
				.thenThrow(new IllegalArgumentException("ASSIST request failed"));

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> controller.getSingleAgreement(76, 12L, 133L));
		assertEquals("ASSIST request failed", ex.getMessage());
	}

	@Test
	void handleError_returnsJsonErrorMap() {
		AssistAgreementService assistAgreementService = mock(AssistAgreementService.class);
		AssistAgreementController controller = new AssistAgreementController(assistAgreementService);

		Map<String, String> error = controller.handleError(new IllegalArgumentException("bad gateway"));
		assertEquals("bad gateway", error.get("error"));
	}
}
