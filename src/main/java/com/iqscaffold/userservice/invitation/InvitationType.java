package com.iqscaffold.userservice.invitation;

/**
 * Enum representing the type of organization invitation.
 *
 * <ul>
 *   <li><strong>EMAIL</strong> - Sent via email to a specific address, single-use only</li>
 *   <li><strong>LINK</strong> - Shareable link with configurable max uses</li>
 *   <li><strong>CODE</strong> - Short alphanumeric code for manual entry (6-8 characters)</li>
 * </ul>
 */
public enum InvitationType {
  /**
   * Email invitation sent to a specific email address.
   * Requires invitee_email field and has max_uses=1.
   */
  EMAIL,

  /**
   * Shareable invitation link with optional usage limits.
   * Can have unlimited uses (max_uses=null) or a specific limit.
   */
  LINK,

  /**
   * Short alphanumeric code for manual entry.
   * Typically 6-8 characters for easy typing.
   */
  CODE
}
