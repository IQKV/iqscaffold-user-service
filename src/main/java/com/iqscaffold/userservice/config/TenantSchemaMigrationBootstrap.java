package com.iqscaffold.userservice.config;

import com.iqscaffold.userservice.tenancy.SchemaNameResolver;
import com.iqscaffold.userservice.tenancy.TenantLiquibaseRunner;
import com.iqscaffold.userservice.tenancy.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Bootstrap component that ensures the default tenant schema has migrations applied.
 * 
 * <p>This component handles the case where the default tenant schema is created externally
 * (e.g., by Helm init scripts) but migrations haven't been run yet. It checks
 * the default tenant schema and applies migrations if the schema exists but is empty.
 * 
 * <p>This is particularly useful in Kubernetes deployments where:
 * <ul>
 *   <li>Helm charts create empty tenant_default schema</li>
 *   <li>The application needs to populate the schema with tables</li>
 *   <li>The DefaultTenantBootstrap skips creation because tenant record exists</li>
 * </ul>
 * 
 * <p>Execution order:
 * <ol>
 *   <li>SystemLiquibaseInitializer runs system migrations</li>
 *   <li>DefaultTenantBootstrap creates tenant record (if needed)</li>
 *   <li>TenantSchemaMigrationBootstrap runs tenant migrations (this class)</li>
 * </ol>
 * 
 * <p>Configuration:
 * <ul>
 *   <li>Enable/disable: {@code iqscaffold.bootstrap.tenant-schema-migration.enabled}</li>
 *   <li>Tenant ID: {@code iqscaffold.bootstrap.tenant-schema-migration.tenant-id}</li>
 * </ul>
 */
@Component
@Order(200) // Run after DefaultTenantBootstrap (Order 100)
@ConditionalOnProperty(
    name = "iqscaffold.bootstrap.tenant-schema-migration.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class TenantSchemaMigrationBootstrap implements ApplicationRunner {

  private static final Logger logger = LoggerFactory.getLogger(TenantSchemaMigrationBootstrap.class);

  private final TenantRepository tenantRepository;
  private final TenantLiquibaseRunner liquibaseRunner;
  private final SchemaNameResolver schemaNameResolver;
  private final JdbcTemplate jdbcTemplate;

  @Value("${iqscaffold.bootstrap.tenant-schema-migration.tenant-id:default}")
  private String defaultTenantId;

  public TenantSchemaMigrationBootstrap(
      TenantRepository tenantRepository,
      TenantLiquibaseRunner liquibaseRunner,
      SchemaNameResolver schemaNameResolver,
      JdbcTemplate jdbcTemplate) {
    this.tenantRepository = tenantRepository;
    this.liquibaseRunner = liquibaseRunner;
    this.schemaNameResolver = schemaNameResolver;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void run(ApplicationArguments args) {
    logger.info("Checking default tenant schema for missing migrations...");

    // Check if the default tenant exists
    var tenantExists = tenantRepository.existsByTenantId(defaultTenantId);
    
    if (!tenantExists) {
      logger.info("Default tenant '{}' does not exist. Skipping tenant schema migration check.", defaultTenantId);
      return;
    }

    logger.info("Default tenant '{}' exists. Checking schema...", defaultTenantId);

    try {
      checkAndMigrateTenantSchema(defaultTenantId);
      logger.info("Default tenant schema migration check completed.");
    } catch (Exception e) {
      logger.error("Failed to check/migrate schema for default tenant: {}", defaultTenantId, e);
      throw new IllegalStateException("Default tenant schema migration failed", e);
    }
  }

  private void checkAndMigrateTenantSchema(String tenantId) {
    var schema = schemaNameResolver.toSchema(tenantId);
    
    logger.debug("Checking schema: {} for tenant: {}", schema, tenantId);

    // Check if schema exists
    boolean schemaExists = checkSchemaExists(schema);
    
    if (!schemaExists) {
      logger.info("Schema {} does not exist for tenant: {}. Creating and migrating...", schema, tenantId);
      createSchemaAndMigrate(schema, tenantId);
      return;
    }

    // Schema exists, check if it has been migrated
    boolean hasMigrations = checkSchemaHasMigrations(schema);
    
    if (!hasMigrations) {
      logger.info("Schema {} exists but has no migrations for tenant: {}. Running migrations...", schema, tenantId);
      runMigrations(schema, tenantId);
    } else {
      logger.debug("Schema {} already has migrations for tenant: {}. Skipping.", schema, tenantId);
    }
  }

  private boolean checkSchemaExists(String schema) {
    try {
      String sql = "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)";
      Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, schema);
      return Boolean.TRUE.equals(exists);
    } catch (Exception e) {
      logger.warn("Failed to check if schema exists: {}", schema, e);
      return false;
    }
  }

  private boolean checkSchemaHasMigrations(String schema) {
    try {
      // Check if databasechangelog table exists in the schema
      String sql = "SELECT EXISTS(SELECT 1 FROM information_schema.tables " +
                   "WHERE table_schema = ? AND table_name = 'databasechangelog')";
      Boolean tableExists = jdbcTemplate.queryForObject(sql, Boolean.class, schema);
      
      if (!Boolean.TRUE.equals(tableExists)) {
        return false;
      }

      // Check if there are any changesets in the changelog
      String countSql = String.format("SELECT COUNT(*) FROM %s.databasechangelog", schema);
      Integer count = jdbcTemplate.queryForObject(countSql, Integer.class);
      
      return count != null && count > 0;
    } catch (Exception e) {
      logger.debug("Schema {} does not have migrations yet: {}", schema, e.getMessage());
      return false;
    }
  }

  private void createSchemaAndMigrate(String schema, String tenantId) {
    try {
      logger.info("Creating schema: {}", schema);
      jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
      logger.info("Schema created: {}", schema);
      
      runMigrations(schema, tenantId);
    } catch (Exception e) {
      logger.error("Failed to create schema and run migrations for tenant: {}", tenantId, e);
      throw new IllegalStateException("Failed to create schema: " + schema, e);
    }
  }

  private void runMigrations(String schema, String tenantId) {
    try {
      logger.info("Running Liquibase migrations for schema: {} (tenant: {})", schema, tenantId);
      liquibaseRunner.runTenantChangelog(schema);
      logger.info("Successfully applied migrations to schema: {} (tenant: {})", schema, tenantId);
    } catch (Exception e) {
      logger.error("Failed to run migrations for schema: {} (tenant: {})", schema, tenantId, e);
      throw new IllegalStateException("Failed to run migrations for schema: " + schema, e);
    }
  }
}
