package com.mentoring.bootcamp.ordermanager.api.tests.integration.controllers;

import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateUserRequest;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.UserRepository;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.JwtTestTokens;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.PostgresContextInitializer;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.SmtpContextInitializer;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.ValkeyContextInitializer;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ContextConfiguration(initializers = {PostgresContextInitializer.class, SmtpContextInitializer.class,
        ValkeyContextInitializer.class})
@AutoConfigureMockMvc
@EnabledIf("com.mentoring.bootcamp.ordermanager.api.tests.integration.config.PostgresContextInitializer#isDockerAvailable")
@Transactional
class SecurityIntegrationTest {

    private static final String USERNAME = "security-test-user";
    private static final String PASSWORD = "Str0ngPassw0rd!";
    // PKCE secret kept by the client; only its SHA-256 (the "challenge") is sent with the first request
    private static final String CODE_VERIFIER = "a-random-code-verifier-of-at-least-43-characters-long";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Value("${spring.security.oauth2.client-id}")
    private String clientId;
    @Value("${spring.security.oauth2.client-secret}")
    private String clientSecret;
    @Value("${spring.security.oauth2.redirect-uri}")
    private String redirectUri;
    @Value("${spring.security.oauth2.swagger-client-id}")
    private String swaggerClientId;
    @Value("${spring.security.oauth2.swagger-redirect-uri}")
    private String swaggerRedirectUri;

    @Test
    void should_return_401_when_no_token_is_sent() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.detail").value("A valid access token is required"));
    }

    @Test
    void should_return_401_when_token_is_invalid() throws Exception {
        mockMvc.perform(get("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("A valid access token is required"));
    }

    @Test
    void should_accept_a_signed_token_with_roles() throws Exception {
        mockMvc.perform(get("/ping").header(HttpHeaders.AUTHORIZATION, bearer(JwtTestTokens.adminToken(jwkSource))))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    void should_return_403_on_ping_when_token_has_no_role() throws Exception {
        String token = bearer(JwtTestTokens.token(jwkSource, "nobody", null));

        mockMvc.perform(get("/ping").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You are not allowed to perform this operation"));
        // Other resources only require being authenticated
        mockMvc.perform(get("/orders").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
    }

    @Test
    void should_let_anyone_sign_up_and_never_return_the_password() throws Exception {
        signUp()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.password").doesNotExist());

        String stored = userRepository.findByUsername(USERNAME).orElseThrow().getPassword();
        assertThat(stored).isNotEqualTo(PASSWORD).startsWith("$2"); // BCrypt hash
    }

    @Test
    void should_keep_swagger_and_api_docs_public() throws Exception {
        mockMvc.perform(get("/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void should_log_in_only_with_the_right_password() throws Exception {
        signUp().andExpect(status().isCreated());

        mockMvc.perform(formLogin("/login").user(USERNAME).password("wrong-password"))
                .andExpect(unauthenticated());
        mockMvc.perform(formLogin("/login").user(USERNAME).password(PASSWORD))
                .andExpect(authenticated().withUsername(USERNAME).withRoles("ADMIN"));
    }

    @Test
    void should_land_on_a_welcome_page_after_a_direct_login() throws Exception {
        signUp().andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(formLogin("/login").user(USERNAME).password(PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andReturn();

        mockMvc.perform(get("/").session((MockHttpSession) login.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Successfully connected")))
                .andExpect(content().string(containsString(USERNAME)));
    }

    @Test
    void should_say_not_connected_on_the_welcome_page_without_login() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Not connected")))
                .andExpect(content().string(containsString("href=\"login\"")));
    }

    @Test
    void should_escape_the_username_on_the_welcome_page() throws Exception {
        String dangerous = "<script>alert(1)</script>";
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CreateUserRequest.builder().username(dangerous).password(PASSWORD).build())))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(formLogin("/login").user(dangerous).password(PASSWORD))
                .andExpect(authenticated())
                .andReturn();

        mockMvc.perform(get("/").session((MockHttpSession) login.getRequest().getSession(false)))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString(dangerous))));
    }

    @Test
    void should_redirect_to_login_when_asking_for_a_code_without_being_logged_in() throws Exception {
        mockMvc.perform(get("/oauth2/authorize")
                        .accept(MediaType.TEXT_HTML) // A browser, as opened by Bruno
                        .queryParam("response_type", "code")
                        .queryParam("client_id", clientId)
                        .queryParam("scope", "read")
                        .queryParam("redirect_uri", redirectUri)
                        .queryParam("code_challenge", codeChallenge(CODE_VERIFIER))
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/login")));
    }

    /**
     * The whole flow Bruno goes through: log in, get an authorization code, exchange it for a token, call the API.
     */
    @Test
    void should_issue_a_token_with_roles_through_the_authorization_code_flow() throws Exception {
        signUp().andExpect(status().isCreated());

        // 1. Log in with the login form
        MvcResult login = mockMvc.perform(formLogin("/login").user(USERNAME).password(PASSWORD))
                .andExpect(authenticated())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        // 2. Ask for an authorization code: the server redirects to the client with ?code=...
        MvcResult authorize = mockMvc.perform(get("/oauth2/authorize")
                        .session(session)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", clientId)
                        .queryParam("scope", "read")
                        .queryParam("redirect_uri", redirectUri)
                        .queryParam("state", "some-state")
                        // PKCE: required by Spring Security 7 for the authorization code flow
                        .queryParam("code_challenge", codeChallenge(CODE_VERIFIER))
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String code = UriComponentsBuilder.fromUriString(authorize.getResponse().getRedirectedUrl())
                .build().getQueryParams().getFirst("code");
        assertThat(code).isNotBlank();

        // 3. Exchange the code for an access token (the client authenticates with its id and secret)
        MvcResult tokenResponse = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(clientId, clientSecret))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", redirectUri)
                        .param("code_verifier", CODE_VERIFIER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refresh_token").exists())
                .andReturn();
        String accessToken = objectMapper.readTree(tokenResponse.getResponse().getContentAsString())
                .path("access_token").asString();

        // 4. The token carries our custom "roles" claim...
        JsonNode claims = objectMapper.readTree(
                Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        assertThat(claims.path("sub").asString()).isEqualTo(USERNAME);
        assertThat(claims.path("roles").valueStream().map(JsonNode::asString).toList()).containsExactly("ROLE_ADMIN");

        // 5. ...and opens the API, including the role-protected ping
        mockMvc.perform(get("/ping").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk());
    }

    /**
     * Swagger UI is a public client: no secret, the token request only carries its id and the PKCE verifier.
     */
    @Test
    void should_let_swagger_ui_log_in_without_client_secret() throws Exception {
        signUp().andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(formLogin("/login").user(USERNAME).password(PASSWORD)).andReturn();

        MvcResult authorize = mockMvc.perform(get("/oauth2/authorize")
                        .session((MockHttpSession) login.getRequest().getSession(false))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", swaggerClientId)
                        .queryParam("scope", "read")
                        .queryParam("redirect_uri", swaggerRedirectUri)
                        .queryParam("code_challenge", codeChallenge(CODE_VERIFIER))
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith(swaggerRedirectUri + "?code=")))
                .andReturn();
        String code = UriComponentsBuilder.fromUriString(authorize.getResponse().getRedirectedUrl())
                .build().getQueryParams().getFirst("code");

        MvcResult tokenResponse = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", swaggerClientId)
                        .param("code", code)
                        .param("redirect_uri", swaggerRedirectUri)
                        .param("code_verifier", CODE_VERIFIER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refresh_token").doesNotExist()) // Public clients get no refresh token
                .andReturn();
        String accessToken = objectMapper.readTree(tokenResponse.getResponse().getContentAsString())
                .path("access_token").asString();

        mockMvc.perform(get("/ping").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk());
    }

    @Test
    void should_refuse_a_swagger_ui_login_without_pkce() throws Exception {
        signUp().andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(formLogin("/login").user(USERNAME).password(PASSWORD)).andReturn();

        mockMvc.perform(get("/oauth2/authorize")
                        .session((MockHttpSession) login.getRequest().getSession(false))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", swaggerClientId)
                        .queryParam("scope", "read")
                        .queryParam("redirect_uri", swaggerRedirectUri))
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("error=invalid_request")));
    }

    private ResultActions signUp() throws Exception {
        return mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        CreateUserRequest.builder().username(USERNAME).password(PASSWORD).build())));
    }

    private static String codeChallenge(String verifier) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
