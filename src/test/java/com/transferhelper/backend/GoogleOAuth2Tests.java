package com.transferhelper.backend.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class GoogleOAuth2Tests {

	@org.springframework.beans.factory.annotation.Autowired
	private WebApplicationContext wac;

	@org.springframework.beans.factory.annotation.Autowired
	private ClientRegistrationRepository clientRegistrationRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUpMockMvc() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac)
				.apply(springSecurity())
				.build();
	}

	@Test
	void apiMe_unauthenticated_isUnauthorized() throws Exception {
		mockMvc.perform(get("/api/google/oauth"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void apiMe_googleOAuth2_authenticated_returnsUserClaims() throws Exception {
		ClientRegistration google = clientRegistrationRepository.findByRegistrationId("google");
		mockMvc.perform(
						get("/api/google/oauth")
								.with(oauth2Login()
										.clientRegistration(google)
										.attributes(attrs -> {
											attrs.put("email", "angel@example.com");
											attrs.put("name", "Angel");
										}))
				)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("angel@example.com"))
				.andExpect(jsonPath("$.name").value("Angel"));
	}
}
