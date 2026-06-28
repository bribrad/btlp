package com.topnotchbroker.btlp.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared PostgreSQL Testcontainer for integration tests.
 *
 * <p>The container is a JVM-wide singleton started once and reused across every test class that
 * imports this configuration, so the suite spins up a single database. {@link ServiceConnection}
 * wires Spring Boot's {@code DataSource} (and therefore Liquibase) to the container, overriding the
 * local defaults in {@code application.yml}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainerConfig {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  static {
    POSTGRES.start();
  }

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgresContainer() {
    return POSTGRES;
  }
}
