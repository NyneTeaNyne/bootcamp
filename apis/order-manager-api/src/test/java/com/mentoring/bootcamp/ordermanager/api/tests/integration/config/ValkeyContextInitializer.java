package com.mentoring.bootcamp.ordermanager.api.tests.integration.config;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts one Valkey container (Redis-compatible cache) shared by every integration test.
 */
public class ValkeyContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final int REDIS_PORT = 6379;

    private static GenericContainer<?> valkey;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        GenericContainer<?> container = container();
        TestPropertyValues.of(
                "spring.data.redis.host=" + container.getHost(),
                "spring.data.redis.port=" + container.getMappedPort(REDIS_PORT)
        ).applyTo(context.getEnvironment());
    }

    // The container is a singleton shared by all tests and deliberately never closed here:
    // Testcontainers' Ryuk container removes it when the test JVM exits.
    @SuppressWarnings("resource")
    private static synchronized GenericContainer<?> container() {
        if (valkey == null) {
            valkey = new GenericContainer<>(DockerImageName.parse("valkey/valkey:8-alpine"))
                    .withExposedPorts(REDIS_PORT)
                    .waitingFor(Wait.forListeningPort());
            valkey.start();
        }
        return valkey;
    }
}
