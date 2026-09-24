package com.mentoring.bootcamp.ordermanager.api.tests.integration.config;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts one Mailpit container (fake SMTP server) shared by every integration test and points
 * Spring Mail to it. Mailpit also exposes an HTTP API that tests use to read the emails received.
 */
public class SmtpContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final int SMTP_PORT = 1025;
    private static final int HTTP_PORT = 8025;

    private static GenericContainer<?> smtpContainer;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        GenericContainer<?> container = container();
        TestPropertyValues.of(
                "spring.mail.host=" + container.getHost(),
                "spring.mail.port=" + container.getMappedPort(SMTP_PORT)
        ).applyTo(context.getEnvironment());
    }

    /**
     * Base URL of the Mailpit HTTP API, e.g. {@code http://localhost:32771/api/v1}.
     */
    public static String mailpitApiUrl() {
        GenericContainer<?> container = container();
        return "http://" + container.getHost() + ":" + container.getMappedPort(HTTP_PORT) + "/api/v1";
    }

    // The container is a singleton shared by all tests and deliberately never closed here:
    // Testcontainers' Ryuk container removes it when the test JVM exits.
    @SuppressWarnings("resource")
    private static synchronized GenericContainer<?> container() {
        if (smtpContainer == null) {
            smtpContainer = new GenericContainer<>(DockerImageName.parse("axllent/mailpit"))
                    .withExposedPorts(SMTP_PORT, HTTP_PORT)
                    .waitingFor(Wait.forListeningPort());
            smtpContainer.start();
        }
        return smtpContainer;
    }
}
