package com.transferhelper.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
public class SecurityConfig {

	private final GoogleOAuth2LoginSuccessHandler googleOAuth2LoginSuccessHandler;

	public SecurityConfig(GoogleOAuth2LoginSuccessHandler googleOAuth2LoginSuccessHandler) {
		this.googleOAuth2LoginSuccessHandler = googleOAuth2LoginSuccessHandler;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/**").authenticated()
						.anyRequest().permitAll())
				.oauth2Login(oauth -> oauth.successHandler(googleOAuth2LoginSuccessHandler))
				.build();
	}
}
