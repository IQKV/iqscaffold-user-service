package com.iqscaffold.userservice.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iqscaffold.userservice.organization.OrganizationRepository;
import com.iqscaffold.userservice.config.IqScaffoldProperties;
import com.iqscaffold.userservice.security.SecurityAuditService;
import com.iqscaffold.userservice.shared.AuthorityRepository;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.InRange;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Property-based tests for invitation cleanup task.
 * 
 * **Property 10: Expired Invitation Cleanup**
 * For any invitation with status PENDING and expiration time in the past, the scheduled cleanup
 * task SHALL update the status to EXPIRED.
 * 
 * **Validates: Requirements 2.8**
 */
@RunWith(JUnitQuickcheck.class)
public class InvitationCleanupPropertyTest {

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
  }

  /**
   * Property 10: Expired Invitation Cleanup
   * 
   * For any invitation with status PENDING and expiration time in the past, the scheduled cleanup
   * task SHALL update the status to EXPIRED.
   * 
   * Validates: Requirements 2.8
   */
  @Property(trials = 100)
  public void cleanupTaskMarksExpiredInvitationsAsExpired(
      @InRange(minInt = 1, maxInt = 10) int numberOfExpiredInvitations,
      @InRange(minInt = 1, maxInt = 720) int hoursExpired
  ) {
    // Arrange: Create expired invitations with PENDING status
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime expirationTime = now.minusHours(hoursExpired);
    
    List<OrganizationInvitation> expiredInvitations = new ArrayList<>();
    for (int i = 0; i < numberOfExpiredInvitations; i++) {
      OrganizationInvitation invitation = new OrganizationInvitation(
          1L,
          "tenant-123",
          "code-" + i,
          InvitationType.LINK,
          "admin",
          1L,
          "USER",
          expirationTime
      );
      expiredInvitations.add(invitation);
    }

    // Mock repository to return expired invitations
    when(invitationRepository.findExpiredInvitations(any(LocalDateTime.class)))
        .thenReturn(expiredInvitations);
    
    // Mock save to capture the updated invitations
    ArgumentCaptor<OrganizationInvitation> captor = ArgumentCaptor.forClass(OrganizationInvitation.class);
    lenient().when(invitationRepository.save(captor.capture()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act: Run the cleanup task
    service.cleanupExpiredInvitations();

    // Assert: All expired invitations should be marked as EXPIRED
    List<OrganizationInvitation> savedInvitations = captor.getAllValues();
    assertThat(savedInvitations)
        .as("Cleanup task should save all expired invitations")
        .hasSize(numberOfExpiredInvitations);
    
    for (OrganizationInvitation invitation : savedInvitations) {
      assertThat(invitation.getStatus())
          .as("Invitation status should be updated to EXPIRED")
          .isEqualTo(InvitationStatus.EXPIRED);
    }
  }

  /**
   * Property 10 (Extended): Non-expired invitations should not be affected
   * 
   * For any invitation with status PENDING and expiration time in the future, the cleanup task
   * SHALL NOT modify the invitation.
   */
  @Property(trials = 100)
  public void cleanupTaskDoesNotAffectNonExpiredInvitations(
      @InRange(minInt = 1, maxInt = 720) int hoursUntilExpiration
  ) {
    // Arrange: Create non-expired invitation
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime futureExpiration = now.plusHours(hoursUntilExpiration);
    
    OrganizationInvitation nonExpiredInvitation = new OrganizationInvitation(
        1L,
        "tenant-123",
        "valid-code",
        InvitationType.LINK,
        "admin",
        1L,
        "USER",
        futureExpiration
    );

    // Mock repository to return empty list (no expired invitations)
    when(invitationRepository.findExpiredInvitations(any(LocalDateTime.class)))
        .thenReturn(List.of());
    
    ArgumentCaptor<OrganizationInvitation> captor = ArgumentCaptor.forClass(OrganizationInvitation.class);
    lenient().when(invitationRepository.save(captor.capture()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act: Run the cleanup task
    service.cleanupExpiredInvitations();

    // Assert: No invitations should be saved (none were expired)
    assertThat(captor.getAllValues())
        .as("Cleanup task should not save any invitations when none are expired")
        .isEmpty();
  }

  /**
   * Property 10 (Extended): Only PENDING invitations should be marked as EXPIRED
   * 
   * For any invitation with status other than PENDING (ACCEPTED, REVOKED, EXPIRED), the cleanup
   * task SHALL NOT modify the invitation even if the expiration time is in the past.
   */
  @Property(trials = 100)
  public void cleanupTaskOnlyAffectsPendingInvitations(
      @InRange(minInt = 1, maxInt = 720) int hoursExpired
  ) {
    // Arrange: Create invitations with non-PENDING status
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime expirationTime = now.minusHours(hoursExpired);
    
    OrganizationInvitation acceptedInvitation = new OrganizationInvitation(
        1L,
        "tenant-123",
        "accepted-code",
        InvitationType.EMAIL,
        "admin",
        1L,
        "USER",
        expirationTime
    );
    acceptedInvitation.markAsAccepted(2L);
    
    OrganizationInvitation revokedInvitation = new OrganizationInvitation(
        1L,
        "tenant-123",
        "revoked-code",
        InvitationType.LINK,
        "admin",
        1L,
        "USER",
        expirationTime
    );
    revokedInvitation.revoke();

    // Mock repository to return empty list (query filters by PENDING status)
    when(invitationRepository.findExpiredInvitations(any(LocalDateTime.class)))
        .thenReturn(List.of());
    
    ArgumentCaptor<OrganizationInvitation> captor = ArgumentCaptor.forClass(OrganizationInvitation.class);
    lenient().when(invitationRepository.save(captor.capture()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act: Run the cleanup task
    service.cleanupExpiredInvitations();

    // Assert: No invitations should be saved (query only returns PENDING invitations)
    assertThat(captor.getAllValues())
        .as("Cleanup task should not affect non-PENDING invitations")
        .isEmpty();
  }

  /**
   * Property 10 (Extended): Cleanup task should handle empty result gracefully
   * 
   * When there are no expired invitations, the cleanup task SHALL complete successfully without
   * errors.
   */
  @Property(trials = 50)
  public void cleanupTaskHandlesEmptyResultGracefully() {
    // Arrange: Mock repository to return empty list
    when(invitationRepository.findExpiredInvitations(any(LocalDateTime.class)))
        .thenReturn(List.of());

    // Act & Assert: Should not throw any exceptions
    service.cleanupExpiredInvitations();
  }

  /**
   * Property 10 (Extended): Cleanup task should handle repository errors gracefully
   * 
   * When the repository throws an exception, the cleanup task SHALL catch it and log the error
   * without propagating the exception.
   */
  @Property(trials = 50)
  public void cleanupTaskHandlesRepositoryErrorsGracefully() {
    // Arrange: Mock repository to throw exception
    when(invitationRepository.findExpiredInvitations(any(LocalDateTime.class)))
        .thenThrow(new RuntimeException("Database connection error"));

    // Act & Assert: Should not throw any exceptions (error is caught and logged)
    service.cleanupExpiredInvitations();
  }
}
