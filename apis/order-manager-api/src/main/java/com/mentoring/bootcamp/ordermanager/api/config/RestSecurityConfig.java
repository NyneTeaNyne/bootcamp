package com.mentoring.bootcamp.ordermanager.api.config;

import com.mentoring.bootcamp.ordermanager.common.security.DelegatingSecurityExceptionHandler;
import com.mentoring.bootcamp.ordermanager.common.security.JwtRoles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Three security filter chains, tried in order:
 * <ol>
 *     <li>the OAuth2 Authorization Server endpoints (/oauth2/token, /oauth2/authorize, ...);</li>
 *     <li>our REST API, protected by JWT;</li>
 *     <li>everything else (login page, Swagger UI, API docs), open.</li>
 * </ol>
 * The matchers do not include "/api/v1": it is the servlet context path, which Spring Security does not see.
 */
@Configuration
public class RestSecurityConfig {

    /** Every REST resource of the API. Add new controllers' paths here to protect them. */
    private static final String[] API_PATHS = {"/users/**", "/items/**", "/orders/**", "/ping/**", "/auth/**"};

    // Both provided by the common module
    private final DelegatingSecurityExceptionHandler securityExceptionHandler;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    public RestSecurityConfig(DelegatingSecurityExceptionHandler securityExceptionHandler,
                              JwtAuthenticationConverter jwtAuthenticationConverter) {
        this.securityExceptionHandler = securityExceptionHandler;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain authServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, authorizationServer ->
                        authorizationServer.oidc(Customizer.withDefaults())) // Support for OpenID Connect
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(API_PATHS)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Sign-up stays open, otherwise nobody could ever get an account to log in with
                        .requestMatchers(HttpMethod.POST, "/users").permitAll()
                        .requestMatchers("/auth/**", "/ping/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(securityExceptionHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler))
                .build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .formLogin(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }

    /**
     * Injects the user's roles into the "roles" claim when an access token is issued
     * (the claim the APIs read, see {@link JwtRoles}).
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                Set<String> roles = context.getPrincipal().getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).filter(Objects::nonNull)
                        // Spring Security 7 also adds factor authorities (e.g. FACTOR_PASSWORD): keep roles only
                        .filter(authority -> authority.startsWith(JwtRoles.ROLE_PREFIX))
                        .collect(Collectors.toSet());
                context.getClaims().claim(JwtRoles.CLAIM, roles);
            }
        };
    }
}
