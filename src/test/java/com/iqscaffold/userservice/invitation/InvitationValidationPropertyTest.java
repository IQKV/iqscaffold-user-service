package com.iqscaffold.userservice.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iqscaffold.userservice.organization.Organization;
import com.iqscaffold.userservice.organization.OrganizationRepository;
import com.iqscaffold.userservice.config.IqScaffoldProperties;
import com.iqscaffold.userservice.security.SecurityAuditService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Property-based tests for invitation validation and management.
 * 
 * Tests the following properties:
 * - Property 6: Organization Filtering
 * - Property 7: Invitation Revocation State Transition
 * - Property 8: Invitation Link Format
 * - Property 9: Comprehensive Invitation Validation
 * 
 * **Validates: Requirements 1.7, 1.8, 1.9, 2.1, 2.2, 2.3, 2.4**
 */
@RunWith(JUnitQuickcheck.class)
public class InvitationValidationPropertyTest {

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

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    
        // Mock properties configuration with lenient stubbing
    var emailConfig = mock(IqScaffoldProperties.Email.class);
    var senderConfig = mock(IqScaffoldProperties.Email.Sender.class);
    lenient().when(properties.email()).thenReturn(emailConfig);
    lenient().when(emailConfig.sender()).thenReturn(senderConfig);
    lenient().when(senderConfig.authBaseUrl()).thenReturn("https://auth.iqscaffold.com");

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

    // Setup default mocks
    lenient().when(organizationRepository.findByTenantId("tenant-123"))
        .thenReturn(Optional.of(organization));
    lenient().when(organizationRepository.findById(1L))
        .thenReturn(Optional.of(organization));
  }

  /**
   * Property 6: Organization Filtering
   * 
   * For any admin user listing invitations, all returned invitations SHALL belong to the user's
   * organization only.
   * 
   * Validates: Requirements 1.7
   */
  @Property(trials = 100)
  public void allReturnedInvitationsMustBelongToUserOrganization() {
    // Arrange
    OrganizationInvitation invitation1 = new OrganizationInvitation(
        1L, // User's organization
        "tenant-123",
        "code1",
        InvitationType.LINK,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(1)
    );
    
    OrganizationInvitation invitation2 = new OrganizationInvitation(
        1L, // User's organization
        "tenant-123",
        "code2",
        InvitationType.EMAIL,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(2)
    );
    invitation2.setInviteeEmail("test@example.com");

    Pageable pageable = PageRequest.of(0, 10);
    Page<OrganizationInvitation> page = new PageImpl<>(
        java.util.List.of(invitation1, invitation2),
        pageable,
        2
    );

    when(invitationRepository.findByOrganizationId(1L, pageable))
        .thenReturn(page);

    // Act
    Page<OrganizationInvitationDto> result = service.getOrganizationInvitations(null, pageable, adminUser);

    // Assert
    assertThat(result.getContent())
        .as("All returned invitations must belong to user's organization")
        .isNotEmpty()
        .allMatch(inv -> inv.organizationId().equals(1L) && inv.tenantId().equals("tenant-123"));
  }

  /**
   * Property 7: Invitation Revocation State Transition
   * 
   * For any invitation with status PENDING, when an admin revokes it, the invitation status SHALL
   * transition to REVOKED.
   * 
   * Validates: Requirements 1.8
   */
  @Property(trials = 100)
  public void pendingInvitationMustTransitionToRevokedWhenRevoked() {
    // Arrange
    OrganizationInvitation invitation = new OrganizationInvitation(
        1L,
        "tenant-123",
        "test-code",
        InvitationType.LINK,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(1)
    );
    
    // Ensure status is PENDING
    assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);

    when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));
    when(invitationRepository.save(any(OrganizationInvitation.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    service.revokeInvitation(1L, adminUser);

    // Assert
    assertThat(invitation.getStatus())
        .as("Invitation status must transition from PENDING to REVOKED")
        .isEqualTo(InvitationStatus.REVOKED);
  }

  /**
   * Property 8: Invitation Link Format
   * 
   * For any invitation, the generated invitation link SHALL match the format
   * https://auth.iqscaffold.com/join/{code} where {code} is the invitation's unique code.
   * 
   * Validates: Requirements 1.9
   */
  @Property(trials = 100)
  public void invitationLinkMustFollowCorrectFormat(String invitationCode) {
    // Arrange
    if (invitationCode == null || invitationCode.isBlank()) {
      invitationCode = "valid-code-123";
    }

    OrganizationInvitation invitation = new OrganizationInvitation(
        1L,
        "tenant-123",
        invitationCode,
        InvitationType.LINK,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(1)
    );

    when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

    // Act
    InvitationLinkResponse result = service.getInvitationLink(1L, adminUser);

    // Assert
    String expectedUrl = "https://auth.iqscaffold.com/join/" + invitationCode;
    assertThat(result.fullUrl())
        .as("Invitation link must follow format https://auth.iqscaffold.com/join/{code}")
        .isEqualTo(expectedUrl);
    assertThat(result.invitationCode()).isEqualTo(invitationCode);
  }

  /**
   * Property 9: Comprehensive Invitation Validation
   * 
   * For any invitation code, validation SHALL succeed if and only if:
   * (1) the invitation exists,
   * (2) status is PENDING,
   * (3) current time is before expiration time,
   * (4) for link invitations with max_uses, current_uses is less than max_uses, and
   * (5) for email invitations, the provided signup email matches invitee_email.
   * 
   * Validates: Requirements 2.1, 2.2, 2.3, 2.4
   */
  @Property(trials = 100)
  public void invitationValidationMustCheckAllConditions() {
    // Test Case 1: Valid invitation (all conditions met)
    OrganizationInvitation validInvitation = new OrganizationInvitation(
        1L,
        "tenant-123",
        "valid-code",
        InvitationType.LINK,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(1) // Not expired
    );
    validInvitation.setMaxUses(5);
    validInvitation.setCurrentUses(2); // Under limit

    when(invitationRepository.findByInvitationCode("valid-code"))
        .thenReturn(Optional.of(validInvitation));

    InvitationValidationResult result1 = service.validateInvitation("valid-code");
    assertThat(result1.valid())
        .as("Invitation should be valid when all conditions are met")
        .isTrue();

    // Test Case 2: Expired invitation (condition 3 fails)
    OrganizationInvitation expiredInvitation = new OrganizationInvitation(
        1L,
        "tenant-123",
        "expired-code",
        InvitationType.LINK,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().minusHours(1) // Expired
    );

    when(invitationRepository.findByInvitationCode("expired-code"))
        .thenReturn(Optional.of(expiredInvitation));

    InvitationValidationResult result2 = service.validateInvitation("expired-code");
    assertThat(result2.valid())
        .as("Invitation should be invalid when expired")
        .isFalse();
    assertThat(result2.message()).contains("expired");

    // Test Case 3: Revoked invitation (condition 2 fails)
    OrganizationInvitation revokedInvitation = new OrganizationInvitation(
        1L,
        "tenant-123",
        "revoked-code",
        InvitationType.LINK,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(1)
    );
    revokedInvitation.revoke();

    when(invitationRepository.findByInvitationCode("revoked-code"))
        .thenReturn(Optional.of(revokedInvitation));

    InvitationValidationResult result3 = service.validateInvitation("revoked-code");
    assertThat(result3.valid())
        .as("Invitation should be invalid when revoked")
        .isFalse();
    assertThat(result3.message()).contains("revoked");

    // Test Case 4: Max uses reached (condition 4 fails)
    OrganizationInvitation maxedInvitation = new OrganizationInvitation(
        1L,
        "tenant-123",
        "maxed-code",
        InvitationType.LINK,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(1)
    );
    maxedInvitation.setMaxUses(3);
    maxedInvitation.setCurrentUses(3); // Reached limit

    when(invitationRepository.findByInvitationCode("maxed-code"))
        .thenReturn(Optional.of(maxedInvitation));

    InvitationValidationResult result4 = service.validateInvitation("maxed-code");
    assertThat(result4.valid())
        .as("Invitation should be invalid when max uses reached")
        .isFalse();
    assertThat(result4.message()).contains("maximum uses");

    // Test Case 5: Email mismatch (condition 5 fails)
    OrganizationInvitation emailInvitation = new OrganizationInvitation(
        1L,
        "tenant-123",
        "email-code",
        InvitationType.EMAIL,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(1)
    );
    emailInvitation.setInviteeEmail("correct@example.com");
    emailInvitation.setMaxUses(1);

    when(invitationRepository.findByInvitationCode("email-code"))
        .thenReturn(Optional.of(emailInvitation));

    InvitationValidationResult result5 = service.validateInvitation("email-code", "wrong@example.com");
    assertThat(result5.valid())
        .as("Email invitation should be invalid when email doesn't match")
        .isFalse();
    assertThat(result5.message()).contains("Email does not match");

    // Test Case 6: Email invitation with matching email (all conditions met)
    InvitationValidationResult result6 = service.validateInvitation("email-code", "correct@example.com");
    assertThat(result6.valid())
        .as("Email invitation should be valid when email matches")
        .isTrue();

    // Test Case 7: Non-existent invitation (condition 1 fails)
    when(invitationRepository.findByInvitationCode("non-existent"))
        .thenReturn(Optional.empty());

    InvitationValidationResult result7 = service.validateInvitation("non-existent");
    assertThat(result7.valid())
        .as("Validation should fail for non-existent invitation")
        .isFalse();
    assertThat(result7.message()).contains("not found");
  }
}
