package com.mentoring.bootcamp.ordermanager.api.tests.integration;

import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateUserRequest;
import com.mentoring.bootcamp.ordermanager.api.entities.UserEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.UserRepository;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.JwtTestTokens;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.PostgresContextInitializer;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.ValkeyContextInitializer;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = {PostgresContextInitializer.class, ValkeyContextInitializer.class})
@EnabledIf("com.mentoring.bootcamp.ordermanager.api.tests.integration.config.PostgresContextInitializer#isDockerAvailable")
class BootcampApplicationTests {

	@LocalServerPort
	private int port;

	@Value("${server.servlet.context-path}")
	private String contextPath;

	@MockitoBean
	private UserRepository userRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private JWKSource<SecurityContext> jwkSource;

	@Test
	void contextLoads() {
	}

	@Test
	void userEntityIsManaged() {
		assertEquals(UserEntity.class,
				entityManagerFactory.getMetamodel().entity(UserEntity.class).getJavaType());
	}

	@Test
	void apiRoutesRequireVersionPrefix() throws Exception {
		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpResponse<String> ping = client.send(
					HttpRequest.newBuilder(URI.create("http://localhost:" + port + contextPath + "/ping"))
							.header("Authorization", "Bearer " + JwtTestTokens.adminToken(jwkSource))
							.timeout(Duration.ofSeconds(5)).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertEquals(200, ping.statusCode());
			assertEquals("pong", ping.body());

			HttpResponse<String> unversioned = client.send(
					HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ping"))
							.timeout(Duration.ofSeconds(5)).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertEquals(404, unversioned.statusCode());
		}
	}

	@Test
	void businessErrorsExposeOnlyPublicDetails() throws Exception {
		when(userRepository.existsByUsername("alice")).thenReturn(true);

		HttpResponse<String> response = request("/users?trace=true&message=true", true);
		JsonNode body = objectMapper.readTree(response.body());

		assertEquals(400, response.statusCode());
		assertEquals(400, body.path("status").asInt());
		assertEquals("Username already exists", body.path("detail").asString());
		assertNoInternalDetails(body);
		assertFalse(response.body().contains("BusinessException"));
	}

	@Test
	void unexpectedErrorsNeverExposeInternalDetails() throws Exception {
		when(userRepository.findAll()).thenThrow(new IllegalStateException("private database details"));

		HttpResponse<String> response = request("/users?trace=true&message=true", false);
		JsonNode body = objectMapper.readTree(response.body());

		assertEquals(500, response.statusCode());
		assertEquals(500, body.path("status").asInt());
		assertNoInternalDetails(body);
		assertFalse(response.body().contains("private database details"));
		assertFalse(response.body().contains("IllegalStateException"));
	}

	@Test
	void missingUsersStillReturnNotFoundWithoutInternalDetails() throws Exception {
		HttpResponse<String> response = request("/users/42?trace=true&message=true", false);

		assertEquals(404, response.statusCode());
		JsonNode body = objectMapper.readTree(response.body());
		assertEquals(404, body.path("status").asInt());
		assertEquals("Not Found", body.path("title").asString());
		assertEquals("User not found", body.path("detail").asString());
		assertNoInternalDetails(body);
	}

	private HttpResponse<String> request(String path, boolean createUser) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder(
				URI.create("http://localhost:" + port + contextPath + path))
				.timeout(Duration.ofSeconds(5))
				.header("Accept", "application/json")
				.header("Authorization", "Bearer " + JwtTestTokens.adminToken(jwkSource));
		if (createUser) {
			builder.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
							CreateUserRequest.builder().username("alice").password("Str0ngPassw0rd!").build())));
		} else {
			builder.GET();
		}
		try (HttpClient client = HttpClient.newHttpClient()) {
			return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
		}
	}

	private void assertNoInternalDetails(JsonNode body) {
		assertFalse(body.has("trace"));
		assertFalse(body.has("exception"));
		assertFalse(body.has("message"));
		assertFalse(body.has("errors"));
	}

}
