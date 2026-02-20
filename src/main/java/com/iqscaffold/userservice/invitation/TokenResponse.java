package com.iqscaffold.userservice.invitation;

/**
 * DTO for JWT token response.
 *
 * @param accessToken  JWT access token
 * @param refreshToken JWT refresh token
 * @param expiresIn    Access token expiration time in seconds
 */
public record TokenResponse(
    String accessToken,
    String refreshToken,
    Long expiresIn
) {
}
