package com.iqscaffold.userservice.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.iqscaffold.userservice.config.PlatformConfigurationProperties;
import com.iqscaffold.userservice.emailverification.EmailVerificationService;
import com.iqscaffold.userservice.organization.Organization;
import com.iqscaffold.userservice.organization.OrganizationRepository;
import com.iqscaffold.userservice.security.InputSanitizer;
import com.iqscaffold.userservice.security.SecurityAuditService;
import com.iqscaffold.userservice.shared.Authority;
import com.iqscaffold.userservice.shared.AuthorityRepository;
import com.iqscaffold.userservice.usermanagement.User;
import com.iqscaffold.userservice.usermanagement.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Test class for tenant owner registration functionality.
 * Verifies that the first user in a tenant receives TENANT_OWNER role and an organization is created.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Tenant Owner Registration Tests")
class TenantOwnerRegistrationTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private AuthorityRepository authorityRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private SecurityAuditService securityAuditService;

  @Mock
  private InputSanitizer inputSanitizer;

  @Mock
  private EmailVerificationService emailVerificationService;

  @Mock
  private PlatformConfigurationProperties platformConfig;

  @Mock
  private PlatformConfigurationProperties.Authorities authoritiesConfig;

  @Mock
  private OrganizationRepository organizationRepository;

  private UserRegistrationService registrationService;

  @BeforeEach
  void setUp() {
    registrationService = new UserRegistrationService(
        userRepository,
        authorityRepository,
        passwordEncoder,
        securityAuditService,
        inputSanitizer,
        emailVerificationService,
        platformConfig,
        organizationRepository
    );

    // Setup default mocks
    when(platformConfig.authorities()).thenReturn(authoritiesConfig);
    when(authoritiesConfig.defaultAuthorities()).thenReturn(List.of("USER"));

    when(inputSanitizer.sanitizeUsername(anyString())).thenAnswer(i -> i.getArgument(0));
    when(inputSanitizer.sanitizeEmail(anyString())).thenAnswer(i -> i.getArgument(0));
    when(inputSanitizer.sanitizeName(anyString())).thenAnswer(i -> i.getArgument(0));
    when(inputSanitizer.isInputSafe(anyString())).thenReturn(true);
    when(inputSanitizer.containsSqlInjection(anyString())).thenReturn(false);

    when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
  }

  @Test
  @DisplayName("First user should receive TENANT_OWNER role")
  void firstUserShouldReceiveTenantOwnerRole() {
    // Arrange
    var tenantId = "tenant-001";
    var request = new SignupRequest(
        "john.doe",
        "john.doe@example.com",
        "SecurePassword123!",
        "John",
        "Doe",
        tenantId
    );

    // Mock: No existing users (first user)
    when(userRepository.count()).thenReturn(0L);
    when(userRepository.existsByUsername(anyString())).thenReturn(false);
    when(userRepository.existsByEmail(anyString())).thenReturn(false);

    // Mock authorities
    var userAuthority = new Authority("USER", "Standard user access");
    var tenantOwnerAuthority = new Authority("TENANT_OWNER", "Tenant owner with full access");

    when(authorityRepository.findByName("USER")).thenReturn(Optional.of(userAuthority));
    when(authorityRepository.findByName("TENANT_OWNER")).thenReturn(Optional.of(tenantOwnerAuthority));

    // Mock user save
    var savedUser = new User("john.doe", "john.doe@example.com", "hashed_password", "John", "Doe", tenantId);
    when(userRepository.save(any(User.class))).thenReturn(savedUser);

    // Mock organization repository
    when(organizationRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
    when(organizationRepository.save(any(Organization.class))).thenAnswer(i -> i.getArgument(0));

    // Act
    var response = registrationService.registerUser(request, "127.0.0.1", "Test-Agent");

    // Assert
    assertThat(response).isNotNull();
    assertThat(response.username()).isEqualTo("john.doe");
    assertThat(response.email()).isEqualTo("john.doe@example.com");

    // Verify TENANT_OWNER authority was fetched
    verify(authorityRepository).findByName("TENANT_OWNER");
    verify(authorityRepository).findByName("USER");

    // Verify organization was created
    var orgCaptor = ArgumentCaptor.forClass(Organization.class);
    verify(organizationRepository).save(orgCaptor.capture());

    var createdOrg = orgCaptor.getValue();
    assertThat(createdOrg.getName()).isEqualTo("John Doe's Organization");
    assertThat(createdOrg.getTenantId()).isEqualTo(tenantId);
    assertThat(createdOrg.getEnabled()).isTrue();
    assertThat(createdOrg.getSubscriptionStatus()).isEqualTo("trial");
    assertThat(createdOrg.getSubscriptionPlan()).isEqualTo("basic");
    assertThat(createdOrg.getMaxUsers()).isEqualTo(10);
  }

  @Test
  @DisplayName("Subsequent users should NOT receive TENANT_OWNER role")
  void subsequentUsersShouldNotReceiveTenantOwnerRole() {
    // Arrange
    var tenantId = "tenant-001";
    var request = new SignupRequest(
        "jane.smith",
        "jane.smith@example.com",
        "SecurePassword123!",
        "Jane",
        "Smith",
        tenantId
    );

    // Mock: Existing users (not first user)
    when(userRepository.count()).thenReturn(1L);
    when(userRepository.existsByUsername(anyString())).thenReturn(false);
    when(userRepository.existsByEmail(anyString())).thenReturn(false);

    // Mock authorities
    var userAuthority = new Authority("USER", "Standard user access");
    when(authorityRepository.findByName("USER")).thenReturn(Optional.of(userAuthority));

    // Mock user save
    var savedUser = new User("jane.smith", "jane.smith@example.com", "hashed_password", "Jane", "Smith", tenantId);
    when(userRepository.save(any(User.class))).thenReturn(savedUser);

    // Act
    var response = registrationService.registerUser(request, "127.0.0.1", "Test-Agent");

    // Assert
    assertThat(response).isNotNull();
    assertThat(response.username()).isEqualTo("jane.smith");

    // Verify TENANT_OWNER authority was NOT fetched
    verify(authorityRepository, times(0)).findByName("TENANT_OWNER");
    verify(authorityRepository).findByName("USER");

    // Verify organization was NOT created
    verify(organizationRepository, times(0)).save(any(Organization.class));
  }

  @Test
  @DisplayName("Organization should be linked to first user")
  void organizationShouldBeLinkedToFirstUser() {
    // Arrange
    var tenantId = "tenant-002";
    var request = new SignupRequest(
        "owner.user",
        "owner@example.com",
        "SecurePassword123!",
        "Owner",
        "User",
        tenantId
    );

    // Mock: No existing users (first user)
    when(userRepository.count()).thenReturn(0L);
    when(userRepository.existsByUsername(anyString())).thenReturn(false);
    when(userRepository.existsByEmail(anyString())).thenReturn(false);

    // Mock authorities
    var userAuthority = new Authority("USER");
    var tenantOwnerAuthority = new Authority("TENANT_OWNER");
    when(authorityRepository.findByName("USER")).thenReturn(Optional.of(userAuthority));
    when(authorityRepository.findByName("TENANT_OWNER")).thenReturn(Optional.of(tenantOwnerAuthority));

    // Mock user save with ID
    var savedUser = new User("owner.user", "owner@example.com", "hashed_password", "Owner", "User", tenantId);
    var userIdField = User.class.getDeclaredField("id");
    userIdField.setAccessible(true);
    userIdField.set(savedUser, 123L);

    when(userRepository.save(any(User.class))).thenReturn(savedUser);

    // Mock organization repository
    when(organizationRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
    when(organizationRepository.save(any(Organization.class))).thenAnswer(i -> i.getArgument(0));

    // Act
    registrationService.registerUser(request, "127.0.0.1", "Test-Agent");

    // Assert
    var orgCaptor = ArgumentCaptor.forClass(Organization.class);
    verify(organizationRepository).save(orgCaptor.capture());

    var createdOrg = orgCaptor.getValue();
    assertThat(createdOrg.getOwnerUserId()).isEqualTo(123L);
    assertThat(createdOrg.getBillingEmail()).isEqualTo("owner@example.com");
    assertThat(createdOrg.getCreatedBy()).isEqualTo("owner.user");
  }

  @Test
  @DisplayName("Should handle existing organization gracefully")
  void shouldHandleExistingOrganizationGracefully() throws Exception {
    // Arrange
    var tenantId = "tenant-003";
    var request = new SignupRequest(
        "new.owner",
        "new.owner@example.com",
        "SecurePassword123!",
        "New",
        "Owner",
        tenantId
    );

    // Mock: No existing users (first user)
    when(userRepository.count()).thenReturn(0L);
    when(userRepository.existsByUsername(anyString())).thenReturn(false);
    when(userRepository.existsByEmail(anyString())).thenReturn(false);

    // Mock authorities
    var userAuthority = new Authority("USER");
    var tenantOwnerAuthority = new Authority("TENANT_OWNER");
    when(authorityRepository.findByName("USER")).thenReturn(Optional.of(userAuthority));
    when(authorityRepository.findByName("TENANT_OWNER")).thenReturn(Optional.of(tenantOwnerAuthority));

    // Mock user save with ID
    var savedUser = new User("new.owner", "new.owner@example.com", "hashed_password", "New", "Owner", tenantId);
    var userIdField = User.class.getDeclaredField("id");
    userIdField.setAccessible(true);
    userIdField.set(savedUser, 456L);

    when(userRepository.save(any(User.class))).thenReturn(savedUser);

    // Mock existing organization
    var existingOrg = new Organization("Existing Org", tenantId);
    when(organizationRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existingOrg));
    when(organizationRepository.save(any(Organization.class))).thenAnswer(i -> i.getArgument(0));

    // Act
    registrationService.registerUser(request, "127.0.0.1", "Test-Agent");

    // Assert - should update existing organization
    verify(organizationRepository).save(existingOrg);
    assertThat(existingOrg.getOwnerUserId()).isEqualTo(456L);
  }
}
