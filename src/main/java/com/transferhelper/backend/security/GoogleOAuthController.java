package com.transferhelper.backend.security;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GoogleOAuthController {

	@GetMapping(path = {"/api/auth/me", "/api/google/oauth"}, produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Object> currentUser(Authentication authentication) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("authenticated", authentication != null && authentication.isAuthenticated());
		if (authentication == null) {
			return response;
		}

		Object principal = authentication.getPrincipal();
		if (principal instanceof Jwt jwt) {
			putIfPresent(response, "userId", jwt.getSubject());
			putIfPresent(response, "email", jwt.getClaimAsString("email"));
			putIfPresent(response, "name", jwt.getClaimAsString("name"));
			putIfPresent(response, "provider", jwt.getClaimAsString("provider"));
			return response;
		}
		if (principal instanceof OidcUser oidc) {
			putIfPresent(response, "providerSubject", oidc.getSubject());
			putIfPresent(response, "email", oidc.getEmail());
			putIfPresent(response, "name", oidc.getFullName());
			return response;
		}
		if (principal instanceof OAuth2User oauth2) {
			putIfPresent(response, "providerSubject", oauth2.getAttribute("sub"));
			putIfPresent(response, "email", oauth2.getAttribute("email"));
			putIfPresent(response, "name", oauth2.getAttribute("name"));
			return response;
		}
		return response;
	}

	private static void putIfPresent(Map<String, Object> response, String key, Object value) {
		if (value != null) {
			response.put(key, value);
		}
	}
}
