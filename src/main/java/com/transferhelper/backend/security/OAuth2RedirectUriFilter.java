package com.transferhelper.backend.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2RedirectUriFilter extends OncePerRequestFilter {

	static final String REDIRECT_URI_SESSION_ATTRIBUTE = "APP_AUTH_REDIRECT_URI";

	private final AppAuthProperties properties;

	public OAuth2RedirectUriFilter(AppAuthProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String redirectUri = request.getParameter("redirect_uri");
		if (isGoogleAuthorizationRequest(request) && redirectUri != null && !redirectUri.isBlank()) {
			if (!properties.isAllowedRedirectUri(redirectUri)) {
				response.sendError(HttpStatus.BAD_REQUEST.value(), "redirect_uri is not allowed");
				return;
			}
			request.getSession(true).setAttribute(REDIRECT_URI_SESSION_ATTRIBUTE, redirectUri);
		}

		filterChain.doFilter(request, response);
	}

	private static boolean isGoogleAuthorizationRequest(HttpServletRequest request) {
		return "GET".equalsIgnoreCase(request.getMethod())
				&& "/oauth2/authorization/google".equals(request.getServletPath());
	}
}
