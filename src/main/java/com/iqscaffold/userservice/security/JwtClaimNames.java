package com.iqscaffold.userservice.security;

/**
 * Constants for JWT claim names used throughout the application.
 *
 * <p>These claim names are used when creating and parsing JWT tokens to ensure
 * consistency across the application.
 */
public final class JwtClaimNames {

  /**
   * Standard JWT claim for subject (user ID).
   */
  public static final String SUBJECT = "sub";

  /**
   * Username claim.
   */
  public static final String USERNAME = "username";

  /**
   * Email claim.
   */
  public static final String EMAIL = "email";

  /**
   * Authorities (roles) claim.
   */
  public static final String AUTHORITIES = "authorities";

  /**
   * Permissions claim.
   */
  public static final String PERMISSIONS = "permissions";

  /**
   * Tenant ID claim.
   */
  public static final String TENANT_ID = "tenant_id";

  /**
   * Organization ID claim.
   */
  public static final String ORGANIZATION_ID = "organizationId";

  /**
   * First name claim.
   */
  public static final String FIRST_NAME = "firstName";

  /**
   * Last name claim.
   */
  public static final String LAST_NAME = "lastName";

  /**
   * Preferred locale claim.
   */
  public static final String PREFERRED_LOCALE = "preferred_locale";

  /**
   * Token type claim.
   */
  public static final String TYPE = "type";

  /**
   * Issued at claim.
   */
  public static final String ISSUED_AT = "iat";

  /**
   * Expiration time claim.
   */
  public static final String EXPIRATION = "exp";

  /**
   * JWT ID claim.
   */
  public static final String JWT_ID = "jti";

  /**
   * Issuer claim.
   */
  public static final String ISSUER = "iss";

  private JwtClaimNames() {
    // Utility class - prevent instantiation
  }
}
