package com.iqscaffold.userservice.tenancy;

import com.iqscaffold.userservice.organization.OrganizationRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public Tenant Service.
 * Provides public access to tenant/organization information for discovery purposes.
 */
@Service
@Transactional(readOnly = true)
public class PublicTenantService {

  private static final Logger logger = LoggerFactory.getLogger(PublicTenantService.class);

  private final TenantRepository tenantRepository;
  private final OrganizationRepository organizationRepository;

  public PublicTenantService(
      final TenantRepository tenantRepository,
      final OrganizationRepository organizationRepository) {
    this.tenantRepository = tenantRepository;
    this.organizationRepository = organizationRepository;
  }

  /**
   * Get all active tenants with their organization names.
   * Returns a map of tenant ID to organization name.
   * 
   * This method is cached to reduce database load since tenant list
   * doesn't change frequently.
   *
   * @return map of tenant ID to organization name
   */
  @Cacheable(value = "publicTenantList", unless = "#result == null || #result.isEmpty()")
  public Map<String, String> getAllActiveTenants() {
    logger.debug("Fetching all active tenants for public listing");

    try {
      // Get all active tenants
      var tenants = tenantRepository.findAll().stream()
          .filter(Tenant::isActive)
          .collect(Collectors.toList());

      logger.debug("Found {} active tenants", tenants.size());

      // Build map of tenant ID to organization name
      Map<String, String> tenantMap = new LinkedHashMap<>();

      for (var tenant : tenants) {
        var tenantId = tenant.getTenantId();
        
        // Try to get organization name for this tenant
        var organizationOpt = organizationRepository.findByTenantId(tenantId);
        
        if (organizationOpt.isPresent()) {
          var organization = organizationOpt.get();
          // Only include enabled organizations
          if (Boolean.TRUE.equals(organization.getEnabled())) {
            tenantMap.put(tenantId, organization.getName());
            logger.trace("Added tenant: {} -> {}", tenantId, organization.getName());
          }
        } else {
          // Fallback to tenant name if no organization found
          tenantMap.put(tenantId, tenant.getName());
          logger.trace("Added tenant (no org): {} -> {}", tenantId, tenant.getName());
        }
      }

      logger.info("Returning {} active tenants for public listing", tenantMap.size());
      return tenantMap;

    } catch (Exception e) {
      logger.error("Error fetching active tenants", e);
      // Return empty map on error to prevent exposing internal errors
      return Map.of();
    }
  }
}
