package com.transferhelper.backend.institutions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "institution_codes")
public class InstitutionCodeEntity {

	@Id
	private Long id;

	@Column(name = "school_name", nullable = false)
	private String schoolName;

	@Column(name = "code", nullable = false)
	private String code;

	@Column(name = "category")
	private Integer category;

	@Column(name = "is_community_college")
	private boolean communityCollege;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getSchoolName() {
		return schoolName;
	}

	public void setSchoolName(String schoolName) {
		this.schoolName = schoolName;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public Integer getCategory() {
		return category;
	}

	public void setCategory(Integer category) {
		this.category = category;
	}

	public boolean isCommunityCollege() {
		return communityCollege;
	}

	public void setCommunityCollege(boolean communityCollege) {
		this.communityCollege = communityCollege;
	}
}
