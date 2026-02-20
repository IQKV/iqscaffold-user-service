package com.iqscaffold.userservice.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iqscaffold.userservice.config.IqScaffoldProperties;
import com.iqscaffold.userservice.organization.Organization;
import com.iqscaffold.userservice.organization.OrganizationRepository;
import com.iqscaffold.userservice.security.SecurityAuditService;
import com.iqscaffold.userservice.shared.Authority;
import com.iqscaffold.userservice.shared.AuthorityRepository;
import com.iqscaffold.userservice.usermanagement.UserContext;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for InvitationService.
 * Tests invitation creation, validation, and management functionality.
 * 
 * Validates Requirements: 1.1, 1.2, 1.3, 1.5, 1.6, 9.1, 9.2, 12.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Invitation Service Tests")
class InvitationServiceTest {

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

  @BeforeEach
  void setUp() {
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
    // Set organization ID for rate limiting tests
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
          // Set ID if not already set
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

  // ========== Invitation Creation Tests ==========

  @Test
  @DisplayName("Should create EMAIL invitation successfully")
  void shouldCreateEmailInvitationSuccessfully() {
    // Arrange
    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.EMAIL,
        "invitee@example.com",
        "USER",
        24,
        null,
        null
    );

    // Act
    OrganizationInvitationDto result = service.createInvitation(request, adminUser);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.type()).isEqualTo(InvitationType.EMAIL);
    assertThat(result.inviteeEmail()).isEqualTo("invitee@example.com");
    assertThat(result.authority()).isEqualTo("USER");
    assertThat(result.status()).isEqualTo(InvitationStatus.PENDING);
    assertThat(result.invitationCode()).isNotNull();
    assertThat(result.invitationCode().length()).isGreaterThanOrEqualTo(32);
    assertThat(result.maxUses()).isEqualTo(1); // EMAIL invitations are always single-use

    verify(invitationRepository).save(any(OrganizationInvitation.class));
    verify(auditService).logInvitationCreated(
        eq(adminUser.userId()),
        eq(adminUser.username()),
        anyLong(),
        eq("EMAIL")
    );
  }

  @Test
  @DisplayName("Should create LINK invitation successfully")
  void shouldCreateLinkInvitationSuccessfully() {
    // Arrange
    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.LINK,
        null,
        "USER",
        48,
        5,
        null
    );

    // Act
    OrganizationInvitationDto result = service.createInvitation(request, adminUser);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.type()).isEqualTo(InvitationType.LINK);
    assertThat(result.inviteeEmail()).isNull();
    assertThat(result.maxUses()).isEqualTo(5);
    assertThat(result.invitationCode().length()).isGreaterThanOrEqualTo(32);

    verify(invitationRepository).save(any(OrganizationInvitation.class));
  }

  @Test
  @DisplayName("Should create CODE invitation with short code")
  void shouldCreateCodeInvitationWithShortCode() {
    // Arrange
    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.CODE,
        null,
        "USER",
        72,
        null,
        null
    );

    // Act
    OrganizationInvitationDto result = service.createInvitation(request, adminUser);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.type()).isEqualTo(InvitationType.CODE);
    assertThat(result.invitationCode()).hasSize(8); // Short code is 8 characters
    assertThat(result.invitationCode()).matches("^[A-Z2-9]+$"); // Only uppercase and numbers

    verify(invitationRepository).save(any(OrganizationInvitation.class));
  }

  @Test
  @DisplayName("Should use default authority USER when not specified")
  void shouldUseDefaultAuthorityWhenNotSpecified() {
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
    assertThat(result.authority()).isEqualTo("USER");
    verify(authorityRepository).findByName("USER");
  }

  @Test
  @DisplayName("Should set expiration time correctly")
  void shouldSetExpirationTimeCorrectly() {
    // Arrange
    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.LINK,
        null,
        "USER",
        48, // 48 hours
        null,
        null
    );

    LocalDateTime beforeCreation = LocalDateTime.now();

    // Act
    OrganizationInvitationDto result = service.createInvitation(request, adminUser);

    // Assert
    LocalDateTime afterCreation = LocalDateTime.now().plusHours(48);
    assertThat(result.expiresAt()).isAfter(beforeCreation.plusHours(47));
    assertThat(result.expiresAt()).isBefore(afterCreation.plusHours(1));
  }

  // ========== Validation Tests ==========

  @Test
  @DisplayName("Should throw exception for missing email in EMAIL invitation")
  void shouldThrowExceptionForMissingEmailInEmailInvitation() {
    // Arrange
    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.EMAIL,
        null, // Missing email
        "USER",
        24,
        null,
        null
    );

    // Act & Assert
    assertThatThrownBy(() -> service.createInvitation(request, adminUser))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Email address is required");

    verify(invitationRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should throw exception for invalid authority")
  void shouldThrowExceptionForInvalidAuthority() {
    // Arrange
    when(authorityRepository.findByName("INVALID_ROLE"))
        .thenReturn(Optional.empty());

    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.LINK,
        null,
        "INVALID_ROLE",
        24,
        null,
        null
    );

    // Act & Assert
    assertThatThrownBy(() -> service.createInvitation(request, adminUser))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid authority: INVALID_ROLE");

    verify(invitationRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should throw exception for inactive organization")
  void shouldThrowExceptionForInactiveOrganization() {
    // Arrange
    organization.setEnabled(false);

    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.LINK,
        null,
        "USER",
        24,
        null,
        null
    );

    // Act & Assert
    assertThatThrownBy(() -> service.createInvitation(request, adminUser))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Organization is not active");

    verify(invitationRepository, never()).save(any());
  }

  // ========== Rate Limiting Tests (Requirement 9.1, 9.2) ==========

  @Test
  @DisplayName("Should enforce rate limit of 10 invitations per hour")
  void shouldEnforceRateLimitOf10InvitationsPerHour() {
    // Arrange
    when(invitationRepository.countInvitationsCreatedSince(anyLong(), any(LocalDateTime.class)))
        .thenReturn(10L); // Already at limit

    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.LINK,
        null,
        "USER",
        24,
        null,
        null
    );

    // Act & Assert
    assertThatThrownBy(() -> service.createInvitation(request, adminUser))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Rate limit exceeded")
        .hasMessageContaining("Maximum 10 invitations per hour");

    verify(invitationRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should allow invitation creation when under rate limit")
  void shouldAllowInvitationCreationWhenUnderRateLimit() {
    // Arrange
    when(invitationRepository.countInvitationsCreatedSince(anyLong(), any(LocalDateTime.class)))
        .thenReturn(9L); // Under limit

    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.LINK,
        null,
        "USER",
        24,
        null,
        null
    );

    // Act
    OrganizationInvitationDto result = service.createInvitation(request, adminUser);

    // Assert
    assertThat(result).isNotNull();
    verify(invitationRepository).save(any(OrganizationInvitation.class));
  }

  // ========== Invitation Code Uniqueness Tests ==========

  @Test
  @DisplayName("Should regenerate code if collision detected")
  void shouldRegenerateCodeIfCollisionDetected() {
    // Arrange
    when(invitationRepository.existsByInvitationCode(anyString()))
        .thenReturn(true)  // First attempt: collision
        .thenReturn(false); // Second attempt: unique

    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.LINK,
        null,
        "USER",
        24,
        null,
        null
    );

    // Act
    OrganizationInvitationDto result = service.createInvitation(request, adminUser);

    // Assert
    assertThat(result).isNotNull();
    verify(invitationRepository, times(2)).existsByInvitationCode(anyString());
    verify(invitationRepository).save(any(OrganizationInvitation.class));
  }

  // ========== Expiration Bounds Tests (Requirement 1.2) ==========

  @Test
  @DisplayName("Should accept minimum expiration of 1 hour")
  void shouldAcceptMinimumExpirationOf1Hour() {
    // Arrange
    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.LINK,
        null,
        "USER",
        1, // Minimum: 1 hour
        null,
        null
    );

    // Act
    OrganizationInvitationDto result = service.createInvitation(request, adminUser);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.expiresAt()).isAfter(LocalDateTime.now());
  }

  @Test
  @DisplayName("Should accept maximum expiration of 720 hours (30 days)")
  void shouldAcceptMaximumExpirationOf720Hours() {
    // Arrange
    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.LINK,
        null,
        "USER",
        720, // Maximum: 30 days
        null,
        null
    );

    // Act
    OrganizationInvitationDto result = service.createInvitation(request, adminUser);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.expiresAt()).isAfter(LocalDateTime.now().plusDays(29));
  }

  // ========== Email Invitation Single-Use Constraint (Requirement 12.1) ==========

  @Test
  @DisplayName("Should set max_uses to 1 for EMAIL invitations")
  void shouldSetMaxUsesTo1ForEmailInvitations() {
    // Arrange
    CreateInvitationRequest request = new CreateInvitationRequest(
        InvitationType.EMAIL,
        "invitee@example.com",
        "USER",
        24,
        5, // Even if specified, should be overridden to 1
        null
    );

    // Act
    OrganizationInvitationDto result = service.createInvitation(request, adminUser);

    // Assert
    assertThat(result.maxUses()).isEqualTo(1);
  }

  // ========== Invitation Validation Tests (Requirements 2.1, 2.2, 2.3, 2.4) ==========

  @Test
  @DisplayName("Should validate invitation successfully when all conditions are met")
  void shouldValidateInvitationSuccessfully() {
    // Arrange
    OrganizationInvitation invitation = new OrganizationInvitation(
        1L, // organizationId
        "tenant-123",
        "valid-code",
        InvitationType.LINK,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(1)
    );

    when(invitationRepository.findByInvitationCode("valid-code"))
        .thenReturn(Optional.of(invitation));
    when(organizationRepository.findById(1L))
        .thenReturn(Optional.of(organization));

    // Act
    InvitationValidationResult result = service.validateInvitation("valid-code");

    // Assert
    assertThat(result.valid()).isTrue();
    assertThat(result.message()).isEqualTo("Invitation is valid");
    assertThat(result.organization()).isNotNull();
    assertThat(result.organization().name()).isEqualTo("Test Organization");
  }

  @Test
  @DisplayName("Should return invalid for non-existent invitation")
  void shouldReturnInvalidForNonExistentInvitation() {
    // Arrange
    when(invitationRepository.findByInvitationCode("invalid-code"))
        .thenReturn(Optional.empty());

    // Act
    InvitationValidationResult result = service.validateInvitation("invalid-code");

    // Assert
    assertThat(result.valid()).isFalse();
    assertThat(result.message()).isEqualTo("Invitation not found");
    assertThat(result.organization()).isNull();
  }

  @Test
  @DisplayName("Should return invalid for expired invitation")
  void shouldReturnInvalidForExpiredInvitation() {
    // Arrange
    OrganizationInvitation invitation = new OrganizationInvitation(
        1L,
        "tenant-123",
        "expired-code",
        InvitationType.LINK,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().minusHours(1) // Expired 1 hour ago
    );

    when(invitationRepository.findByInvitationCode("expired-code"))
        .thenReturn(Optional.of(invitation));

    // Act
    InvitationValidationResult result = service.validateInvitation("expired-code");

    // Assert
    assertThat(result.valid()).isFalse();
    assertThat(result.message()).isEqualTo("Invitation has expired");
    assertThat(result.organization()).isNull();
  }

  @Test
  @DisplayName("Should return invalid for revoked invitation")
  void shouldReturnInvalidForRevokedInvitation() {
    // Arrange
    OrganizationInvitation invitation = new OrganizationInvitation(
        1L,
        "tenant-123",
        "revoked-code",
        InvitationType.LINK,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(1)
    );
    invitation.revoke();

    when(invitationRepository.findByInvitationCode("revoked-code"))
        .thenReturn(Optional.of(invitation));

    // Act
    InvitationValidationResult result = service.validateInvitation("revoked-code");

    // Assert
    assertThat(result.valid()).isFalse();
    assertThat(result.message()).isEqualTo("Invitation has been revoked");
    assertThat(result.organization()).isNull();
  }

  @Test
  @DisplayName("Should return invalid for invitation that reached max uses")
  void shouldReturnInvalidForInvitationThatReachedMaxUses() {
    // Arrange
    OrganizationInvitation invitation = new OrganizationInvitation(
        1L,
        "tenant-123",
        "maxed-code",
        InvitationType.LINK,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(1)
    );
    invitation.setMaxUses(3);
    invitation.setCurrentUses(3); // Reached max uses

    when(invitationRepository.findByInvitationCode("maxed-code"))
        .thenReturn(Optional.of(invitation));

    // Act
    InvitationValidationResult result = service.validateInvitation("maxed-code");

    // Assert
    assertThat(result.valid()).isFalse();
    assertThat(result.message()).isEqualTo("Invitation has reached maximum uses");
    assertThat(result.organization()).isNull();
  }

  @Test
  @DisplayName("Should validate email invitation with matching email")
  void shouldValidateEmailInvitationWithMatchingEmail() {
    // Arrange
    OrganizationInvitation invitation = new OrganizationInvitation(
        1L,
        "tenant-123",
        "email-code",
        InvitationType.EMAIL,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(1)
    );
    invitation.setInviteeEmail("invitee@example.com");
    invitation.setMaxUses(1);

    when(invitationRepository.findByInvitationCode("email-code"))
        .thenReturn(Optional.of(invitation));
    when(organizationRepository.findById(1L))
        .thenReturn(Optional.of(organization));

    // Act
    InvitationValidationResult result = service.validateInvitation("email-code", "invitee@example.com");

    // Assert
    assertThat(result.valid()).isTrue();
    assertThat(result.message()).isEqualTo("Invitation is valid");
    assertThat(result.organization()).isNotNull();
  }

  @Test
  @DisplayName("Should return invalid for email invitation with non-matching email")
  void shouldReturnInvalidForEmailInvitationWithNonMatchingEmail() {
    // Arrange
    OrganizationInvitation invitation = new OrganizationInvitation(
        1L,
        "tenant-123",
        "email-code",
        InvitationType.EMAIL,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(1)
    );
    invitation.setInviteeEmail("invitee@example.com");
    invitation.setMaxUses(1);

    when(invitationRepository.findByInvitationCode("email-code"))
        .thenReturn(Optional.of(invitation));

    // Act
    InvitationValidationResult result = service.validateInvitation("email-code", "different@example.com");

    // Assert
    assertThat(result.valid()).isFalse();
    assertThat(result.message()).isEqualTo("Email does not match invitation");
    assertThat(result.organization()).isNull();
  }

  @Test
  @DisplayName("Should validate link invitation without email parameter")
  void shouldValidateLinkInvitationWithoutEmailParameter() {
    // Arrange
    OrganizationInvitation invitation = new OrganizationInvitation(
        1L,
        "tenant-123",
        "link-code",
        InvitationType.LINK,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(1)
    );

    when(invitationRepository.findByInvitationCode("link-code"))
        .thenReturn(Optional.of(invitation));
    when(organizationRepository.findById(1L))
        .thenReturn(Optional.of(organization));

    // Act
    InvitationValidationResult result = service.validateInvitation("link-code", null);

    // Assert
    assertThat(result.valid()).isTrue();
    assertThat(result.message()).isEqualTo("Invitation is valid");
  }

  @Test
  @DisplayName("Should validate email invitation without email parameter (basic validation)")
  void shouldValidateEmailInvitationWithoutEmailParameter() {
    // Arrange
    OrganizationInvitation invitation = new OrganizationInvitation(
        1L,
        "tenant-123",
        "email-code",
        InvitationType.EMAIL,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(1)
    );
    invitation.setInviteeEmail("invitee@example.com");
    invitation.setMaxUses(1);

    when(invitationRepository.findByInvitationCode("email-code"))
        .thenReturn(Optional.of(invitation));
    when(organizationRepository.findById(1L))
        .thenReturn(Optional.of(organization));

    // Act - Using overloaded method without email
    InvitationValidationResult result = service.validateInvitation("email-code");

    // Assert - Should pass basic validation (email check skipped when email param is null)
    assertThat(result.valid()).isTrue();
    assertThat(result.message()).isEqualTo("Invitation is valid");
  }

  // ========== Invitation Revocation Tests ==========

  @Test
  @DisplayName("Should revoke invitation successfully")
  void shouldRevokeInvitationSuccessfully() {
    // Arrange
    OrganizationInvitation invitation = new OrganizationInvitation(
        1L, // organizationId
        "tenant-123",
        "test-code",
        InvitationType.LINK,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(1)
    );

    when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

    // Act
    service.revokeInvitation(1L, adminUser);

    // Assert
    assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.REVOKED);
    verify(invitationRepository).save(invitation);
    verify(auditService).logInvitationRevoked(
        eq(adminUser.userId()),
        eq(adminUser.username()),
        eq(1L)
    );
  }

  @Test
  @DisplayName("Should throw exception when revoking invitation from different organization")
  void shouldThrowExceptionWhenRevokingInvitationFromDifferentOrganization() {
    // Arrange
    OrganizationInvitation invitation = new OrganizationInvitation(
        1L, // organizationId
        "different-tenant",
        "test-code",
        InvitationType.LINK,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(1)
    );

    when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

    // Act & Assert
    assertThatThrownBy(() -> service.revokeInvitation(1L, adminUser))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Access denied");

    verify(invitationRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should throw exception when revoking non-existent invitation")
  void shouldThrowExceptionWhenRevokingNonExistentInvitation() {
    // Arrange
    when(invitationRepository.findById(999L)).thenReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> service.revokeInvitation(999L, adminUser))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invitation not found");
  }

  // ========== Invitation Link Generation Tests ==========

  @Test
  @DisplayName("Should generate invitation link with correct format")
  void shouldGenerateInvitationLinkWithCorrectFormat() {
    // Arrange
    OrganizationInvitation invitation = new OrganizationInvitation(
        1L, // organizationId
        "tenant-123",
        "test-code-123",
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
    assertThat(result).isNotNull();
    assertThat(result.invitationCode()).isEqualTo("test-code-123");
    assertThat(result.fullUrl()).isEqualTo("https://auth.iqscaffold.com/join/test-code-123");
    assertThat(result.shortCode()).isNull(); // Only for CODE type
  }

  @Test
  @DisplayName("Should include short code for CODE type invitations")
  void shouldIncludeShortCodeForCodeTypeInvitations() {
    // Arrange
    OrganizationInvitation invitation = new OrganizationInvitation(
        1L, // organizationId
        "tenant-123",
        "ABCD1234",
        InvitationType.CODE,
        "admin",
        1L,
        "USER",
        LocalDateTime.now().plusDays(1)
    );

    when(invitationRepository.findById(1L)).thenReturn(Optional.of(invitation));

    // Act
    InvitationLinkResponse result = service.getInvitationLink(1L, adminUser);

    // Assert
    assertThat(result.shortCode()).isEqualTo("ABCD1234");
  }

  // ========== Get Organization Invitations Tests (Requirement 1.7) ==========

  @Test
  @DisplayName("Should get all invitations for organization without status filter")
  void shouldGetAllInvitationsForOrganizationWithoutStatusFilter() {
    // Arrange
    OrganizationInvitation invitation1 = new OrganizationInvitation(
        1L, "tenant-123", "code1", InvitationType.LINK, "admin", 1L, "USER",
        LocalDateTime.now().plusDays(1)
    );
    OrganizationInvitation invitation2 = new OrganizationInvitation(
        1L, "tenant-123", "code2", InvitationType.EMAIL, "admin", 1L, "USER",
        LocalDateTime.now().plusDays(2)
    );
    invitation2.setInviteeEmail("test@example.com");

    org.springframework.data.domain.PageRequest pageable = 
        org.springframework.data.domain.PageRequest.of(0, 10);
    org.springframework.data.domain.Page<OrganizationInvitation> page = 
        new org.springframework.data.domain.PageImpl<>(
            java.util.List.of(invitation1, invitation2),
            pageable,
            2
        );

    when(invitationRepository.findByOrganizationId(1L, pageable))
        .thenReturn(page);

    // Act
    org.springframework.data.domain.Page<OrganizationInvitationDto> result = 
        service.getOrganizationInvitations(null, pageable, adminUser);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(2);
    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getContent().get(0).invitationCode()).isEqualTo("code1");
    assertThat(result.getContent().get(1).invitationCode()).isEqualTo("code2");
    
    verify(invitationRepository).findByOrganizationId(1L, pageable);
    verify(invitationRepository, never()).findByOrganizationIdAndStatus(anyLong(), any(), any());
  }

  @Test
  @DisplayName("Should get invitations filtered by status")
  void shouldGetInvitationsFilteredByStatus() {
    // Arrange
    OrganizationInvitation invitation1 = new OrganizationInvitation(
        1L, "tenant-123", "code1", InvitationType.LINK, "admin", 1L, "USER",
        LocalDateTime.now().plusDays(1)
    );

    org.springframework.data.domain.PageRequest pageable = 
        org.springframework.data.domain.PageRequest.of(0, 10);
    org.springframework.data.domain.Page<OrganizationInvitation> page = 
        new org.springframework.data.domain.PageImpl<>(
            java.util.List.of(invitation1),
            pageable,
            1
        );

    when(invitationRepository.findByOrganizationIdAndStatus(1L, InvitationStatus.PENDING, pageable))
        .thenReturn(page);

    // Act
    org.springframework.data.domain.Page<OrganizationInvitationDto> result = 
        service.getOrganizationInvitations(InvitationStatus.PENDING, pageable, adminUser);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).status()).isEqualTo(InvitationStatus.PENDING);
    
    verify(invitationRepository).findByOrganizationIdAndStatus(1L, InvitationStatus.PENDING, pageable);
    verify(invitationRepository, never()).findByOrganizationId(anyLong(), any());
  }

  @Test
  @DisplayName("Should return empty page when no invitations exist")
  void shouldReturnEmptyPageWhenNoInvitationsExist() {
    // Arrange
    org.springframework.data.domain.PageRequest pageable = 
        org.springframework.data.domain.PageRequest.of(0, 10);
    org.springframework.data.domain.Page<OrganizationInvitation> emptyPage = 
        new org.springframework.data.domain.PageImpl<>(
            java.util.List.of(),
            pageable,
            0
        );

    when(invitationRepository.findByOrganizationId(1L, pageable))
        .thenReturn(emptyPage);

    // Act
    org.springframework.data.domain.Page<OrganizationInvitationDto> result = 
        service.getOrganizationInvitations(null, pageable, adminUser);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0);
  }

  @Test
  @DisplayName("Should verify user belongs to organization before listing invitations")
  void shouldVerifyUserBelongsToOrganizationBeforeListingInvitations() {
    // Arrange
    when(organizationRepository.findByTenantId("tenant-123"))
        .thenReturn(Optional.empty());

    org.springframework.data.domain.PageRequest pageable = 
        org.springframework.data.domain.PageRequest.of(0, 10);

    // Act & Assert
    assertThatThrownBy(() -> 
        service.getOrganizationInvitations(null, pageable, adminUser))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Organization not found");

    verify(invitationRepository, never()).findByOrganizationId(anyLong(), any());
    verify(invitationRepository, never()).findByOrganizationIdAndStatus(anyLong(), any(), any());
  }

  @Test
  @DisplayName("Should handle pagination correctly")
  void shouldHandlePaginationCorrectly() {
    // Arrange
    OrganizationInvitation invitation1 = new OrganizationInvitation(
        1L, "tenant-123", "code1", InvitationType.LINK, "admin", 1L, "USER",
        LocalDateTime.now().plusDays(1)
    );
    OrganizationInvitation invitation2 = new OrganizationInvitation(
        1L, "tenant-123", "code2", InvitationType.LINK, "admin", 1L, "USER",
        LocalDateTime.now().plusDays(2)
    );

    // First page
    org.springframework.data.domain.PageRequest pageable1 = 
        org.springframework.data.domain.PageRequest.of(0, 1);
    org.springframework.data.domain.Page<OrganizationInvitation> page1 = 
        new org.springframework.data.domain.PageImpl<>(
            java.util.List.of(invitation1),
            pageable1,
            2
        );

    // Second page
    org.springframework.data.domain.PageRequest pageable2 = 
        org.springframework.data.domain.PageRequest.of(1, 1);
    org.springframework.data.domain.Page<OrganizationInvitation> page2 = 
        new org.springframework.data.domain.PageImpl<>(
            java.util.List.of(invitation2),
            pageable2,
            2
        );

    when(invitationRepository.findByOrganizationId(1L, pageable1))
        .thenReturn(page1);
    when(invitationRepository.findByOrganizationId(1L, pageable2))
        .thenReturn(page2);

    // Act
    org.springframework.data.domain.Page<OrganizationInvitationDto> result1 = 
        service.getOrganizationInvitations(null, pageable1, adminUser);
    org.springframework.data.domain.Page<OrganizationInvitationDto> result2 = 
        service.getOrganizationInvitations(null, pageable2, adminUser);

    // Assert
    assertThat(result1.getContent()).hasSize(1);
    assertThat(result1.getTotalElements()).isEqualTo(2);
    assertThat(result1.getTotalPages()).isEqualTo(2);
    assertThat(result1.getContent().get(0).invitationCode()).isEqualTo("code1");

    assertThat(result2.getContent()).hasSize(1);
    assertThat(result2.getTotalElements()).isEqualTo(2);
    assertThat(result2.getTotalPages()).isEqualTo(2);
    assertThat(result2.getContent().get(0).invitationCode()).isEqualTo("code2");
  }
}
