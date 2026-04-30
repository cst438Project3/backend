package com.transferhelper.backend.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class AppTokenService {

	private final JwtEncoder jwtEncoder;
	private final AppAuthProperties properties;

	public AppTokenService(JwtEncoder jwtEncoder, AppAuthProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
	}

	public String createToken(AppUser appUser) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(Duration.ofMinutes(properties.getTokenTtlMinutes()));

		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer(properties.getJwtIssuer())
				.issuedAt(now)
				.expiresAt(expiresAt)
				.subject(appUser.getId().toString())
				.claim("email", appUser.getEmail())
				.claim("provider", appUser.getProvider())
				.claim("provider_subject", appUser.getProviderSubject())
				.claim("roles", List.of("USER"));
		if (appUser.getName() != null) {
			claims.claim("name", appUser.getName());
		}

		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}
}
