package com.mentoring.bootcamp.ordermanager.common.security;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Convention shared by the token issuer and every API that accepts its tokens:
 * the user's roles travel in a "roles" claim, already prefixed (e.g. ROLE_ADMIN).
 */
public final class JwtRoles {

    /** Name of the JWT claim holding the roles. */
    public static final String CLAIM = "roles";

    /** Prefix of the authorities that are roles (as opposed to e.g. FACTOR_PASSWORD). */
    public static final String ROLE_PREFIX = "ROLE_";

    private JwtRoles() {
    }

    /**
     * Makes an API read the "roles" claim instead of the default "scope" claim.
     */
    public static JwtAuthenticationConverter authenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(CLAIM);
        authoritiesConverter.setAuthorityPrefix(""); // Roles are already stored as ROLE_xxx

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
