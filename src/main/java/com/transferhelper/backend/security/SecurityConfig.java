package com.transferhelper.backend.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties(AppAuthProperties.class)
public class SecurityConfig {

	private final GoogleOAuth2LoginSuccessHandler googleOAuth2LoginSuccessHandler;
	private final OAuth2RedirectUriFilter oauth2RedirectUriFilter;
	private final String allowedOrigins;

	public SecurityConfig(
			GoogleOAuth2LoginSuccessHandler googleOAuth2LoginSuccessHandler,
			OAuth2RedirectUriFilter oauth2RedirectUriFilter,
			@Value("${app.cors.allowed-origins}") String allowedOrigins
	) {
		this.googleOAuth2LoginSuccessHandler = googleOAuth2LoginSuccessHandler;
		this.oauth2RedirectUriFilter = oauth2RedirectUriFilter;
		this.allowedOrigins = allowedOrigins;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.cors(Customizer.withDefaults())
				.csrf(csrf -> csrf.disable())
				.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/", "/error", "/oauth2/**", "/login/oauth2/**").permitAll()
						.requestMatchers("/api/**").authenticated()
						.anyRequest().permitAll())
				.oauth2Login(oauth -> oauth.successHandler(googleOAuth2LoginSuccessHandler))
				.oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
				.addFilterBefore(oauth2RedirectUriFilter, OAuth2AuthorizationRequestRedirectFilter.class)
				.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(parseCsv(allowedOrigins));
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
		configuration.setExposedHeaders(List.of("Authorization"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	private static List<String> parseCsv(String value) {
		return Arrays.stream(value.split(","))
				.map(String::trim)
				.filter(item -> !item.isBlank())
				.toList();
	}
}
