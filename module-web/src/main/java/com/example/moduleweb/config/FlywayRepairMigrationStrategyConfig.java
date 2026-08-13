package com.example.moduleweb.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * When migration files are edited after they have already been applied (e.g. seed data or
 * formatting), Flyway validation fails on deploy. {@link Flyway#repair()} realigns checksums in
 * {@code flyway_schema_history} with the current scripts. Enable only for environments where that
 * is acceptable (staging by default via {@code application-staging.yml}).
 */
@Configuration
@ConditionalOnProperty(prefix = "app.flyway", name = "repair-before-migrate", havingValue = "true")
public class FlywayRepairMigrationStrategyConfig {

  @Bean
  FlywayMigrationStrategy flywayMigrationStrategy() {
    return flyway -> {
      flyway.repair();
      flyway.migrate();
    };
  }
}
