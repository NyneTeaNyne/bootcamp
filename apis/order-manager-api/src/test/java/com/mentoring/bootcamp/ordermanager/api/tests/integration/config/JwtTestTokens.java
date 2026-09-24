package com.mentoring.bootcamp.ordermanager.api.tests.integration.config;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;
import java.util.Set;

/**
 * Signs access tokens with the application's own key, like the authorization server does.
 * Useful for tests that call the API over real HTTP, where {@code @WithMockUser} does not apply.
 */
public final class JwtTestTokens {

    private JwtTestTokens() {
    }

    public static String adminToken(JWKSource<SecurityContext> jwkSource) {
        return token(jwkSource, "admin", Set.of("ROLE_ADMIN"));
    }

    /**
     * @param roles value of the "roles" claim, or {@code null} to leave the claim out
     */
    public static String token(JWKSource<SecurityContext> jwkSource, String subject, Set<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300));
        if (roles != null) {
            claims.claim("roles", roles);
        }
        return new NimbusJwtEncoder(jwkSource)
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
                .getTokenValue();
    }
}
