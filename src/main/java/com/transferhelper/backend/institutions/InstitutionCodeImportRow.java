package com.transferhelper.backend.institutions;

public record InstitutionCodeImportRow(
		Long code,
		String schoolName,
		Integer category,
		Boolean isCommunityCollege
) {
}
