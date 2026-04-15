package com.iqscaffold.userservice.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.iqscaffold.userservice.organization.Organization;
import com.iqscaffold.userservice.organization.OrganizationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PublicTenantService Tests")
class PublicTenantServiceTest {

  @Mock
  private TenantRepository tenantRepository;

  @Mock
  private OrganizationRepository organizationRepository;

  private PublicTenantService service;

  @BeforeEach
  void setUp() {
    service = new PublicTenantService(tenantRepository, organizationRepository);
  }

  @Test
  @DisplayName("Should return map of active tenants with organization names")
  void shouldReturnActiveTenants() {
    // Given
    var tenant1 = new Tenant("default", "Default Tenant", "Default tenant for platform", "system");
    tenant1.setStatus(TenantStatus.ACTIVE);

    var tenant2 = new Tenant("acme", "Acme Tenant", "Acme Corporation tenant", "system");
    tenant2.setStatus(TenantStatus.ACTIVE);

    var tenant3 = new Tenant("disabled", "Disabled Tenant", "This is disabled", "system");
    tenant3.setStatus(TenantStatus.SUSPENDED);

    when(tenantRepository.findAll()).thenReturn(List.of(tenant1, tenant2, tenant3));

    var org1 = new Organization("IQ  Key Value Platform", "default");
    org1.setEnabled(true);

    var org2 = new Organization("Acme Corporation", "acme");
    org2.setEnabled(true);

    when(organizationRepository.findByTenantId("default")).thenReturn(Optional.of(org1));
    when(organizationRepository.findByTenantId("acme")).thenReturn(Optional.of(org2));
    // Note: disabled tenant is filtered out, so no need to mock its organization lookup

    // When
    var result = service.getAllActiveTenants();

    // Then
    assertThat(result).hasSize(2);
    assertThat(result).containsEntry("default", "IQ  Key Value Platform");
    assertThat(result).containsEntry("acme", "Acme Corporation");
    assertThat(result).doesNotContainKey("disabled");
  }

  @Test
  @DisplayName("Should exclude disabled organizations")
  void shouldExcludeDisabledOrganizations() {
    // Given
    var tenant1 = new Tenant("active", "Active Tenant", "Active", "system");
    tenant1.setStatus(TenantStatus.ACTIVE);

    var tenant2 = new Tenant("inactive", "Inactive Tenant", "Inactive", "system");
    tenant2.setStatus(TenantStatus.ACTIVE);

    when(tenantRepository.findAll()).thenReturn(List.of(tenant1, tenant2));

    var org1 = new Organization("Active Org", "active");
    org1.setEnabled(true);

    var org2 = new Organization("Inactive Org", "inactive");
    org2.setEnabled(false);

    when(organizationRepository.findByTenantId("active")).thenReturn(Optional.of(org1));
    when(organizationRepository.findByTenantId("inactive")).thenReturn(Optional.of(org2));

    // When
    var result = service.getAllActiveTenants();

    // Then
    assertThat(result).hasSize(1);
    assertThat(result).containsEntry("active", "Active Org");
    assertThat(result).doesNotContainKey("inactive");
  }

  @Test
  @DisplayName("Should use tenant name when organization not found")
  void shouldUseTenantNameWhenOrganizationNotFound() {
    // Given
    var tenant = new Tenant("orphan", "Orphan Tenant", "No organization", "system");
    tenant.setStatus(TenantStatus.ACTIVE);

    when(tenantRepository.findAll()).thenReturn(List.of(tenant));
    when(organizationRepository.findByTenantId("orphan")).thenReturn(Optional.empty());

    // When
    var result = service.getAllActiveTenants();

    // Then
    assertThat(result).hasSize(1);
    assertThat(result).containsEntry("orphan", "Orphan Tenant");
  }

  @Test
  @DisplayName("Should return empty map on error")
  void shouldReturnEmptyMapOnError() {
    // Given
    when(tenantRepository.findAll()).thenThrow(new RuntimeException("Database error"));

    // When
    var result = service.getAllActiveTenants();

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should return empty map when no active tenants")
  void shouldReturnEmptyMapWhenNoActiveTenants() {
    // Given
    when(tenantRepository.findAll()).thenReturn(List.of());

    // When
    var result = service.getAllActiveTenants();

    // Then
    assertThat(result).isEmpty();
  }
}
