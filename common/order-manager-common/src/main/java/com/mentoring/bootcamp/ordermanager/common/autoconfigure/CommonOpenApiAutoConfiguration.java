package com.mentoring.bootcamp.ordermanager.common.autoconfigure;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * For APIs that publish Swagger and are protected by JWT: adds an "Authorize" button to Swagger UI,
 * where you paste an access token so that "Try it out" works.
 */
@AutoConfiguration
@ConditionalOnClass(name = {
        "io.swagger.v3.oas.annotations.OpenAPIDefinition",
        "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter"})
@OpenAPIDefinition(security = @SecurityRequirement(name = CommonOpenApiAutoConfiguration.BEARER_AUTH))
@SecurityScheme(name = CommonOpenApiAutoConfiguration.BEARER_AUTH, type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "JWT")
public class CommonOpenApiAutoConfiguration {
    public static final String BEARER_AUTH = "bearerAuth";
}
