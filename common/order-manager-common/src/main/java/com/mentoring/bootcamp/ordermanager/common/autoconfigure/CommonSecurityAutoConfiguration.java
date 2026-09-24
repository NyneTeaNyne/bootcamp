package com.mentoring.bootcamp.ordermanager.common.autoconfigure;

import com.mentoring.bootcamp.ordermanager.common.security.DelegatingSecurityExceptionHandler;
import com.mentoring.bootcamp.ordermanager.common.security.JwtRoles;
import com.mentoring.bootcamp.ordermanager.common.security.SecurityExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Active when the module uses Spring Security: security errors in the common JSON format,
 * and, when it validates JWTs, the shared "roles" claim convention.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.security.web.AuthenticationEntryPoint")
public class CommonSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SecurityExceptionHandler securityExceptionHandler() {
        return new SecurityExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public DelegatingSecurityExceptionHandler delegatingSecurityExceptionHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return new DelegatingSecurityExceptionHandler(resolver);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter")
    static class JwtRolesConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
            return JwtRoles.authenticationConverter();
        }
    }
}
