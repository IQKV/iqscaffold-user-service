package com.iqscaffold.userservice.registration;

import java.util.List;

import com.iqscaffold.userservice.config.PlatformConfigurationProperties;
import com.iqscaffold.userservice.emailverification.EmailVerificationService;
import com.iqscaffold.userservice.organization.Organization;
import com.iqscaffold.userservice.organization.OrganizationRepository;
import com.iqscaffold.userservice.security.InputSanitizer;
import com.iqscaffold.userservice.security.SecurityAuditService;
import com.iqscaffold.userservice.shared.Authority;
import com.iqscaffold.userservice.shared.AuthorityRepository;
import com.iqscaffold.userservice.tenancy.TenantContext;
import com.iqscaffold.userservice.usermanagement.User;
import com.iqscaffold.userservice.usermanagement.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enhanced service for user registration with security measures and configurable default authorities.
 * Includes input sanitization, audit logging, security validation, and flexible authority assignment.
 */
@Service
@Transactional
public class UserRegistrationService {

  private static final Logger logger = LoggerFactory.getLogger(UserRegistrationService.class);

  private final UserRepository userRepository;
  private final AuthorityRepository authorityRepository;
  private final PasswordEncoder passwordEncoder;
  private final SecurityAuditService securityAuditService;
  private final InputSanitizer inputSanitizer;
  private final EmailVerificationService emailVerificationService;
  private final PlatformConfigurationProperties platformConfig;
  private final OrganizationRepository organizationRepository;

  public UserRegistrationService(final UserRepository userRepository,
                                 final AuthorityRepository authorityRepository,
                                 final PasswordEncoder passwordEncoder,
                                 final SecurityAuditService securityAuditService,
                                 final InputSanitizer inputSanitizer,
                                 final EmailVerificationService emailVerificationService,
                                 final PlatformConfigurationProperties platformConfig,
                                 final OrganizationRepository organizationRepository) {
    this.userRepository = userRepository;
    this.authorityRepository = authorityRepository;
    this.passwordEncoder = passwordEncoder;
    this.securityAuditService = securityAuditService;
    this.inputSanitizer = inputSanitizer;
    this.emailVerificationService = emailVerificationService;
    this.platformConfig = platformConfig;
    this.organizationRepository = organizationRepository;
  }

  /**
   * Register a new user with enhanced security validation. Includes input sanitization, security checks, and audit logging.
   */
  public UserRegistrationResponse registerUser(SignupRequest request, String ipAddress, String userAgent) {
    // Sanitize all inputs to prevent XSS and injection attacks
    var sanitizedUsername = inputSanitizer.sanitizeUsername(request.username());
    var sanitizedEmail = inputSanitizer.sanitizeEmail(request.email());
    var sanitizedFirstName = inputSanitizer.sanitizeName(request.firstName());
    var sanitizedLastName = inputSanitizer.sanitizeName(request.lastName());

    // Validate input safety
    if (!inputSanitizer.isInputSafe(request.username())
        || !inputSanitizer.isInputSafe(request.email())
        || !inputSanitizer.isInputSafe(request.firstName())
        || !inputSanitizer.isInputSafe(request.lastName())) {

      securityAuditService.logSuspiciousActivity(
          sanitizedUsername, "Potential XSS/injection attempt in registration", ipAddress, userAgent);
      throw new UserRegistrationException("Invalid input detected");
    }

    // Check for SQL injection attempts
    if (inputSanitizer.containsSqlInjection(request.username())
        || inputSanitizer.containsSqlInjection(request.email())
        || inputSanitizer.containsSqlInjection(request.firstName())
        || inputSanitizer.containsSqlInjection(request.lastName())) {

      securityAuditService.logSuspiciousActivity(
          sanitizedUsername, "SQL injection attempt in registration", ipAddress, userAgent);
      throw new UserRegistrationException("Invalid input detected");
    }

    // Check for duplicate username and email using var
    var existingUsername = userRepository.existsByUsername(sanitizedUsername);
    var existingEmail = userRepository.existsByEmail(sanitizedEmail);

    if (existingUsername) {
      securityAuditService.logFailedAuthentication(
          sanitizedUsername, "Registration failed - username exists", ipAddress, userAgent);
      throw new UserRegistrationException("Username already exists");
    }

    if (existingEmail) {
      securityAuditService.logFailedAuthentication(
          sanitizedEmail, "Registration failed - email exists", ipAddress, userAgent);
      throw new UserRegistrationException("Email already exists");
    }

    // Hash password with enhanced security
    var hashedPassword = passwordEncoder.encode(request.password());

    // Create new user entity with sanitized inputs
    var user = new User(
        sanitizedUsername,
        sanitizedEmail,
        hashedPassword,
        sanitizedFirstName,
        sanitizedLastName,
        request.tenantId()
    );

    // Ensure emailVerified is false for new users
    user.setEmailVerified(false);

    // Check if this is the first user in the tenant
    var isFirstUser = isFirstUserInTenant(request.tenantId());

    // Assign authorities based on whether this is the first user
    List<Authority> authorities;
    if (isFirstUser) {
      // First user gets TENANT_OWNER role
      authorities = findOrCreateTenantOwnerAuthorities();
      logger.info("Assigning TENANT_OWNER role to first user in tenant: {}", request.tenantId());
    } else {
      // Subsequent users get default authorities
      authorities = findOrCreateDefaultAuthorities();
    }

    for (final var authority : authorities) {
      user.addAuthority(authority);
    }

    // Save user
    var savedUser = userRepository.save(user);

    // If this is the first user, create organization and link it
    if (isFirstUser) {
      createOrganizationForFirstUser(savedUser, sanitizedUsername);
    }

    // Log successful registration with assigned authorities
    var authorityNames = authorities.stream()
        .map(Authority::getName)
        .toList();
    logger.info("User registered successfully: {} with authorities: {}",
        savedUser.getUsername(), authorityNames);

    securityAuditService.logUserRegistration(
        savedUser.getUsername(), savedUser.getEmail(), ipAddress, userAgent);

    // Generate verification token and send verification email
    try {
      emailVerificationService.generateVerificationToken(savedUser);
    } catch (final Exception e) {
      logger.warn("Failed to send verification email to user: {} ({})",
          savedUser.getUsername(), savedUser.getEmail(), e);
      // Don't fail registration if email sending fails
    }

    // Return registration response
    return new UserRegistrationResponse(
        savedUser.getId(),
        savedUser.getUsername(),
        savedUser.getEmail(),
        savedUser.getFirstName(),
        savedUser.getLastName(),
        savedUser.getEmailVerified(),
        savedUser.getCreatedAt(),
        "User registered successfully. Please verify your email."
    );
  }

  /**
   * Find or create the configured default authorities for new users.
   * Uses the configurable list from platform configuration properties.
   */
  private List<Authority> findOrCreateDefaultAuthorities() {
    var configuredAuthorities = platformConfig.authorities().defaultAuthorities();

    logger.debug("Finding or creating default authorities: {}", configuredAuthorities);

    return configuredAuthorities.stream()
        .map(this::findOrCreateAuthority)
        .toList();
  }

  /**
   * Find or create a specific authority by name.
   * Creates the authority with appropriate description if it doesn't exist.
   */
  private Authority findOrCreateAuthority(String authorityName) {
    var existingAuthority = authorityRepository.findByName(authorityName);

    if (existingAuthority.isPresent()) {
      return existingAuthority.get();
    }

    // Get description from platform configuration if available
    var description = getAuthorityDescription(authorityName);
    var newAuthority = new Authority(authorityName, description);
    var savedAuthority = authorityRepository.save(newAuthority);

    logger.info("Created new authority: {} - {}", authorityName, description);
    return savedAuthority;
  }

  /**
   * Get authority description from platform configuration.
   */
  private String getAuthorityDescription(String authorityName) {
    var authorityDefinition = platformConfig.authorities().getAuthority(authorityName);
    if (authorityDefinition != null) {
      return authorityDefinition.description();
    }

    // Provide generic description for authorities not in configuration
    return "Configurable authority: " + authorityName;
  }

  /**
   * Check if this is the first user in the tenant.
   * Executes in tenant context to count existing users.
   */
  private boolean isFirstUserInTenant(String tenantId) {
    return TenantContext.executeInTenantContext(tenantId, () -> {
      var userCount = userRepository.count();
      logger.debug("User count in tenant {}: {}", tenantId, userCount);
      return userCount == 0;
    });
  }

  /**
   * Find or create TENANT_OWNER authority along with default authorities.
   * First user gets both TENANT_OWNER and USER roles.
   */
  private List<Authority> findOrCreateTenantOwnerAuthorities() {
    var tenantOwnerAuthority = findOrCreateAuthority("TENANT_OWNER");
    var defaultAuthorities = findOrCreateDefaultAuthorities();

    // Combine TENANT_OWNER with default authorities
    var authorities = new java.util.ArrayList<Authority>();
    authorities.add(tenantOwnerAuthority);
    authorities.addAll(defaultAuthorities);

    logger.debug("Created tenant owner authorities: TENANT_OWNER + {}", 
        defaultAuthorities.stream().map(Authority::getName).toList());

    return authorities;
  }

  /**
   * Create organization for the first user (tenant owner).
   * Organization is created in PUBLIC schema and linked to the tenant.
   */
  private void createOrganizationForFirstUser(User user, String createdBy) {
    try {
      // Check if organization already exists for this tenant
      var existingOrg = organizationRepository.findByTenantId(user.getTenantId());
      if (existingOrg.isPresent()) {
        logger.info("Organization already exists for tenant: {}, updating owner", user.getTenantId());
        var org = existingOrg.get();
        org.setOwnerUserId(user.getId());
        organizationRepository.save(org);
        return;
      }

      // Create new organization
      var organizationName = user.getFirstName() + " " + user.getLastName() + "'s Organization";
      var organization = new Organization(organizationName, user.getTenantId());
      organization.setEnabled(true);
      organization.setSubscriptionStatus("trial");
      organization.setSubscriptionPlan("basic");
      organization.setMaxUsers(10);
      organization.setCreatedBy(createdBy);
      organization.setOwnerUserId(user.getId());
      organization.setBillingEmail(user.getEmail());

      var savedOrganization = organizationRepository.save(organization);

      logger.info("Created organization '{}' (ID: {}) for first user {} in tenant: {}",
          savedOrganization.getName(), savedOrganization.getId(), 
          user.getUsername(), user.getTenantId());

    } catch (final Exception e) {
      logger.error("Failed to create organization for first user {} in tenant {}: {}",
          user.getUsername(), user.getTenantId(), e.getMessage(), e);
      // Don't fail registration if organization creation fails
    }
  }


  /**
   * Custom exception for user registration errors.
   */
  public static class UserRegistrationException extends RuntimeException {

    public UserRegistrationException(final String message) {
      super(message);
    }

    public UserRegistrationException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
