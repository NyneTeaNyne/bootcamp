package com.mentoring.bootcamp.ordermanager.api.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

/**
 * Bean factory for the OAuth2 setup. Secrets and durations come from application.properties,
 * which reads them from environment variables in production (12-factor app).
 */
@Configuration
@Setter
@ConfigurationProperties(prefix = "spring.security.oauth2")
public class SecurityConfig {

    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String swaggerClientId;
    private String swaggerRedirectUri;
    private long accessTokenValidityMinutes;
    private long refreshTokenValidityDays;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * The applications allowed to ask for tokens, both with the authorization code flow:
     * a confidential client (e.g. Bruno) and Swagger UI.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(PasswordEncoder passwordEncoder) {
        return new InMemoryRegisteredClientRepository(confidentialClient(passwordEncoder), swaggerUiClient());
    }

    /**
     * A tool that can keep a secret (Bruno, Postman...): authenticates with its id and secret.
     */
    private RegisteredClient confidentialClient(PasswordEncoder passwordEncoder) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(clientSecret))
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(redirectUri)
                .scope("read")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(accessTokenValidityMinutes))
                        .refreshTokenTimeToLive(Duration.ofDays(refreshTokenValidityDays))
                        .reuseRefreshTokens(false) // Rotation for enhanced security
                        .build())
                .build();
    }

    /**
     * Swagger UI runs in the browser: anybody could read a secret there, so it is a "public" client
     * without secret, protected by PKCE instead. Public clients get no refresh token.
     */
    private RegisteredClient swaggerUiClient() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(swaggerClientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(swaggerRedirectUri)
                .scope("read")
                .clientSettings(ClientSettings.builder().requireProofKey(true).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(accessTokenValidityMinutes))
                        .build())
                .build();
    }

    /**
     * RSA 2048 key pair: the private key signs the tokens, the public key verifies them.
     * Generated at startup, so tokens do not survive a restart.
     */
    @Bean
    public KeyPair tokenSigningKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(KeyPair tokenSigningKeyPair) {
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) tokenSigningKeyPair.getPublic())
                .privateKey((RSAPrivateKey) tokenSigningKeyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    /**
     * Used by the API (resource server) to check the signature of incoming tokens.
     */
    @Bean
    public JwtDecoder jwtDecoder(KeyPair tokenSigningKeyPair) {
        return NimbusJwtDecoder.withPublicKey((RSAPublicKey) tokenSigningKeyPair.getPublic()).build();
    }
}
