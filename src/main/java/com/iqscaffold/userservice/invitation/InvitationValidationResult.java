package com.iqscaffold.userservice.invitation;

/**
 * Result DTO for invitation validation.
 *
 * @param valid        Whether the invitation is valid
 * @param message      Validation message (error message if invalid)
 * @param organization Organization preview (null if invalid)
 */
public record InvitationValidationResult(
    boolean valid,
    String message,
    OrganizationPreviewDto organization
) {
}
