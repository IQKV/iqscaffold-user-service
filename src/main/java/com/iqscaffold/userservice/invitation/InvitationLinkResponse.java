package com.iqscaffold.userservice.invitation;

import java.time.LocalDateTime;

/**
 * Response DTO for invitation link generation.
 *
 * @param invitationCode Unique invitation code
 * @param fullUrl        Full invitation URL (e.g., https://auth.iqkv.dev/join/{code})
 * @param shortCode      Short code for manual entry (only for CODE type, null otherwise)
 * @param expiresAt      Expiration timestamp
 */
public record InvitationLinkResponse(
    String invitationCode,
    String fullUrl,
    String shortCode,
    LocalDateTime expiresAt
) {
}
