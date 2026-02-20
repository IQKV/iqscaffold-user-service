package com.iqscaffold.userservice.invitation;

/**
 * Enum representing the status of an organization invitation.
 *
 * <ul>
 *   <li><strong>PENDING</strong> - Active invitation that can be used</li>
 *   <li><strong>ACCEPTED</strong> - Successfully used (for single-use invitations)</li>
 *   <li><strong>EXPIRED</strong> - Past expiration date</li>
 *   <li><strong>REVOKED</strong> - Manually cancelled by admin</li>
 * </ul>
 */
public enum InvitationStatus {
  /**
   * Invitation is active and can be used for signup.
   */
  PENDING,

  /**
   * Invitation has been successfully used.
   * Applies to single-use email invitations.
   */
  ACCEPTED,

  /**
   * Invitation has passed its expiration date.
   * Set automatically by scheduled cleanup task.
   */
  EXPIRED,

  /**
   * Invitation has been manually revoked by an administrator.
   */
  REVOKED
}
