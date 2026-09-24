package com.mentoring.bootcamp.ordermanager.common.tests.unit.security;

import com.mentoring.bootcamp.ordermanager.common.security.DelegatingSecurityExceptionHandler;
import com.mentoring.bootcamp.ordermanager.common.security.SecurityExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SecurityExceptionHandlerTest {

    private final SecurityExceptionHandler handler = new SecurityExceptionHandler();

    @Test
    void should_return_401_asking_for_a_bearer_token() {
        ResponseEntity<ProblemDetail> response =
                handler.handleAuthenticationException(new BadCredentialsException("private details"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(response.getBody().getDetail()).isEqualTo("A valid access token is required");
    }

    @Test
    void should_return_403_when_the_role_is_missing() {
        ProblemDetail problem = handler.handleAccessDeniedException(new AccessDeniedException("private details"));

        assertThat(problem.getStatus()).isEqualTo(403);
        assertThat(problem.getDetail()).isEqualTo("You are not allowed to perform this operation");
    }

    @Test
    void should_forward_security_errors_to_the_mvc_exception_resolver() {
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        DelegatingSecurityExceptionHandler delegating = new DelegatingSecurityExceptionHandler(resolver);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        BadCredentialsException authentication = new BadCredentialsException("no token");
        AccessDeniedException accessDenied = new AccessDeniedException("no role");

        delegating.commence(request, response, authentication);
        delegating.handle(request, response, accessDenied);

        verify(resolver).resolveException(eq(request),
                eq(response), isNull(), eq(authentication));
        verify(resolver).resolveException(eq(request),
                eq(response), isNull(), eq(accessDenied));
    }
}
