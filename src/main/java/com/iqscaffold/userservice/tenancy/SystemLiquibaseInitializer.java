package com.iqscaffold.userservice.tenancy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Initializes system-level database schema using Liquibase migrations.
 * 
 * <p>This component runs system migrations in the public schema before
 * the EntityManagerFactory is created. This ensures that system tables
 * (tenants, authorities, etc.) exist before Hibernate schema validation.
 * 
 * <p>Execution order:
 * <ol>
 *   <li>DataSource bean creation</li>
 *   <li>SystemLiquibaseInitializer afterPropertiesSet (this class)</li>
 *   <li>EntityManagerFactory creation with schema validation</li>
 *   <li>Application startup completes</li>
 * </ol>
 * 
 * <p>This initializer is disabled in test profiles to avoid running migrations
 * during unit and integration tests.
 */
@Component
@Order(Integer.MIN_VALUE) // Run as early as possible
@ConditionalOnProperty(name = "iqscaffold.liquibase.system-schema.enabled", havingValue = "true", matchIfMissing = true)
public class SystemLiquibaseInitializer implements InitializingBean {

  private final TenantLiquibaseRunner runner;
  private static final Logger logger = LoggerFactory.getLogger(SystemLiquibaseInitializer.class);

  public SystemLiquibaseInitializer(final TenantLiquibaseRunner runner) {
    this.runner = runner;
  }

  /**
   * Runs system Liquibase migrations during bean initialization.
   * This executes before EntityManagerFactory creation to ensure
   * system tables exist for Hibernate schema validation.
   */
  @Override
  public void afterPropertiesSet() {
    logger.info("Initializing system schema with Liquibase migrations...");
    try {
      runner.runSystemChangelog();
      logger.info("System schema initialization completed successfully");
    } catch (final Exception e) {
      logger.error("Failed to run system Liquibase changelog", e);
      throw new IllegalStateException("System schema initialization failed", e);
    }
  }
}