package com.iqscaffold.userservice.invitation;

import java.util.List;

/**
 * Response DTO for signup with invitation.
 *
 * @param userId           Created user ID
 * @param username         Username
 * @param email            Email address
 * @param tenantId         Tenant ID
 * @param organizationName Organization name
 * @param authorities      List of assigned authority names
 * @param tokens           JWT tokens (access and refresh)
 * @param message          Success message
 */
public record SignupWithInvitationResponse(
    Long userId,
    String username,
    String email,
    String tenantId,
    String organizationName,
    List<String> authorities,
    TokenResponse tokens,
    String message
) {
}
