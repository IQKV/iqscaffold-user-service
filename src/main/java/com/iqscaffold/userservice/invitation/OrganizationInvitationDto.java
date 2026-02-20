package com.iqscaffold.userservice.invitation;

import java.time.LocalDateTime;

/**
 * DTO for OrganizationInvitation entity.
 * Used for API responses to avoid exposing internal entity structure.
 *
 * @param id                   Invitation ID
 * @param organizationId       Organization ID
 * @param organizationName     Organization name (joined from Organization entity)
 * @param tenantId             Tenant ID
 * @param invitationCode       Unique invitation code
 * @param type                 Invitation type (EMAIL, LINK, CODE)
 * @param inviteeEmail         Email address for EMAIL type invitations
 * @param invitedByUsername    Username of the user who created the invitation
 * @param authority            Authority to assign to new user
 * @param status               Current invitation status
 * @param expiresAt            Expiration timestamp
 * @param acceptedAt           Timestamp when invitation was accepted
 * @param maxUses              Maximum number of uses (null = unlimited)
 * @param currentUses          Current number of uses
 * @param createdAt            Creation timestamp
 */
public record OrganizationInvitationDto(
    Long id,
    Long organizationId,
    String organizationName,
    String tenantId,
    String invitationCode,
    InvitationType type,
    String inviteeEmail,
    String invitedByUsername,
    String authority,
    InvitationStatus status,
    LocalDateTime expiresAt,
    LocalDateTime acceptedAt,
    Integer maxUses,
    Integer currentUses,
    LocalDateTime createdAt
) {
  /**
   * Create DTO from entity.
   */
  public static OrganizationInvitationDto fromEntity(OrganizationInvitation invitation, String organizationName) {
    return new OrganizationInvitationDto(
        invitation.getId(),
        invitation.getOrganizationId(),
        organizationName,
        invitation.getTenantId(),
        invitation.getInvitationCode(),
        invitation.getType(),
        invitation.getInviteeEmail(),
        invitation.getInvitedByUsername(),
        invitation.getAuthority(),
        invitation.getStatus(),
        invitation.getExpiresAt(),
        invitation.getAcceptedAt(),
        invitation.getMaxUses(),
        invitation.getCurrentUses(),
        invitation.getCreatedAt()
    );
  }

  /**
   * Check if invitation is currently valid.
   */
  public boolean isValid() {
    return status == InvitationStatus.PENDING
           && LocalDateTime.now().isBefore(expiresAt)
           && !hasReachedMaxUses();
  }

  /**
   * Check if invitation has reached max uses.
   */
  public boolean hasReachedMaxUses() {
    return maxUses != null && currentUses >= maxUses;
  }
}
