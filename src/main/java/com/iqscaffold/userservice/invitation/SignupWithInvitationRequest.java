package com.iqscaffold.userservice.invitation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for signing up with an invitation.
 *
 * @param username        Username (3-50 characters)
 * @param email           Email address
 * @param password        Password (8-100 characters)
 * @param firstName       First name
 * @param lastName        Last name
 * @param preferredLocale Preferred locale (optional, defaults to "en")
 */
public record SignupWithInvitationRequest(
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    String username,

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    String password,

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    String firstName,

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    String lastName,

    String preferredLocale
) {
  /**
   * Constructor with default locale.
   */
  public SignupWithInvitationRequest {
    if (preferredLocale == null || preferredLocale.isBlank()) {
      preferredLocale = "en";
    }
  }
}
