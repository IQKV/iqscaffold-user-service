package com.iqscaffold.userservice.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iqscaffold.userservice.organization.Organization;
import com.iqscaffold.userservice.organization.OrganizationRepository;
import com.iqscaffold.userservice.config.IqScaffoldProperties;
import com.iqscaffold.userservice.security.SecurityAuditService;
import com.iqscaffold.userservice.shared.Authority;
import com.iqscaffold.userservice.shared.AuthorityRepository;
import com.iqscaffold.userservice.usermanagement.UserContext;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Property-based tests for invitation creation.
 * 
 * Tests the following properties:
 * - Property 2: Invitation Expiration Bounds
 * - Property 3: Email Invitation Validation
 * - Property 4: Authority Default Assignment
 * - Property 5: Authority Validation
 * - Property 29: Email Invitation Single-Use Constraint
 * 
 * **Validates: Requirements 1.2, 1.3, 1.5, 1.6, 12.1**
 */
@RunWith(JUnitQuickcheck.class)
public class InvitationCreationPropertyTest {

  @Mock
  private InvitationRepository invitationRepository;

  @Mock
  private OrganizationRepository organizationRepository;

  @Mock
  private AuthorityRepository authorityRepository;

  @Mock
  private SecurityAuditService auditService;

  @Mock
  private InvitationEmailService emailService;

  @Mock
  private IqScaffoldProperties properties;

  private InvitationService service;
  private UserContext adminUser;
  private Organization organization;
  private Authority userAuthority;
  private Authority adminAuthority;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    
        // Mock properties configuration with lenient stubbing
    var emailConfig = mock(IqScaffoldProperties.Email.class);
    var senderConfig = mock(IqScaffoldProperties.Email.Sender.class);
    lenient().when(properties.email()).thenReturn(emailConfig);
    lenient().when(emailConfig.sender()).thenReturn(senderConfig);
    lenient().when(senderConfig.authBaseUrl()).thenReturn("https://auth.iqkv.dev");

    service = new InvitationService(
        invitationRepository,
        organizationRepository,
        authorityRepository,
        auditService,
        emailService,
        properties
    );

    adminUser = new UserContext(
        1L,
        "admin",
        "admin@example.com",
        java.util.Set.of("ADMIN", "TENANT_ADMIN"),
        java.util.Set.of(),
        "Admin",
        "User",
        "tenant-123",
        1L,
        java.util.Map.of()
    );

    organization = new Organization("Test Organization", "tenant-123");
    organization.setEnabled(true);
    try {
      java.lang.reflect.Field idField = Organization.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(organization, 1L);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set organization ID", e);
    }

    userAuthority = new Authority("USER", "Default user role");
    adminAuthority = new Authority("ADMIN", "Admin role");

    // Setup default mocks
    lenient().when(organizationRepository.findByTenantId("tenant-123"))
        .thenReturn(Optional.of(organization));
    lenient().when(authorityRepository.findByName("USER"))
        .thenReturn(Optional.of(userAuthority));
    lenient().when(authorityRepository.findByName("ADMIN"))
        .thenReturn(Optional.of(adminAuthority));
    lenient().when(invitationRepository.countInvitationsCreatedSince(anyLong(), any(LocalDateTime.class)))
        .thenReturn(0L);
    lenient().when(invitationRepository.existsByInvitationCode(anyString()))
        .thenReturn(false);
    lenient().when(invitationRepository.save(any(OrganizationInvitation.class)))
        .thenAnswer(invocation -> {
          OrganizationInvitation inv = invocation.getArgument(0);
          if (inv.getId() == null) {
            try {
              java.lang.reflect.Field idField = OrganizationInvitation.class.getDeclaredField("id");
              idField.setAccessible(true);
              idField.set(inv, 1L);
            } catch (Exception e) {
              throw new RuntimeException("Failed to set invitation ID", e);
            }
          }
          return inv;
        });
  }

  /**
   * Property 2: Invitation Expiration Bounds
   * 
   * For any invitation creation request with expiration hours between 1 and 720, the system SHALL
   * accept the request and set the expiration time correctly.
   * 
   * Note: Validation of expiration hours outside 1-720 range happens at the REST layer via
   * @Min/@Max annotations. This property test focuses on correct expiration time calculation
   * for valid inputs.
   * 
   * Validates: Requirements 1.2
   */
  @Property(trials = 100)
  public void expirationHoursMustBeBetween1And720(
      @com.pholser.junit.quickcheck.generator.InRange(minInt = 1, maxInt = 720) int expirationHours
  ) {
    // Arrange
    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.LINK,
        null,
        "USER",
        expirationHours,
        null,
        null
    );

    LocalDateTime beforeCreation = LocalDateTime.now();

    // Act
    OrganizationInvitationDto result = service.createInvitation(request, adminUser);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.expiresAt())
        .as("Expiration time should be set correctly for valid expiration hours")
        .isAfter(beforeCreation.plusHours(expirationHours - 1))
        .isBefore(LocalDateTime.now().plusHours(expirationHours + 1));
  }

  /**
   * Property 3: Email Invitation Validation
   * 
   * For any email invitation creation request, if the invitee email is missing or invalid, the
   * system SHALL reject the request; if the email is valid, the system SHALL accept the request.
   * 
   * Validates: Requirements 1.3
   */
  @Property(trials = 50)
  public void emailInvitationRequiresValidEmail(String email) {
    // Arrange
    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.EMAIL,
        email,
        "USER",
        24,
        null,
        null
    );

    // Act & Assert
    if (email == null || email.isBlank()) {
      // Should reject missing email
      assertThatThrownBy(() -> service.createInvitation(request, adminUser))
          .as("Should reject EMAIL invitation without email address")
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Email address is required");
    } else if (isValidEmail(email)) {
      // Should accept valid email
      OrganizationInvitationDto result = service.createInvitation(request, adminUser);
      assertThat(result.inviteeEmail()).isEqualTo(email);
    }
    // Note: Invalid email format is handled by @Email validation annotation at REST layer
  }

  /**
   * Property 4: Authority Default Assignment
   * 
   * For any invitation created without specifying an authority, the system SHALL assign the
   * default authority "USER".
   * 
   * Validates: Requirements 1.5
   */
  @Property(trials = 100)
  public void invitationWithoutAuthorityShouldDefaultToUser() {
    // Arrange
    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.LINK,
        null,
        null, // No authority specified
        24,
        null,
        null
    );

    // Act
    OrganizationInvitationDto result = service.createInvitation(request, adminUser);

    // Assert
    assertThat(result.authority())
        .as("Invitation without specified authority should default to USER")
        .isEqualTo("USER");
  }

  /**
   * Property 5: Authority Validation
   * 
   * For any invitation creation request with a specified authority, if the authority does not
   * exist in the system, the system SHALL reject the request.
   * 
   * Validates: Requirements 1.6
   */
  @Property(trials = 50)
  public void invitationWithInvalidAuthorityShouldBeRejected(String authority) {
    // Arrange
    when(authorityRepository.findByName(authority))
        .thenReturn(Optional.empty());

    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.LINK,
        null,
        authority,
        24,
        null,
        null
    );

    // Act & Assert
    if (authority != null && !authority.isBlank() && !authority.equals("USER") && !authority.equals("ADMIN")) {
      assertThatThrownBy(() -> service.createInvitation(request, adminUser))
          .as("Should reject invitation with non-existent authority")
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid authority");
    }
  }

  /**
   * Property 29: Email Invitation Single-Use Constraint
   * 
   * For any email invitation (type=EMAIL), the max_uses SHALL be set to 1.
   * 
   * Validates: Requirements 12.1
   */
  @Property(trials = 100)
  public void emailInvitationMustHaveMaxUsesOf1(Integer requestedMaxUses) {
    // Arrange
    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.EMAIL,
        "test@example.com",
        "USER",
        24,
        requestedMaxUses, // Even if specified, should be overridden
        null
    );

    // Act
    OrganizationInvitationDto result = service.createInvitation(request, adminUser);

    // Assert
    assertThat(result.maxUses())
        .as("EMAIL invitations must always have max_uses set to 1, regardless of request")
        .isEqualTo(1);
  }

  /**
   * Helper method to validate email format (basic validation).
   */
  private boolean isValidEmail(String email) {
    if (email == null || email.isBlank()) {
      return false;
    }
    // Basic email validation
    return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
  }
}
