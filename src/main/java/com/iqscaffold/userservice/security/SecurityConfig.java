package com.iqscaffold.userservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Security configuration for the User Service.
 * 
 * <p>Configures JWT-based authentication, CSRF protection, and rate limiting with
 * clear separation between public and protected endpoints.
 * 
 * <h3>Security Features</h3>
 * <ul>
 *   <li>JWT-based stateless authentication</li>
 *   <li>BCrypt password encoding (strength 12)</li>
 *   <li>Rate limiting on sensitive endpoints</li>
 *   <li>CSRF protection for web endpoints</li>
 *   <li>Security headers (HSTS, frame options, etc.)</li>
 * </ul>
 * 
 * <h3>Public Endpoints</h3>
 * <ul>
 *   <li><strong>Authentication:</strong> signup, login, validate, health</li>
 *   <li><strong>Password Management:</strong> forgot, reset</li>
 *   <li><strong>Email Verification:</strong> verify, resend, status</li>
 *   <li><strong>Self-Service:</strong> /api/v1/public/**</li>
 *   <li><strong>Infrastructure:</strong> actuator, swagger, jwks</li>
 * </ul>
 * 
 * <h3>Protected Endpoints</h3>
 * <ul>
 *   <li><strong>Token Management:</strong> refresh, logout (requires authentication)</li>
 *   <li><strong>Admin:</strong> /api/v1/admin/** (requires ADMIN or SUPER_ADMIN)</li>
 *   <li><strong>Tenants:</strong> /api/v1/tenants/** (requires SUPER_ADMIN)</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

  private final JwtDecoder jwtDecoder;
  private final RateLimitingFilter rateLimitingFilter;

  public SecurityConfig(final JwtDecoder jwtDecoder, final RateLimitingFilter rateLimitingFilter) {
    this.jwtDecoder = jwtDecoder;
    this.rateLimitingFilter = rateLimitingFilter;
  }

  /**
   * Password encoder bean using BCrypt with strength 12.
   * Higher strength provides better security at the cost of performance.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  /**
   * Main security filter chain configuration.
   * 
   * <p>Configures:
   * <ul>
   *   <li>CSRF protection (disabled for API endpoints)</li>
   *   <li>Stateless session management</li>
   *   <li>Public and protected endpoint authorization</li>
   *   <li>JWT-based OAuth2 resource server</li>
   *   <li>Rate limiting filter</li>
   *   <li>Security headers</li>
   * </ul>
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        // CSRF Configuration
        .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            // Disable CSRF for stateless API endpoints (using JWT tokens)
            .ignoringRequestMatchers("/api/**", "/actuator/**", "/swagger-ui/**", "/v3/api-docs/**")
        )
        
        // Session Management - Stateless for JWT
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        
        // Authorization Rules
        .authorizeHttpRequests(auth -> auth
            // ========================================
            // PUBLIC ENDPOINTS (No Authentication)
            // ========================================
            
            // Authentication Endpoints
            .requestMatchers(
                "/api/v1/auth/signup",      // User registration
                "/api/v1/auth/login",       // User login
                "/api/v1/auth/validate",    // Token validation (for gateway/services)
                "/api/v1/auth/health"       // Auth service health check
            ).permitAll()
            
            // Password Management Endpoints
            .requestMatchers(
                "/api/v1/auth/password/forgot",  // Initiate password reset
                "/api/v1/auth/password/reset"    // Complete password reset
            ).permitAll()
            
            // Email Verification Endpoints
            .requestMatchers(
                "/api/v1/auth/email/verify",   // Verify email with token
                "/api/v1/auth/email/resend",   // Resend verification email
                "/api/v1/auth/email/status"    // Check verification status
            ).permitAll()
            
            // Self-Service Provisioning Endpoints
            .requestMatchers("/api/v1/public/**").permitAll()
            
            // Infrastructure Endpoints
            .requestMatchers(
                "/actuator/health/**",      // Health checks
                "/actuator/info"            // Service info
            ).permitAll()
            
            // API Documentation Endpoints
            .requestMatchers(
                "/swagger-ui/**",           // Swagger UI
                "/v3/api-docs/**"           // OpenAPI docs
            ).permitAll()
            
            // JWK Set Endpoint (for JWT signature verification)
            .requestMatchers("/.well-known/jwks.json").permitAll()
            
            // ========================================
            // PROTECTED ENDPOINTS (Authentication Required)
            // ========================================
            
            // Token Management Endpoints (requires valid JWT)
            .requestMatchers(
                "/api/v1/auth/refresh",      // Refresh access token
                "/api/v1/auth/logout",       // Logout current session
                "/api/v1/auth/logout-all"    // Logout all sessions
            ).authenticated()
            
            // Admin Endpoints (requires ADMIN or SUPER_ADMIN authority)
            .requestMatchers("/api/v1/admin/**")
                .hasAnyAuthority("ADMIN", "SUPER_ADMIN")
            
            // Tenant Management Endpoints (requires SUPER_ADMIN authority)
            .requestMatchers("/api/v1/tenants/**")
                .hasAnyAuthority("SUPER_ADMIN")
            
            // Default: All other requests require authentication
            .anyRequest().authenticated()
        )
        
        // OAuth2 Resource Server (JWT)
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.decoder(jwtDecoder))
        )
        
        // Rate Limiting Filter (before authentication)
        .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
        
        // Security Headers
        .headers(headers -> headers
            // Prevent clickjacking attacks
            .frameOptions(frameOptions -> frameOptions.deny())
            
            // Prevent MIME type sniffing
            .contentTypeOptions(contentTypeOptions -> {})
            
            // HTTP Strict Transport Security (HSTS)
            .httpStrictTransportSecurity(hsts -> hsts
                .maxAgeInSeconds(31536000)  // 1 year
                .includeSubDomains(true)
            )
        )
        
        .build();
  }
}
