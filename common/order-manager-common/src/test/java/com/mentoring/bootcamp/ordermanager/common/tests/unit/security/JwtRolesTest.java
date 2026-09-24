package com.mentoring.bootcamp.ordermanager.common.tests.unit.security;

import com.mentoring.bootcamp.ordermanager.common.security.JwtRoles;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRolesTest {

    @Test
    void should_turn_the_roles_claim_into_authorities_without_adding_a_prefix() {
        Jwt jwt = jwt(List.of("ROLE_ADMIN", "ROLE_USER"));

        // Spring Security 7 also adds a FACTOR_BEARER authority (multi-factor support): only look at roles
        assertThat(roles(jwt)).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void should_ignore_the_default_scope_claim() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("alice")
                .claim("scope", "read")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThat(JwtRoles.authenticationConverter().convert(jwt).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .noneMatch(authority -> authority.startsWith("SCOPE_") || authority.startsWith("ROLE_"));
    }

    private List<String> roles(Jwt jwt) {
        return JwtRoles.authenticationConverter().convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(JwtRoles.ROLE_PREFIX))
                .toList();
    }

    private Jwt jwt(List<String> roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("alice")
                .claim(JwtRoles.CLAIM, roles)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
