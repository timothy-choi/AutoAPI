package com.autoapi.controlplane;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ControlPlaneFlywayConfiguration {

  @Bean
  @ConfigurationProperties("spring.datasource")
  DataSourceProperties controlPlaneDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean
  @ConditionalOnMissingBean(DataSource.class)
  DataSource controlPlaneDataSource(DataSourceProperties controlPlaneDataSourceProperties) {
    return controlPlaneDataSourceProperties.initializeDataSourceBuilder().build();
  }

  @Bean
  Flyway controlPlaneFlyway(DataSource controlPlaneDataSource) {
    return Flyway.configure()
        .dataSource(controlPlaneDataSource)
        .locations("classpath:db/migration")
        .load();
  }

  @Bean
  FlywayMigrationInitializer controlPlaneFlywayMigrationInitializer(Flyway controlPlaneFlyway) {
    return new FlywayMigrationInitializer(controlPlaneFlyway);
  }
}
