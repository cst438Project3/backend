package com.transferhelper.backend.institutions;

public record InstitutionPairDto(
		Long toId,
		Long fromId,
		InstitutionCodeDto toInstitution,
		InstitutionCodeDto fromInstitution,
		boolean validPair
) {
}
