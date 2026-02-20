package com.iqscaffold.userservice.invitation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating an organization invitation.
 *
 * @param type              The type of invitation (EMAIL, LINK, CODE)
 * @param inviteeEmail      Email address for EMAIL type invitations (required for EMAIL)
 * @param authority         Authority to assign to new user (defaults to USER if not specified)
 * @param expirationHours   Hours until invitation expires (1-720, i.e., 1 hour to 30 days)
 * @param maxUses           Maximum number of uses (null = unlimited, only for LINK type)
 * @param customMessage     Optional custom message to include in invitation email
 */
public record CreateInvitationRequest(
    @NotNull(message = "Invitation type is required")
    InvitationType type,

    @Email(message = "Invalid email address")
    String inviteeEmail,

    String authority,

    @NotNull(message = "Expiration hours is required")
    @Min(value = 1, message = "Expiration must be at least 1 hour")
    @Max(value = 720, message = "Expiration cannot exceed 720 hours (30 days)")
    Integer expirationHours,

    @Min(value = 1, message = "Max uses must be at least 1 if specified")
    Integer maxUses,

    String customMessage
) {
  /**
   * Compact constructor with validation and defaults.
   */
  public CreateInvitationRequest {
    // Set default authority if not provided
    if (authority == null || authority.isBlank()) {
      authority = "USER";
    }
  }

  /**
   * Validate that email is provided for EMAIL type invitations.
   */
  public boolean isValid() {
    if (type == InvitationType.EMAIL) {
      return inviteeEmail != null && !inviteeEmail.isBlank();
    }
    return true;
  }

  /**
   * Get validation error message if invalid.
   */
  public String getValidationError() {
    if (type == InvitationType.EMAIL && (inviteeEmail == null || inviteeEmail.isBlank())) {
      return "Email address is required for EMAIL type invitations";
    }
    return null;
  }
}
