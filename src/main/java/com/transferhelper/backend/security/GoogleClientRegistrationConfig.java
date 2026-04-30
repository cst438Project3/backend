package com.transferhelper.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration
public class GoogleClientRegistrationConfig {

	private final String clientId;
	private final String clientSecret;

	public GoogleClientRegistrationConfig(
			@Value("${app.oauth.google.client-id}") String clientId,
			@Value("${app.oauth.google.client-secret}") String clientSecret
	) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	@Bean
	ClientRegistrationRepository clientRegistrationRepository() {
		return new InMemoryClientRegistrationRepository(googleClientRegistration());
	}

	private ClientRegistration googleClientRegistration() {
		return CommonOAuth2Provider.GOOGLE.getBuilder("google")
				.clientId(clientId)
				.clientSecret(clientSecret)
				.scope("openid", "profile", "email")
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.build();
	}
}
