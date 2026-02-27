package com.iqscaffold.userservice.config;

import com.iqscaffold.userservice.infrastructure.repository.dto.TenantDto.CreateTenantRequest;
import com.iqscaffold.userservice.tenancy.TenantManagementService;
import com.iqscaffold.userservice.tenancy.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Bootstrap component that creates a default tenant on application startup if none exists.
 * 
 * <p>This ensures that the application has at least one tenant schema provisioned
 * and ready for user authentication and operations.
 * 
 * <p>Execution order:
 * <ol>
 *   <li>SystemLiquibaseInitializer runs system migrations</li>
 *   <li>Application context fully initialized</li>
 *   <li>DefaultTenantBootstrap creates default tenant (this class)</li>
 * </ol>
 * 
 * <p>Configuration:
 * <ul>
 *   <li>Enable/disable: {@code iqscaffold.bootstrap.default-tenant.enabled}</li>
 *   <li>Tenant ID: {@code iqscaffold.bootstrap.default-tenant.tenant-id}</li>
 *   <li>Tenant name: {@code iqscaffold.bootstrap.default-tenant.name}</li>
 * </ul>
 */
@Component
@Order(100) // Run after system initialization
@ConditionalOnProperty(
    name = "iqscaffold.bootstrap.default-tenant.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class DefaultTenantBootstrap implements ApplicationRunner {

  private static final Logger logger = LoggerFactory.getLogger(DefaultTenantBootstrap.class);

  private final TenantRepository tenantRepository;
  private final TenantManagementService tenantManagementService;

  @Value("${iqscaffold.bootstrap.default-tenant.tenant-id:default}")
  private String defaultTenantId;

  @Value("${iqscaffold.bootstrap.default-tenant.name:Default Organization}")
  private String defaultTenantName;

  @Value("${iqscaffold.bootstrap.default-tenant.description:Default tenant created automatically on startup}")
  private String defaultTenantDescription;

  @Value("${iqscaffold.bootstrap.default-tenant.max-users:100}")
  private Integer maxUsers;

  @Value("${iqscaffold.bootstrap.default-tenant.storage-quota-gb:50}")
  private Integer storageQuotaGb;

  @Value("${iqscaffold.bootstrap.default-tenant.api-rate-limit:1000}")
  private Integer apiRateLimitPerMinute;

  public DefaultTenantBootstrap(
      TenantRepository tenantRepository,
      TenantManagementService tenantManagementService) {
    this.tenantRepository = tenantRepository;
    this.tenantManagementService = tenantManagementService;
  }

  @Override
  public void run(ApplicationArguments args) {
    logger.info("Checking for existing tenants...");

    long tenantCount = tenantRepository.count();
    
    if (tenantCount == 0) {
      logger.info("No tenants found. Creating default tenant: {}", defaultTenantId);
      createDefaultTenant();
    } else {
      logger.info("Found {} existing tenant(s). Skipping default tenant creation.", tenantCount);
    }
  }

  private void createDefaultTenant() {
    try {
      CreateTenantRequest request = new CreateTenantRequest(
          defaultTenantId,
          defaultTenantName,
          defaultTenantDescription,
          null, // domain
          maxUsers,
          storageQuotaGb,
          apiRateLimitPerMinute
      );

      var tenant = tenantManagementService.createTenant(request, "system");
      
      logger.info(
          "Successfully created default tenant: {} (ID: {})",
          tenant.name(),
          tenant.tenantId()
      );
      logger.info("Tenant schema provisioned and migrations applied successfully");
      logger.info("You can now authenticate users with X-Tenant-ID: {}", defaultTenantId);
      
    } catch (Exception e) {
      logger.error("Failed to create default tenant", e);
      throw new IllegalStateException("Default tenant bootstrap failed", e);
    }
  }
}
