package com.mentoring.bootcamp.ordermanager.api.tests.integration.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Starts one PostgreSQL container shared by every integration test and injects its
 * dynamic coordinates into the Spring environment before the context starts.
 */
public class PostgresContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresContextInitializer.class);

    private static PostgreSQLContainer postgresDb;
    private static Boolean dockerAvailable;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        PostgreSQLContainer container = container();
        TestPropertyValues.of(
                "spring.datasource.url=" + container.getJdbcUrl(),
                "spring.datasource.username=" + container.getUsername(),
                "spring.datasource.password=" + container.getPassword()
        ).applyTo(context.getEnvironment());
    }

    /**
     * Used by {@code @EnabledIf} so integration tests are skipped instead of failing when Docker is not running.
     * Says why when Docker cannot be reached: a silent skip is easy to miss.
     */
    public static synchronized boolean isDockerAvailable() {
        if (dockerAvailable == null) {
            try {
                DockerClientFactory.instance().client();
                dockerAvailable = true;
            } catch (RuntimeException exception) {
                LOGGER.warn("Docker is not reachable by Testcontainers, integration tests are SKIPPED: {}"
                        + " (see README > Dépannage: DOCKER_HOST / Colima / Docker Desktop socket)",
                        exception.getMessage());
                dockerAvailable = false;
            }
        }
        return dockerAvailable;
    }

    // The container is a singleton shared by all tests and deliberately never closed here:
    // Testcontainers' Ryuk container removes it when the test JVM exits.
    @SuppressWarnings("resource")
    private static synchronized PostgreSQLContainer container() {
        if (postgresDb == null) {
            postgresDb = new PostgreSQLContainer("postgres:17-alpine").withDatabaseName("orders_test");
            postgresDb.start();
        }
        return postgresDb;
    }
}
