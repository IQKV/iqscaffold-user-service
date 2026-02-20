package com.iqscaffold.userservice.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.iqscaffold.userservice.organization.Organization;
import com.iqscaffold.userservice.organization.OrganizationRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for InvitationSignupService.
 * Tests focus on the getOrganizationPreview method.
 */
@ExtendWith(MockitoExtension.class)
class InvitationSignupServiceTest {

  @Mock
  private InvitationRepository invitationRepository;

  @Mock
  private OrganizationRepository organizationRepository;

  private InvitationSignupService invitationSignupService;

  @BeforeEach
  void setUp() {
    // Create service with minimal dependencies for testing getOrganizationPreview
    invitationSignupService = new InvitationSignupService(
        null, // invitationService - not needed for preview
        invitationRepository,
        null, // userRepository - not needed for preview
        organizationRepository,
        null, // tenantRepository - not needed for preview
        null, // authorityRepository - not needed for preview
        null, // passwordEncoder - not needed for preview
        null, // jwtService - not needed for preview
        null, // jwtConfiguration - not needed for preview
        null, // emailVerificationService - not needed for preview
        null, // invitationEmailService - not needed for preview
        null, // auditService - not needed for preview
        null  // platformConfig - not needed for preview
    );
  }

  @Test
  void shouldReturnOrganizationPreviewWithAllFields() {
    // Given: valid invitation and organization
    String invitationCode = "test-invitation-code-12345678901234567890";
    LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
    
    OrganizationInvitation invitation = new OrganizationInvitation();
    invitation.setInvitationCode(invitationCode);
    invitation.setOrganizationId(1L);
    invitation.setInvitedByUsername("admin_user");
    invitation.setExpiresAt(expiresAt);
    
    Organization organization = new Organization("Tech Corp", "tenant_123");
    organization.setDescription("A leading technology company");
    organization.setIndustry("Technology");
    organization.setCity("San Francisco");
    organization.setCountry("USA");
    
    when(invitationRepository.findByInvitationCode(invitationCode))
        .thenReturn(Optional.of(invitation));
    when(organizationRepository.findById(1L))
        .thenReturn(Optional.of(organization));

    // When: getting organization preview
    OrganizationPreviewDto preview = invitationSignupService.getOrganizationPreview(invitationCode);

    // Then: all fields should be populated correctly
    assertThat(preview).isNotNull();
    assertThat(preview.name()).isEqualTo("Tech Corp");
    assertThat(preview.description()).isEqualTo("A leading technology company");
    assertThat(preview.industry()).isEqualTo("Technology");
    assertThat(preview.city()).isEqualTo("San Francisco");
    assertThat(preview.country()).isEqualTo("USA");
    assertThat(preview.invitedByUsername()).isEqualTo("admin_user");
    assertThat(preview.invitationExpiresAt()).isEqualTo(expiresAt);
  }

  @Test
  void shouldReturnOrganizationPreviewWithNullOptionalFields() {
    // Given: invitation and organization with minimal fields
    String invitationCode = "test-code-minimal-12345678901234567890";
    LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
    
    OrganizationInvitation invitation = new OrganizationInvitation();
    invitation.setInvitationCode(invitationCode);
    invitation.setOrganizationId(2L);
    invitation.setInvitedByUsername("inviter");
    invitation.setExpiresAt(expiresAt);
    
    Organization organization = new Organization("Minimal Org", "tenant_456");
    // Leave description, industry, city, country as null
    
    when(invitationRepository.findByInvitationCode(invitationCode))
        .thenReturn(Optional.of(invitation));
    when(organizationRepository.findById(2L))
        .thenReturn(Optional.of(organization));

    // When: getting organization preview
    OrganizationPreviewDto preview = invitationSignupService.getOrganizationPreview(invitationCode);

    // Then: required fields should be present, optional fields can be null
    assertThat(preview).isNotNull();
    assertThat(preview.name()).isEqualTo("Minimal Org");
    assertThat(preview.invitedByUsername()).isEqualTo("inviter");
    assertThat(preview.invitationExpiresAt()).isEqualTo(expiresAt);
    // Optional fields can be null
    assertThat(preview.description()).isNull();
    assertThat(preview.industry()).isNull();
    assertThat(preview.city()).isNull();
    assertThat(preview.country()).isNull();
  }

  @Test
  void shouldThrowExceptionWhenInvitationNotFound() {
    // Given: non-existent invitation code
    String invitationCode = "non-existent-code-12345678901234567890";
    
    when(invitationRepository.findByInvitationCode(invitationCode))
        .thenReturn(Optional.empty());

    // When/Then: should throw exception
    assertThatThrownBy(() -> invitationSignupService.getOrganizationPreview(invitationCode))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invitation not found");
  }

  @Test
  void shouldThrowExceptionWhenOrganizationNotFound() {
    // Given: valid invitation but organization doesn't exist
    String invitationCode = "orphan-invitation-12345678901234567890";
    
    OrganizationInvitation invitation = new OrganizationInvitation();
    invitation.setInvitationCode(invitationCode);
    invitation.setOrganizationId(999L);
    invitation.setInvitedByUsername("admin");
    invitation.setExpiresAt(LocalDateTime.now().plusDays(1));
    
    when(invitationRepository.findByInvitationCode(invitationCode))
        .thenReturn(Optional.of(invitation));
    when(organizationRepository.findById(999L))
        .thenReturn(Optional.empty());

    // When/Then: should throw exception
    assertThatThrownBy(() -> invitationSignupService.getOrganizationPreview(invitationCode))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Organization not found");
  }

  @Test
  void shouldReturnPreviewForExpiredInvitation() {
    // Given: expired invitation (preview should still work)
    String invitationCode = "expired-invitation-12345678901234567890";
    LocalDateTime expiresAt = LocalDateTime.now().minusDays(1); // Expired
    
    OrganizationInvitation invitation = new OrganizationInvitation();
    invitation.setInvitationCode(invitationCode);
    invitation.setOrganizationId(3L);
    invitation.setInvitedByUsername("admin");
    invitation.setExpiresAt(expiresAt);
    invitation.setStatus(InvitationStatus.EXPIRED);
    
    Organization organization = new Organization("Expired Org", "tenant_789");
    organization.setDescription("Test organization");
    
    when(invitationRepository.findByInvitationCode(invitationCode))
        .thenReturn(Optional.of(invitation));
    when(organizationRepository.findById(3L))
        .thenReturn(Optional.of(organization));

    // When: getting organization preview
    OrganizationPreviewDto preview = invitationSignupService.getOrganizationPreview(invitationCode);

    // Then: preview should still be returned (validation happens elsewhere)
    assertThat(preview).isNotNull();
    assertThat(preview.name()).isEqualTo("Expired Org");
    assertThat(preview.invitationExpiresAt()).isEqualTo(expiresAt);
  }
}
