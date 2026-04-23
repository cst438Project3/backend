package com.transferhelper.backend.security;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GoogleOAuthController {

	@GetMapping(path = "/api/google/oauth", produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Object> googleOauthMe(@AuthenticationPrincipal Object principal) {
		if (principal instanceof OidcUser oidc) {
			return Map.of(
					"email", oidc.getEmail(),
					"name", oidc.getFullName()
			);
		}
		if (principal instanceof OAuth2User oauth2) {
			return Map.of(
					"email", oauth2.getAttribute("email"),
					"name", oauth2.getAttribute("name")
			);
		}
		return Map.of();
	}
}
