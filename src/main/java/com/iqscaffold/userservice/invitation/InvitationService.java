package com.iqscaffold.userservice.invitation;

import com.iqscaffold.userservice.organization.Organization;
import com.iqscaffold.userservice.organization.OrganizationRepository;
import com.iqscaffold.userservice.security.SecurityAuditService;
import com.iqscaffold.userservice.shared.Authority;
import com.iqscaffold.userservice.shared.AuthorityRepository;
import com.iqscaffold.userservice.usermanagement.UserContext;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing organization invitations.
 * Handles invitation creation, validation, revocation, and cleanup.
 */
@Service
@Transactional
public class InvitationService {

  private static final Logger logger = LoggerFactory.getLogger(InvitationService.class);

  // Secure random for cryptographic code generation
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  // Invitation code configuration
  private static final int INVITATION_CODE_BYTES = 32; // 32 bytes = 256 bits
  private static final int SHORT_CODE_LENGTH = 8; // For CODE type invitations

  // Rate limiting configuration
  private static final int MAX_INVITATIONS_PER_HOUR = 10;

  private final InvitationRepository invitationRepository;
  private final OrganizationRepository organizationRepository;
  private final AuthorityRepository authorityRepository;
  private final SecurityAuditService auditService;
  private final InvitationEmailService emailService;

  public InvitationService(
      InvitationRepository invitationRepository,
      OrganizationRepository organizationRepository,
      AuthorityRepository authorityRepository,
      SecurityAuditService auditService,
      InvitationEmailService emailService
  ) {
    this.invitationRepository = invitationRepository;
    this.organizationRepository = organizationRepository;
    this.authorityRepository = authorityRepository;
    this.auditService = auditService;
    this.emailService = emailService;
  }

  /**
   * Generate a cryptographically secure invitation code.
   * Uses SecureRandom to generate minimum 32 characters (Base64 URL-safe encoding).
   *
   * @return Secure invitation code (minimum 32 characters)
   */
  private String generateSecureInvitationCode() {
    byte[] randomBytes = new byte[INVITATION_CODE_BYTES];
    SECURE_RANDOM.nextBytes(randomBytes);
    // Use URL-safe Base64 encoding without padding
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }

  /**
   * Generate a short alphanumeric code for CODE type invitations.
   * Uses SecureRandom for security.
   *
   * @return Short code (6-8 characters)
   */
  private String generateShortCode() {
    String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Exclude ambiguous characters
    StringBuilder code = new StringBuilder(SHORT_CODE_LENGTH);
    for (int i = 0; i < SHORT_CODE_LENGTH; i++) {
      int index = SECURE_RANDOM.nextInt(chars.length());
      code.append(chars.charAt(index));
    }
    return code.toString();
  }

  /**
   * Create a new organization invitation.
   *
   * @param request     Invitation creation request
   * @param currentUser Current authenticated user
   * @return Created invitation DTO
   * @throws IllegalArgumentException if validation fails
   * @throws IllegalStateException    if rate limit exceeded or organization inactive
   */
  public OrganizationInvitationDto createInvitation(
      CreateInvitationRequest request,
      UserContext currentUser
  ) {
    // Validate request
    if (!request.isValid()) {
      throw new IllegalArgumentException(request.getValidationError());
    }

    // Get organization
    Organization organization = organizationRepository.findByTenantId(currentUser.tenantId())
        .orElseThrow(() -> new IllegalStateException("Organization not found"));

    // Validate organization is active
    if (!organization.isActive()) {
      throw new IllegalStateException("Organization is not active");
    }

    // Check rate limit
    LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
    long recentInvitationCount = invitationRepository.countInvitationsCreatedSince(
        organization.getId(),
        oneHourAgo
    );

    if (recentInvitationCount >= MAX_INVITATIONS_PER_HOUR) {
      throw new IllegalStateException(
          "Rate limit exceeded. Maximum " + MAX_INVITATIONS_PER_HOUR + " invitations per hour"
      );
    }

    // Validate authority exists
    Authority authority = authorityRepository.findByName(request.authority())
        .orElseThrow(() -> new IllegalArgumentException("Invalid authority: " + request.authority()));

    // Generate invitation code
    String invitationCode = request.type() == InvitationType.CODE
        ? generateShortCode()
        : generateSecureInvitationCode();

    // Ensure code is unique (extremely unlikely collision, but check anyway)
    while (invitationRepository.existsByInvitationCode(invitationCode)) {
      invitationCode = request.type() == InvitationType.CODE
          ? generateShortCode()
          : generateSecureInvitationCode();
    }

    // Calculate expiration time
    LocalDateTime expiresAt = LocalDateTime.now().plusHours(request.expirationHours());

    // Create invitation entity
    OrganizationInvitation invitation = new OrganizationInvitation(
        organization.getId(),
        currentUser.tenantId(),
        invitationCode,
        request.type(),
        currentUser.username(),
        currentUser.userId(),
        request.authority(),
        expiresAt
    );

    // Set email for EMAIL type
    if (request.type() == InvitationType.EMAIL) {
      invitation.setInviteeEmail(request.inviteeEmail());
      invitation.setMaxUses(1); // Email invitations are always single-use
    } else if (request.maxUses() != null) {
      invitation.setMaxUses(request.maxUses());
    }

    // Save invitation
    invitation = invitationRepository.save(invitation);

    // Create audit log
    auditService.logInvitationCreated(
        currentUser.userId(),
        currentUser.username(),
        invitation.getId(),
        invitation.getType().toString()
    );

    logger.info("Created {} invitation {} for organization {} by user {}",
        invitation.getType(),
        invitation.getId(),
        organization.getName(),
        currentUser.username()
    );

    // Send email if type is EMAIL
    if (request.type() == InvitationType.EMAIL) {
      try {
        emailService.sendInvitationEmail(
            request.inviteeEmail(),
            invitation.getInvitationCode(),
            organization,
            currentUser.username(),
            invitation.getExpiresAt()
        );
        logger.info("Invitation email sent to {} for invitation {}",
            request.inviteeEmail(),
            invitation.getId()
        );
      } catch (Exception e) {
        // Log error but don't fail invitation creation
        logger.error("Failed to send invitation email to {} for invitation {}",
            request.inviteeEmail(),
            invitation.getId(),
            e
        );
      }
    }

    return OrganizationInvitationDto.fromEntity(invitation, organization.getName());
  }

  /**
   * Get paginated list of invitations for an organization.
   *
   * @param status      Optional status filter
   * @param pageable    Pagination parameters
   * @param currentUser Current authenticated user
   * @return Page of invitation DTOs
   */
  public Page<OrganizationInvitationDto> getOrganizationInvitations(
      InvitationStatus status,
      Pageable pageable,
      UserContext currentUser
  ) {
    // Get organization
    Organization organization = organizationRepository.findByTenantId(currentUser.tenantId())
        .orElseThrow(() -> new IllegalStateException("Organization not found"));

    // Query invitations
    Page<OrganizationInvitation> invitations = status != null
        ? invitationRepository.findByOrganizationIdAndStatus(organization.getId(), status, pageable)
        : invitationRepository.findByOrganizationId(organization.getId(), pageable);

    // Convert to DTOs
    return invitations.map(inv -> OrganizationInvitationDto.fromEntity(inv, organization.getName()));
  }

  /**
   * Revoke an invitation.
   *
   * @param invitationId Invitation ID to revoke
   * @param currentUser  Current authenticated user
   * @throws IllegalArgumentException if invitation not found
   * @throws IllegalStateException    if user doesn't have permission
   */
  public void revokeInvitation(Long invitationId, UserContext currentUser) {
    // Find invitation
    OrganizationInvitation invitation = invitationRepository.findById(invitationId)
        .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

    // Verify user belongs to same organization
    if (!invitation.getTenantId().equals(currentUser.tenantId())) {
      throw new IllegalStateException("Access denied to this invitation");
    }

    // Revoke invitation
    invitation.revoke();
    invitationRepository.save(invitation);

    // Create audit log
    auditService.logInvitationRevoked(
        currentUser.userId(),
        currentUser.username(),
        invitation.getId()
    );

    logger.info("Revoked invitation {} by user {}", invitationId, currentUser.username());
  }

  /**
   * Generate invitation link for an invitation.
   *
   * @param invitationId Invitation ID
   * @param currentUser  Current authenticated user
   * @return Invitation link response
   * @throws IllegalArgumentException if invitation not found
   * @throws IllegalStateException    if user doesn't have permission
   */
  public InvitationLinkResponse getInvitationLink(Long invitationId, UserContext currentUser) {
    // Find invitation
    OrganizationInvitation invitation = invitationRepository.findById(invitationId)
        .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

    // Verify user belongs to same organization
    if (!invitation.getTenantId().equals(currentUser.tenantId())) {
      throw new IllegalStateException("Access denied to this invitation");
    }

    // Generate full URL
    String fullUrl = "https://app.iqscaffold.com/join/" + invitation.getInvitationCode();

    // For CODE type, also provide short code
    String shortCode = invitation.getType() == InvitationType.CODE
        ? invitation.getInvitationCode()
        : null;

    return new InvitationLinkResponse(
        invitation.getInvitationCode(),
        fullUrl,
        shortCode,
        invitation.getExpiresAt()
    );
  }

  /**
   * Validate an invitation code.
   * Used by public signup flow to check if invitation is valid before showing signup form.
   *
   * @param invitationCode Invitation code to validate
   * @return Validation result with organization preview
   */
  public InvitationValidationResult validateInvitation(String invitationCode) {
    return validateInvitation(invitationCode, null);
  }

  /**
   * Validate an invitation code with optional email verification.
   * Used by signup flow to check if invitation is valid and email matches (for EMAIL type).
   *
   * @param invitationCode Invitation code to validate
   * @param email          Optional email to verify (required for EMAIL type invitations)
   * @return Validation result with organization preview
   */
  public InvitationValidationResult validateInvitation(String invitationCode, String email) {
    // Find invitation
    OrganizationInvitation invitation = invitationRepository.findByInvitationCode(invitationCode)
        .orElse(null);

    if (invitation == null) {
      return new InvitationValidationResult(false, "Invitation not found", null);
    }

    // Check status
    if (invitation.getStatus() != InvitationStatus.PENDING) {
      String message = switch (invitation.getStatus()) {
        case EXPIRED -> "Invitation has expired";
        case REVOKED -> "Invitation has been revoked";
        case ACCEPTED -> "Invitation has already been used";
        case PENDING -> "Invitation is pending"; // Should not reach here due to if condition
      };
      return new InvitationValidationResult(false, message, null);
    }

    // Check expiration
    if (invitation.isExpired()) {
      return new InvitationValidationResult(false, "Invitation has expired", null);
    }

    // Check usage limit (for link invitations with max_uses)
    if (invitation.hasReachedMaxUses()) {
      return new InvitationValidationResult(false, "Invitation has reached maximum uses", null);
    }

    // For email invitations, verify email matches
    if (email != null && invitation.getType() == InvitationType.EMAIL) {
      if (!invitation.emailMatches(email)) {
        return new InvitationValidationResult(false, "Email does not match invitation", null);
      }
    }

    // Get organization preview
    Organization organization = organizationRepository.findById(invitation.getOrganizationId())
        .orElseThrow(() -> new IllegalStateException("Organization not found"));

    OrganizationPreviewDto preview = new OrganizationPreviewDto(
        organization.getName(),
        organization.getDescription(),
        organization.getIndustry(),
        organization.getCity(),
        organization.getCountry(),
        invitation.getInvitedByUsername(),
        invitation.getExpiresAt()
    );

    return new InvitationValidationResult(true, "Invitation is valid", preview);
  }

  /**
   * Scheduled cleanup of expired invitations.
   * Runs every hour to mark expired PENDING invitations as EXPIRED.
   */
  @Scheduled(cron = "0 0 * * * *") // Every hour at minute 0
  public void cleanupExpiredInvitations() {
    try {
      LocalDateTime now = LocalDateTime.now();
      List<OrganizationInvitation> expiredInvitations = invitationRepository.findExpiredInvitations(now);

      int count = 0;
      for (OrganizationInvitation invitation : expiredInvitations) {
        invitation.markAsExpired();
        invitationRepository.save(invitation);
        count++;
      }

      if (count > 0) {
        logger.info("Marked {} expired invitations as EXPIRED", count);
      }
    } catch (Exception e) {
      logger.error("Failed to cleanup expired invitations", e);
    }
  }
}
