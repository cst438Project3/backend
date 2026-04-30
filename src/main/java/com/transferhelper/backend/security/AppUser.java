package com.transferhelper.backend.security;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "app_user",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_app_user_provider_subject", columnNames = {"provider", "provider_subject"}),
				@UniqueConstraint(name = "uq_app_user_email", columnNames = "email")
		}
)
public class AppUser {

	@Id
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false)
	private String provider;

	@Column(name = "provider_subject", nullable = false)
	private String providerSubject;

	@Column(nullable = false)
	private String email;

	private String name;

	@Column(name = "avatar_url")
	private String avatarUrl;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected AppUser() {
	}

	public AppUser(String provider, String providerSubject, String email, String name, String avatarUrl) {
		this.provider = provider;
		this.providerSubject = providerSubject;
		this.email = email;
		this.name = name;
		this.avatarUrl = avatarUrl;
	}

	@PrePersist
	void prePersist() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getProvider() {
		return provider;
	}

	public String getProviderSubject() {
		return providerSubject;
	}

	public String getEmail() {
		return email;
	}

	public String getName() {
		return name;
	}

	public String getAvatarUrl() {
		return avatarUrl;
	}

	public void updateProfile(String email, String name, String avatarUrl) {
		this.email = email;
		this.name = name;
		this.avatarUrl = avatarUrl;
	}
}
