package com.transferhelper.backend.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AppAuthProperties {

	private String jwtSecret;
	private String jwtIssuer = "transferhelper-backend";
	private long tokenTtlMinutes = 1440;
	private String successRedirectUri = "http://localhost:8081/auth/callback";
	private List<String> allowedRedirectUris = new ArrayList<>();

	public String getJwtSecret() {
		return jwtSecret;
	}

	public void setJwtSecret(String jwtSecret) {
		this.jwtSecret = jwtSecret;
	}

	public String getJwtIssuer() {
		return jwtIssuer;
	}

	public void setJwtIssuer(String jwtIssuer) {
		this.jwtIssuer = jwtIssuer;
	}

	public long getTokenTtlMinutes() {
		return tokenTtlMinutes;
	}

	public void setTokenTtlMinutes(long tokenTtlMinutes) {
		this.tokenTtlMinutes = tokenTtlMinutes;
	}

	public String getSuccessRedirectUri() {
		return successRedirectUri;
	}

	public void setSuccessRedirectUri(String successRedirectUri) {
		this.successRedirectUri = successRedirectUri;
	}

	public List<String> getAllowedRedirectUris() {
		return allowedRedirectUris;
	}

	public void setAllowedRedirectUris(List<String> allowedRedirectUris) {
		this.allowedRedirectUris = allowedRedirectUris;
	}

	public boolean isAllowedRedirectUri(String redirectUri) {
		if (redirectUri == null || redirectUri.isBlank()) {
			return false;
		}
		return allAllowedRedirectUris().contains(redirectUri);
	}

	public List<String> allAllowedRedirectUris() {
		List<String> allowed = new ArrayList<>();
		addIfPresent(allowed, successRedirectUri);
		for (String redirectUri : allowedRedirectUris) {
			if (redirectUri == null) {
				continue;
			}
			Arrays.stream(redirectUri.split(","))
					.map(String::trim)
					.filter(value -> !value.isBlank())
					.forEach(allowed::add);
		}
		return allowed;
	}

	private static void addIfPresent(List<String> values, String value) {
		if (value != null && !value.isBlank()) {
			values.add(value.trim());
		}
	}
}
