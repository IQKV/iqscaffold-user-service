package com.iqscaffold.userservice.tenancy;

import javax.sql.DataSource;

import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TenantLiquibaseRunner {

  private static final Logger logger = LoggerFactory.getLogger(TenantLiquibaseRunner.class);

  private final DataSource dataSource;
  private final String tenantChangeLog;
  private final String systemChangeLog;
  private final String contexts;

  public TenantLiquibaseRunner(
      final DataSource dataSource,
      @Value("${iqscaffold.liquibase.tenantChangeLog:classpath:db/changelog/tenant/master.xml}") final String tenantChangeLog,
      @Value("${iqscaffold.liquibase.systemChangeLog:classpath:db/changelog/system/master.xml}") final String systemChangeLog,
      @Value("${iqscaffold.liquibase.contexts:}") final String contexts) {
    this.dataSource = dataSource;
    this.tenantChangeLog = tenantChangeLog;
    this.systemChangeLog = systemChangeLog;
    this.contexts = contexts;
  }

  public void runSystemChangelog() throws Exception {
    logger.info("Running system changelog with contexts: {}", contexts);
    
    var liquibase = new SpringLiquibase();
    liquibase.setDataSource(dataSource);
    liquibase.setDefaultSchema("public");
    liquibase.setLiquibaseSchema("public");
    liquibase.setChangeLog(systemChangeLog);
    
    if (StringUtils.hasText(contexts)) {
      liquibase.setContexts(contexts);
    }
    
    liquibase.afterPropertiesSet();
  }

  public void runTenantChangelog(String schema) throws Exception {
    logger.info("Running tenant changelog for schema '{}' with contexts: '{}'", schema, contexts);
    logger.debug("Tenant changelog file: {}", tenantChangeLog);
    
    var liquibase = new SpringLiquibase();
    liquibase.setDataSource(dataSource);
    liquibase.setDefaultSchema(schema);
    liquibase.setLiquibaseSchema(schema);
    liquibase.setChangeLog(tenantChangeLog);
    
    if (StringUtils.hasText(contexts)) {
      logger.info("Applying Liquibase contexts: {}", contexts);
      liquibase.setContexts(contexts);
    } else {
      logger.info("No Liquibase contexts specified - running all changesets");
    }
    
    try {
      liquibase.afterPropertiesSet();
      logger.info("Successfully completed tenant changelog for schema: {}", schema);
    } catch (Exception e) {
      logger.error("Failed to run tenant changelog for schema: {}", schema, e);
      throw e;
    }
  }
}