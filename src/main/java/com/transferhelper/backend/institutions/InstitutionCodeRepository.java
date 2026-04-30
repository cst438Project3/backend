package com.transferhelper.backend.institutions;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionCodeRepository extends JpaRepository<InstitutionCodeEntity, Long> {

	List<InstitutionCodeEntity> findBySchoolNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String schoolName, String code);
}
