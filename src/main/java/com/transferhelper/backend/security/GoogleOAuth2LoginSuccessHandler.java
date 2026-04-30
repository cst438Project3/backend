package com.transferhelper.backend.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class GoogleOAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
	private final AppUserService appUserService;
	private final AppTokenService appTokenService;
	private final AppAuthProperties properties;

	public GoogleOAuth2LoginSuccessHandler(
			AppUserService appUserService,
			AppTokenService appTokenService,
			AppAuthProperties properties
	) {
		this.appUserService = appUserService;
		this.appTokenService = appTokenService;
		this.properties = properties;
	}

	@Override
	public void onAuthenticationSuccess(
			HttpServletRequest request,
			HttpServletResponse response,
			Authentication authentication
	) throws IOException, ServletException {
		GoogleProfile profile = GoogleProfile.from(authentication.getPrincipal());
		AppUser appUser = appUserService.upsertGoogleUser(
				profile.subject(),
				profile.email(),
				profile.name(),
				profile.avatarUrl()
		);
		String token = appTokenService.createToken(appUser);

		SavedRequest saved = requestCache.getRequest(request, response);
		if (saved != null) {
			response.sendRedirect(saved.getRedirectUrl());
			return;
		}

		String redirectUri = resolveRedirectUri(request);
		if (redirectUri != null) {
			response.sendRedirect(appendToken(redirectUri, token));
			return;
		}

		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"token\":\"" + token + "\",\"token_type\":\"Bearer\"}");
	}

	private String resolveRedirectUri(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			Object redirectUri = session.getAttribute(OAuth2RedirectUriFilter.REDIRECT_URI_SESSION_ATTRIBUTE);
			session.removeAttribute(OAuth2RedirectUriFilter.REDIRECT_URI_SESSION_ATTRIBUTE);
			if (redirectUri instanceof String value && properties.isAllowedRedirectUri(value)) {
				return value;
			}
		}
		return properties.getSuccessRedirectUri();
	}

	private static String appendToken(String redirectUri, String token) {
		String separator = redirectUri.contains("?") ? "&" : "?";
		return redirectUri + separator
				+ "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
				+ "&token_type=Bearer";
	}

	private record GoogleProfile(String subject, String email, String name, String avatarUrl) {

		static GoogleProfile from(Object principal) {
			if (principal instanceof OidcUser oidc) {
				return new GoogleProfile(
						oidc.getSubject(),
						oidc.getEmail(),
						oidc.getFullName(),
						oidc.getPicture()
				);
			}
			if (principal instanceof OAuth2User oauth2) {
				return new GoogleProfile(
						oauth2.getAttribute("sub"),
						oauth2.getAttribute("email"),
						oauth2.getAttribute("name"),
						oauth2.getAttribute("picture")
				);
			}
			throw new IllegalStateException("Unsupported Google OAuth2 principal");
		}
	}
}
