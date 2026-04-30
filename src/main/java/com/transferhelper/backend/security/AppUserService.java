package com.transferhelper.backend.security;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserService {

	private static final String GOOGLE_PROVIDER = "google";

	private final AppUserRepository appUserRepository;

	public AppUserService(AppUserRepository appUserRepository) {
		this.appUserRepository = appUserRepository;
	}

	@Transactional
	public AppUser upsertGoogleUser(String googleSubject, String email, String name, String avatarUrl) {
		if (googleSubject == null || googleSubject.isBlank()) {
			throw new IllegalArgumentException("Google subject is required");
		}
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("Google email is required");
		}

		String normalizedEmail = email.trim().toLowerCase();
		return appUserRepository.findByProviderAndProviderSubject(GOOGLE_PROVIDER, googleSubject)
				.or(() -> appUserRepository.findByEmailIgnoreCase(normalizedEmail))
				.map(existing -> {
					existing.updateProfile(normalizedEmail, name, avatarUrl);
					return existing;
				})
				.orElseGet(() -> appUserRepository.save(
						new AppUser(GOOGLE_PROVIDER, googleSubject, normalizedEmail, name, avatarUrl)
				));
	}
}
