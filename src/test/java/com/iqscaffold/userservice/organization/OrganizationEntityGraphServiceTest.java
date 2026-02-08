package com.iqscaffold.userservice.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.iqscaffold.userservice.shared.PaymentGatewayProvider;
import com.iqscaffold.userservice.tenancy.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationEntityGraphServiceTest {

  @Mock
  private OrganizationRepository organizationRepository;

  @InjectMocks
  private OrganizationEntityGraphService organizationEntityGraphService;

  @Test
  @DisplayName("Should find organization with tenant")
  void shouldFindOrganizationWithTenant() {
    // Arrange
    var tenantId = "tenant-123";
    var organization = new Organization("Test Org", tenantId);
    when(organizationRepository.findByTenantIdWithTenant(tenantId)).thenReturn(Optional.of(organization));

    // Act
    var result = organizationEntityGraphService.findOrganizationWithTenant(tenantId);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().getName()).isEqualTo("Test Org");
  }

  @Test
  @DisplayName("Should find organization for settings")
  void shouldFindOrganizationForSettings() {
    // Arrange
    var organizationId = 1L;
    var organization = new Organization("Test Org", "tenant-123");
    when(organizationRepository.findByIdWithPreferences(organizationId)).thenReturn(Optional.of(organization));

    // Act
    var result = organizationEntityGraphService.findOrganizationForSettings(organizationId);

    // Assert
    assertThat(result).isPresent();
  }

  @Test
  @DisplayName("Should find organization with complete profile")
  void shouldFindOrganizationWithCompleteProfile() {
    // Arrange
    var tenantId = "tenant-123";
    var organization = new Organization("Test Org", tenantId);
    when(organizationRepository.findByTenantIdWithComplete(tenantId)).thenReturn(Optional.of(organization));

    // Act
    var result = organizationEntityGraphService.findOrganizationWithCompleteProfile(tenantId);

    // Assert
    assertThat(result).isPresent();
  }

  @Test
  @DisplayName("Should get organization billing info")
  void shouldGetOrganizationBillingInfo() {
    // Arrange
    var tenantId = "tenant-123";
    var organization = new Organization("Test Org", tenantId);
    organization.setBillingEmail("billing@test.com");
    organization.setSubscriptionStatus("ACTIVE");
    organization.setSubscriptionPlan("PREMIUM");
    organization.setPaymentGatewayProvider(PaymentGatewayProvider.STRIPE);
    organization.setChargesEnabled(true);
    organization.setPayoutsEnabled(true);
    var tenant = new Tenant(tenantId, "Test Tenant");
    organization.setTenant(tenant);
    when(organizationRepository.findByTenantIdWithTenant(tenantId)).thenReturn(Optional.of(organization));

    // Act
    var result = organizationEntityGraphService.getOrganizationBillingInfo(tenantId);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.organizationName()).isEqualTo("Test Org");
    assertThat(result.billingEmail()).isEqualTo("billing@test.com");
    assertThat(result.subscriptionStatus()).isEqualTo("ACTIVE");
    assertThat(result.canAcceptPayments()).isFalse(); // Organization.canAcceptPayments() checks hasPaymentGatewayAccount()
    assertThat(result.tenantName()).isEqualTo("Test Tenant");
  }

  @Test
  @DisplayName("Should return null when organization not found for billing info")
  void shouldReturnNullWhenOrganizationNotFoundForBillingInfo() {
    // Arrange
    var tenantId = "nonexistent";
    when(organizationRepository.findByTenantIdWithTenant(tenantId)).thenReturn(Optional.empty());

    // Act
    var result = organizationEntityGraphService.getOrganizationBillingInfo(tenantId);

    // Assert
    assertThat(result).isNull();
  }

  @Test
  @DisplayName("Should get organization settings")
  void shouldGetOrganizationSettings() {
    // Arrange
    var organizationId = 1L;
    var organization = new Organization("Test Org", "tenant-123");
    organization.setMaxUsers(100);
    var preference = new OrganizationPreference();
    preference.setDefaultLocale("en");
    preference.setDefaultTimezone("UTC");
    preference.setDefaultCurrency("USD");
    preference.setPasswordMinLength(8);
    organization.setPreference(preference);
    when(organizationRepository.findByIdWithPreferences(organizationId)).thenReturn(Optional.of(organization));

    // Act
    var result = organizationEntityGraphService.getOrganizationSettings(organizationId);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.organizationName()).isEqualTo("Test Org");
    assertThat(result.maxUsers()).isEqualTo(100);
    assertThat(result.defaultLocale()).isEqualTo("en");
  }

  @Test
  @DisplayName("Should use default values when preference is null")
  void shouldUseDefaultValuesWhenPreferenceIsNull() {
    // Arrange
    var organizationId = 1L;
    var organization = new Organization("Test Org", "tenant-123");
    organization.setMaxUsers(50);
    organization.setPreference(null);
    when(organizationRepository.findByIdWithPreferences(organizationId)).thenReturn(Optional.of(organization));

    // Act
    var result = organizationEntityGraphService.getOrganizationSettings(organizationId);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.defaultLocale()).isEqualTo("en");
    assertThat(result.defaultTimezone()).isEqualTo("UTC");
  }

  @Test
  @DisplayName("Should check if organization can accept payments")
  void shouldCheckIfOrganizationCanAcceptPayments() {
    // Arrange
    var tenantId = "tenant-123";
    var organization = new Organization("Test Org", tenantId);
    organization.setChargesEnabled(true);
    organization.setPaymentGatewayAccountId("acct_123");
    when(organizationRepository.findByTenantId(tenantId)).thenReturn(Optional.of(organization));

    // Act
    var result = organizationEntityGraphService.canOrganizationAcceptPayments(tenantId);

    // Assert
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("Should return false when organization not found for payment check")
  void shouldReturnFalseWhenOrganizationNotFoundForPaymentCheck() {
    // Arrange
    var tenantId = "nonexistent";
    when(organizationRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

    // Act
    var result = organizationEntityGraphService.canOrganizationAcceptPayments(tenantId);

    // Assert
    assertThat(result).isFalse();
  }
}
