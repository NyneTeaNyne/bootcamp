package com.mentoring.bootcamp.ordermanager.common.tests.unit.autoconfigure;

import com.mentoring.bootcamp.ordermanager.common.autoconfigure.CommonMailAutoConfiguration;
import com.mentoring.bootcamp.ordermanager.common.autoconfigure.CommonSecurityAutoConfiguration;
import com.mentoring.bootcamp.ordermanager.common.autoconfigure.CommonWebAutoConfiguration;
import com.mentoring.bootcamp.ordermanager.common.mail.MailService;
import com.mentoring.bootcamp.ordermanager.common.mail.MailUseCase;
import com.mentoring.bootcamp.ordermanager.common.security.DelegatingSecurityExceptionHandler;
import com.mentoring.bootcamp.ordermanager.common.security.SecurityExceptionHandler;
import com.mentoring.bootcamp.ordermanager.common.web.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Checks that each shared building block switches on (or off) depending on what the consuming module uses.
 */
class CommonAutoConfigurationTest {

    private final WebApplicationContextRunner webContext = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonWebAutoConfiguration.class,
                    CommonSecurityAutoConfiguration.class, CommonMailAutoConfiguration.class))
            // Normally provided by Spring MVC
            .withBean("handlerExceptionResolver", HandlerExceptionResolver.class, () -> mock(HandlerExceptionResolver.class));

    @Test
    void should_provide_the_error_handlers_and_jwt_roles_to_a_secured_web_api() {
        webContext.run(context -> {
            assertThat(context).hasSingleBean(ApiExceptionHandler.class);
            assertThat(context).hasSingleBean(SecurityExceptionHandler.class);
            assertThat(context).hasSingleBean(DelegatingSecurityExceptionHandler.class);
            assertThat(context).hasSingleBean(JwtAuthenticationConverter.class);
        });
    }

    @Test
    void should_skip_security_parts_when_the_module_does_not_use_spring_security() {
        webContext.withClassLoader(new FilteredClassLoader(AuthenticationEntryPoint.class, JwtAuthenticationConverter.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ApiExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(SecurityExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(DelegatingSecurityExceptionHandler.class);
                });
    }

    @Test
    void should_skip_web_parts_outside_a_web_application() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonWebAutoConfiguration.class,
                        CommonSecurityAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ApiExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(SecurityExceptionHandler.class);
                });
    }

    @Test
    void should_provide_a_mail_service_when_spring_mail_is_configured() {
        webContext.withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .run(context -> assertThat(context).hasSingleBean(MailUseCase.class));
    }

    @Test
    void should_not_provide_a_mail_service_without_mail_server() {
        webContext.run(context -> assertThat(context).doesNotHaveBean(MailUseCase.class));
    }

    @Test
    void should_let_the_module_replace_a_shared_bean() {
        MailUseCase custom = (to, subject, body) -> { };
        webContext.withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .withBean(MailUseCase.class, () -> custom)
                .run(context -> {
                    assertThat(context).getBean(MailUseCase.class).isSameAs(custom);
                    assertThat(context).doesNotHaveBean(MailService.class);
                });
    }
}
