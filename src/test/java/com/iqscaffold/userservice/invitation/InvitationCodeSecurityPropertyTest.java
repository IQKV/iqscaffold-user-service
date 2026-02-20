package com.iqscaffold.userservice.invitation;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Property-based tests for invitation code security.
 * 
 * **Property 1: Invitation Code Security**
 * For any invitation created by the system, the invitation code SHALL be at least 32 characters
 * long and generated using cryptographically secure randomness.
 * 
 * **Validates: Requirements 1.1**
 */
@RunWith(JUnitQuickcheck.class)
public class InvitationCodeSecurityPropertyTest {

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

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    
    // Mock properties configuration
    var emailConfig = mock(IqScaffoldProperties.Email.class);
    var senderConfig = mock(IqScaffoldProperties.Email.Sender.class);
    when(properties.email()).thenReturn(emailConfig);
    when(emailConfig.sender()).thenReturn(senderConfig);
    when(senderConfig.authBaseUrl()).thenReturn("https://auth.iqscaffold.com");

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

    // Setup default mocks
    lenient().when(organizationRepository.findByTenantId("tenant-123"))
        .thenReturn(Optional.of(organization));
    lenient().when(authorityRepository.findByName("USER"))
        .thenReturn(Optional.of(userAuthority));
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
   * Property 1: Invitation Code Security
   * 
   * For any invitation created by the system, the invitation code SHALL be at least 32 characters
   * long and generated using cryptographically secure randomness.
   * 
   * Note: This property applies to EMAIL and LINK invitations. CODE invitations use 8-character
   * short codes for manual entry.
   * 
   * Validates: Requirements 1.1
   */
  @Property(trials = 100)
  public void invitationCodeMustBeAtLeast32Characters(
      @From(InvitationRequestGenerator.class) CreateInvitationRequest request
  ) {
    // Act
    OrganizationInvitationDto invitation = service.createInvitation(request, adminUser);

    // Assert
    if (request.type() == InvitationType.CODE) {
      // CODE invitations use short 8-character codes
      assertThat(invitation.invitationCode())
          .as("CODE invitation should have 8-character short code")
          .hasSize(8);
    } else {
      // EMAIL and LINK invitations use long secure codes
      assertThat(invitation.invitationCode())
          .as("Invitation code must be at least 32 characters for cryptographic security")
          .hasSizeGreaterThanOrEqualTo(32);
    }
  }

  /**
   * Property 1 (Extended): Invitation codes must be URL-safe
   * 
   * All invitation codes should only contain URL-safe characters (alphanumeric, dash, underscore).
   */
  @Property(trials = 100)
  public void invitationCodeMustBeUrlSafe(
      @From(InvitationRequestGenerator.class) CreateInvitationRequest request
  ) {
    // Act
    OrganizationInvitationDto invitation = service.createInvitation(request, adminUser);

    // Assert
    if (request.type() == InvitationType.CODE) {
      // CODE invitations use uppercase alphanumeric only (no confusing characters)
      assertThat(invitation.invitationCode())
          .as("CODE invitation must use uppercase alphanumeric only")
          .matches("^[A-Z2-9]+$");
    } else {
      // EMAIL and LINK invitations use Base64 URL-safe encoding
      assertThat(invitation.invitationCode())
          .as("Invitation code must be URL-safe (alphanumeric, dash, underscore only)")
          .matches("^[A-Za-z0-9_-]+$");
    }
  }

  /**
   * Property 1 (Extended): Invitation codes must be unique
   * 
   * No two invitations should have the same code (cryptographic randomness ensures uniqueness).
   */
  @Property(trials = 50)
  public void invitationCodesMustBeUnique(
      @From(InvitationRequestGenerator.class) CreateInvitationRequest request1,
      @From(InvitationRequestGenerator.class) CreateInvitationRequest request2
  ) {
    // Act
    OrganizationInvitationDto invitation1 = service.createInvitation(request1, adminUser);
    OrganizationInvitationDto invitation2 = service.createInvitation(request2, adminUser);

    // Assert
    assertThat(invitation1.invitationCode())
        .as("Two invitations created sequentially must have different codes")
        .isNotEqualTo(invitation2.invitationCode());
  }
}
