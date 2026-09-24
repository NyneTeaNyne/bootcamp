package com.mentoring.bootcamp.ordermanager.api.tests.integration.controllers;

import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.PostgresContextInitializer;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.SmtpContextInitializer;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.ValkeyContextInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ContextConfiguration(initializers = {PostgresContextInitializer.class, SmtpContextInitializer.class,
        ValkeyContextInitializer.class})
@AutoConfigureMockMvc
@EnabledIf("com.mentoring.bootcamp.ordermanager.api.tests.integration.config.PostgresContextInitializer#isDockerAvailable")
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_publish_the_api_description_with_the_three_groups() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Bootcamp Manager API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"))
                .andExpect(jsonPath("$.tags[*].name", containsInAnyOrder("Orders", "Users", "Items")))
                .andExpect(jsonPath("$.paths['/orders'].post.summary").value("Place a new order"))
                .andExpect(jsonPath("$.paths['/orders'].post.responses['201']").exists())
                // Provided by the common module
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                // Login from Swagger UI
                .andExpect(jsonPath("$.components.securitySchemes.oauth2.flows.authorizationCode.authorizationUrl")
                        .value("http://127.0.0.1:8080/api/v1/oauth2/authorize"))
                .andExpect(jsonPath("$.components.securitySchemes.oauth2.flows.authorizationCode.tokenUrl")
                        .value("http://127.0.0.1:8080/api/v1/oauth2/token"))
                .andExpect(jsonPath("$.security[*].oauth2").exists())
                .andExpect(jsonPath("$.components.schemas.CreateOrderRequest.description")
                        .value("Request object for creating a new order"));
    }

    @Test
    void should_serve_the_swagger_ui() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
