package com.transferhelper.backend.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	Optional<AppUser> findByProviderAndProviderSubject(String provider, String providerSubject);

	Optional<AppUser> findByEmailIgnoreCase(String email);
}
