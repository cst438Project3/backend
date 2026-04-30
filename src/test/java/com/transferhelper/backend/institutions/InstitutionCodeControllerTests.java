package com.transferhelper.backend.institutions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
class InstitutionCodeControllerTests {

	@Test
	void syncLocal_inBrowser_returnsCounts() {
		InstitutionCodeService institutionCodeService = mock(InstitutionCodeService.class);
		InstitutionCodeController controller = new InstitutionCodeController(institutionCodeService);

		when(institutionCodeService.syncFromLocalResource()).thenReturn(new InstitutionSyncResponse(179, 179));

		InstitutionSyncResponse response = controller.syncFromLocalFileInBrowser();
		assertEquals(179, response.fetchedCount());
		assertEquals(179, response.storedCount());
	}

	@Test
	void pair_returnsTwoInstitutions() {
		InstitutionCodeService institutionCodeService = mock(InstitutionCodeService.class);
		InstitutionCodeController controller = new InstitutionCodeController(institutionCodeService);

		InstitutionCodeDto to = new InstitutionCodeDto(12L, "California State University, Monterey Bay", "12", 0, false);
		InstitutionCodeDto from = new InstitutionCodeDto(133L, "Monterey Peninsula College", "133", 2, true);
		InstitutionPairDto pair = new InstitutionPairDto(12L, 133L, to, from, true);

		when(institutionCodeService.getPair(12L, 133L)).thenReturn(pair);

		InstitutionPairDto response = controller.getInstitutionPair(12L, 133L);
		assertEquals(12L, response.toId());
		assertEquals(133L, response.fromId());
		assertEquals("California State University, Monterey Bay", response.toInstitution().schoolName());
		assertEquals("Monterey Peninsula College", response.fromInstitution().schoolName());
		assertEquals(true, response.validPair());
	}

	@Test
	void getInstitution_whenMissing_throwsThenMapsError() {
		InstitutionCodeService institutionCodeService = mock(InstitutionCodeService.class);
		InstitutionCodeController controller = new InstitutionCodeController(institutionCodeService);

		when(institutionCodeService.getById(999L)).thenThrow(new IllegalArgumentException("Institution code not found: 999"));

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> controller.getInstitution(999L));
		assertEquals("Institution code not found: 999", ex.getMessage());

		Map<String, String> error = controller.handleNotFound(ex);
		assertEquals("Institution code not found: 999", error.get("error"));
	}

	@Test
	void searchInstitutions_passesFiltersThrough() {
		InstitutionCodeService institutionCodeService = mock(InstitutionCodeService.class);
		InstitutionCodeController controller = new InstitutionCodeController(institutionCodeService);

		List<InstitutionCodeDto> expected = List.of(new InstitutionCodeDto(133L, "Monterey Peninsula College", "133", 2, true));
		when(institutionCodeService.search("monterey", 2, true)).thenReturn(expected);

		List<InstitutionCodeDto> response = controller.searchInstitutions("monterey", 2, true);
		assertEquals(1, response.size());
		assertEquals(133L, response.getFirst().id());
	}
}
