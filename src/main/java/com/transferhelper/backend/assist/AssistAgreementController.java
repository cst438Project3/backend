package com.transferhelper.backend.assist;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/assist", produces = MediaType.APPLICATION_JSON_VALUE)
public class AssistAgreementController {

	private final AssistAgreementService assistAgreementService;

	public AssistAgreementController(AssistAgreementService assistAgreementService) {
		this.assistAgreementService = assistAgreementService;
	}

	@GetMapping(path = "/agreement")
	public Map<String, Object> getSingleAgreement(
			@RequestParam("year") int year,
			@RequestParam("toId") long toId,
			@RequestParam("fromId") long fromId
	) {
		return assistAgreementService.fetchSinglePair(year, toId, fromId);
	}

	@ResponseStatus(HttpStatus.BAD_GATEWAY)
	@ExceptionHandler(IllegalArgumentException.class)
	public Map<String, String> handleError(IllegalArgumentException ex) {
		return Map.of("error", ex.getMessage());
	}
}
