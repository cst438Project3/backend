package com.transferhelper.backend.institutions;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/institutions", produces = MediaType.APPLICATION_JSON_VALUE)
public class InstitutionCodeController {

	private final InstitutionCodeService service;

	public InstitutionCodeController(InstitutionCodeService service) {
		this.service = service;
	}

	@PostMapping(path = "/sync")
	public InstitutionSyncResponse syncFromAssist() {
		return service.syncFromAssist();
	}

	@GetMapping(path = "/sync")
	public InstitutionSyncResponse syncFromAssistInBrowser() {
		return service.syncFromAssist();
	}

	@PostMapping(path = "/sync/local")
	public InstitutionSyncResponse syncFromLocalFile() {
		return service.syncFromLocalResource();
	}

	@GetMapping(path = "/sync/local")
	public InstitutionSyncResponse syncFromLocalFileInBrowser() {
		return service.syncFromLocalResource();
	}

	@PostMapping(path = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
	public InstitutionSyncResponse importSchoolCodes(@RequestBody List<InstitutionCodeImportRow> rows) {
		return service.importFromSchoolCodes(rows);
	}

	@GetMapping
	public List<InstitutionCodeDto> searchInstitutions(
			@RequestParam(name = "q", required = false) String query,
			@RequestParam(name = "category", required = false) Integer category,
			@RequestParam(name = "isCommunityCollege", required = false) Boolean isCommunityCollege
	) {
		return service.search(query, category, isCommunityCollege);
	}

	@GetMapping(path = "/{id:\\d+}")
	public InstitutionCodeDto getInstitution(@PathVariable("id") Long id) {
		return service.getById(id);
	}

	@GetMapping(path = "/pair")
	public InstitutionPairDto getInstitutionPair(
			@RequestParam("toId") Long toId,
			@RequestParam("fromId") Long fromId
	) {
		return service.getPair(toId, fromId);
	}

	@ResponseStatus(HttpStatus.NOT_FOUND)
	@org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
	public Map<String, String> handleNotFound(IllegalArgumentException ex) {
		return Map.of("error", ex.getMessage());
	}
}
