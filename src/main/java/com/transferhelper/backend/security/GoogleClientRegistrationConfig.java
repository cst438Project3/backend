package com.transferhelper.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

@Configuration
public class GoogleClientRegistrationConfig {

	@Bean
	ClientRegistrationRepository clientRegistrationRepository() {
		return new InMemoryClientRegistrationRepository(googleClientRegistration());
	}

	static ClientRegistration googleClientRegistration() {
		// Override via environment variables in production.
		String clientId = System.getenv().getOrDefault("GOOGLE_CLIENT_ID", "test-client");
		String clientSecret = System.getenv().getOrDefault("GOOGLE_CLIENT_SECRET", "test-secret");

		return ClientRegistration.withRegistrationId("google")
				.clientId(clientId)
				.clientSecret(clientSecret)
				.scope("openid", "profile", "email")
				.authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
				.tokenUri("https://oauth2.googleapis.com/token")
				.userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
				.userNameAttributeName(IdTokenClaimNames.SUB)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.clientName("Google")
				.build();
	}
}
