package com.mentoring.bootcamp.ordermanager.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lets you log in from Swagger UI: "Authorize" > "oauth2" opens our login page and brings a token back.
 * (The common module already offers "bearerAuth", to paste a token obtained elsewhere.)
 * <p>
 * Open Swagger through 127.0.0.1, not localhost: the authorization server refuses "localhost" redirect URIs.
 */
@Configuration
public class OpenApiConfig {

    public static final String OAUTH2 = "oauth2";

    @Bean
    public OpenApiCustomizer oauth2LoginInSwagger(
            @Value("${bootcamp.openapi.oauth2-base-url:http://127.0.0.1:8080/api/v1}") String baseUrl) {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSecuritySchemes(OAUTH2, new SecurityScheme()
                    .type(SecurityScheme.Type.OAUTH2)
                    .description("Log in with your account (authorization code + PKCE)")
                    .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                            .authorizationUrl(baseUrl + "/oauth2/authorize")
                            .tokenUrl(baseUrl + "/oauth2/token")
                            .scopes(new Scopes().addString("read", "Access the API")))));
            // Either scheme is accepted: a pasted token (bearerAuth) or a Swagger login (oauth2)
            openApi.addSecurityItem(new SecurityRequirement().addList(OAUTH2, "read"));
        };
    }
}
