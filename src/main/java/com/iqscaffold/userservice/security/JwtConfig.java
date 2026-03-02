package com.iqscaffold.userservice.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * JWT Configuration for OAuth2 Resource Server.
 *
 * <p>Configures the JWT decoder to validate and parse JWT tokens using HMAC-SHA256 algorithm.
 * The secret key is shared between the user-service (issuer) and other services (consumers).
 *
 * <h3>Security Considerations:</h3>
 * <ul>
 *   <li>Secret key must be at least 256 bits (32 bytes) for HS256</li>
 *   <li>Secret key should be stored securely (environment variable, secrets manager)</li>
 *   <li>Same secret key must be used across all services in the platform</li>
 *   <li>Rotate secret keys periodically for enhanced security</li>
 * </ul>
 */
@Configuration
public class JwtConfig {

  @Value("${iqscaffold.auth.jwt.secret-key}")
  private String secretKey;

  /**
   * JWT Decoder bean for validating and parsing JWT tokens.
   *
   * <p>Uses HMAC-SHA256 (HS256) algorithm with a shared secret key.
   * The decoder automatically validates:
   * <ul>
   *   <li>JWT signature</li>
   *   <li>Token expiration (exp claim)</li>
   *   <li>Token not-before time (nbf claim)</li>
   * </ul>
   *
   * @return configured JWT decoder
   */
  @Bean
  public JwtDecoder jwtDecoder() {
    SecretKey key = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(key)
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
  }
}
