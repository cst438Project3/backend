package com.transferhelper.backend.institutions;

public record InstitutionCodeDto(
		Long id,
		String schoolName,
		String code,
		Integer category,
		boolean isCommunityCollege
) {
	static InstitutionCodeDto fromEntity(InstitutionCodeEntity entity) {
		return new InstitutionCodeDto(
				entity.getId(),
				entity.getSchoolName(),
				entity.getCode(),
				entity.getCategory(),
				entity.isCommunityCollege()
		);
	}
}
