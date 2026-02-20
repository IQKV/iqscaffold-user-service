package com.iqscaffold.userservice.invitation;

import java.time.LocalDateTime;

/**
 * DTO for organization preview shown to users before signup.
 * Contains public information about the organization.
 *
 * @param name                   Organization name
 * @param description            Organization description
 * @param industry               Industry/sector
 * @param city                   City location
 * @param country                Country location
 * @param invitedByUsername      Username of the user who created the invitation
 * @param invitationExpiresAt    When the invitation expires
 */
public record OrganizationPreviewDto(
    String name,
    String description,
    String industry,
    String city,
    String country,
    String invitedByUsername,
    LocalDateTime invitationExpiresAt
) {
  /**
   * Get formatted location string.
   */
  public String getLocation() {
    if (city != null && country != null) {
      return city + ", " + country;
    }
    return city != null ? city : (country != null ? country : "");
  }
}
