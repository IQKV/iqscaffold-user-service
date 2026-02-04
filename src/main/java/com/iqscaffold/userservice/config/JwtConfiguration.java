package com.iqscaffold.userservice.config;

import java.time.Duration;

import com.iqscaffold.userservice.authentication.JwtKeyManagementService;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT configuration for token generation and validation.
 * Uses RSA256 algorithm with key rotation support.
 */
@Configuration
@ConfigurationProperties(prefix = "iqscaffold.auth.jwt")
public class JwtConfiguration {

  private Duration accessTokenExpiry = Duration.ofMinutes(15);
  private Duration refreshTokenExpiry = Duration.ofDays(7);
  private String issuer = "iqscaffold-user-service";
  private String secretKey;
  private String algorithm = "HS256";

  private final JwtKeyManagementService keyManagementService;

  public JwtConfiguration(
      @org.springframework.beans.factory.annotation.Autowired(required = false) final JwtKeyManagementService keyManagementService) {
    this.keyManagementService = keyManagementService;
  }

  @Bean
  public JWKSource<SecurityContext> jwkSource() {
    if (isSymmetricConfigured()) {
      var key = new com.nimbusds.jose.jwk.OctetSequenceKey.Builder(secretKey.getBytes())
          .algorithm(com.nimbusds.jose.JWSAlgorithm.parse(algorithm))
          .keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE)
          .build();
      return new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(key));
    }
    if (keyManagementService == null) {
      throw new IllegalStateException("Neither secret-key nor JwtKeyManagementService is available");
    }
    return (jwkSelector, context) -> jwkSelector.select(keyManagementService.getJwkSet());
  }

  @Bean
  public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
    return new NimbusJwtEncoder(jwkSource);
  }

  @Bean
  public JwtDecoder jwtDecoder() {
    if (isSymmetricConfigured()) {
      javax.crypto.SecretKey key = new javax.crypto.spec.SecretKeySpec(
          secretKey.getBytes(), "Hmac" + algorithm.substring(2));
      return NimbusJwtDecoder.withSecretKey(key).build();
    }
    // Use JWK Set URI for validation (supports multiple keys during rotation)
    return NimbusJwtDecoder.withJwkSetUri("http://localhost:8080/.well-known/jwks.json").build();
  }

  private boolean isSymmetricConfigured() {
    return secretKey != null && !secretKey.isBlank() && !"change-me-in-production".equals(secretKey);
  }

  // Getters and setters for configuration properties
  public Duration getAccessTokenExpiry() {
    return accessTokenExpiry;
  }

  public void setAccessTokenExpiry(Duration accessTokenExpiry) {
    this.accessTokenExpiry = accessTokenExpiry;
  }

  public Duration getRefreshTokenExpiry() {
    return refreshTokenExpiry;
  }

  public void setRefreshTokenExpiry(Duration refreshTokenExpiry) {
    this.refreshTokenExpiry = refreshTokenExpiry;
  }

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public void setAlgorithm(String algorithm) {
    this.algorithm = algorithm;
  }
}
