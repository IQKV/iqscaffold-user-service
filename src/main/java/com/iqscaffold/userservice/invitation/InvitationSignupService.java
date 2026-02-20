package com.iqscaffold.userservice.invitation;

import com.iqscaffold.userservice.authentication.JwtService;
import com.iqscaffold.userservice.config.JwtConfiguration;
import com.iqscaffold.userservice.config.PlatformConfigurationProperties;
import com.iqscaffold.userservice.emailverification.EmailVerificationService;
import com.iqscaffold.userservice.organization.Organization;
import com.iqscaffold.userservice.organization.OrganizationRepository;
import com.iqscaffold.userservice.security.SecurityAuditService;
import com.iqscaffold.userservice.shared.Authority;
import com.iqscaffold.userservice.shared.AuthorityRepository;
import com.iqscaffold.userservice.tenancy.TenantContext;
import com.iqscaffold.userservice.tenancy.TenantRepository;
import com.iqscaffold.userservice.usermanagement.User;
import com.iqscaffold.userservice.usermanagement.UserRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for handling signup with invitation.
 * Manages the complete flow of accepting an invitation and creating a new user account.
 */
@Service
@Transactional
public class InvitationSignupService {

  private static final Logger logger = LoggerFactory.getLogger(InvitationSignupService.class);

  private final InvitationService invitationService;
  private final InvitationRepository invitationRepository;
  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final TenantRepository tenantRepository;
  private final AuthorityRepository authorityRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final JwtConfiguration jwtConfiguration;
  private final EmailVerificationService emailVerificationService;
  private final InvitationEmailService invitationEmailService;
  private final SecurityAuditService auditService;
  private final PlatformConfigurationProperties platformConfig;

  public InvitationSignupService(
      InvitationService invitationService,
      InvitationRepository invitationRepository,
      UserRepository userRepository,
      OrganizationRepository organizationRepository,
      TenantRepository tenantRepository,
      AuthorityRepository authorityRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      JwtConfiguration jwtConfiguration,
      EmailVerificationService emailVerificationService,
      InvitationEmailService invitationEmailService,
      SecurityAuditService auditService,
      PlatformConfigurationProperties platformConfig
  ) {
    this.invitationService = invitationService;
    this.invitationRepository = invitationRepository;
    this.userRepository = userRepository;
    this.organizationRepository = organizationRepository;
    this.tenantRepository = tenantRepository;
    this.authorityRepository = authorityRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.jwtConfiguration = jwtConfiguration;
    this.emailVerificationService = emailVerificationService;
    this.invitationEmailService = invitationEmailService;
    this.auditService = auditService;
    this.platformConfig = platformConfig;
  }

  /**
   * Sign up a new user with an invitation code.
   * Creates user in target tenant schema, assigns authorities, generates tokens.
   *
   * @param invitationCode Invitation code
   * @param request        Signup request
   * @return Signup response with tokens and user info
   * @throws IllegalArgumentException if validation fails
   * @throws IllegalStateException    if invitation is invalid or user creation fails
   */
  public SignupWithInvitationResponse signupWithInvitation(
      String invitationCode,
      SignupWithInvitationRequest request
  ) {
    // Step 1: Validate invitation
    InvitationValidationResult validation = invitationService.validateInvitation(invitationCode);
    if (!validation.valid()) {
      throw new IllegalArgumentException(validation.message());
    }

    // Step 2: Get invitation entity
    OrganizationInvitation invitation = invitationRepository.findByInvitationCode(invitationCode)
        .orElseThrow(() -> new IllegalStateException("Invitation not found"));

    // Step 3: Validate email matches for EMAIL type invitations
    if (!invitation.emailMatches(request.email())) {
      throw new IllegalArgumentException("Email does not match invitation");
    }

    // Step 4: Check username uniqueness across ALL tenants
    if (isUsernameExistsGlobally(request.username())) {
      auditService.logFailedAuthentication(
          request.username(),
          "Signup with invitation failed - username exists",
          "system",
          "invitation-signup"
      );
      throw new ConflictException("Username already exists");
    }

    // Step 5: Check email uniqueness across ALL tenants
    if (isEmailExistsGlobally(request.email())) {
      auditService.logFailedAuthentication(
          request.email(),
          "Signup with invitation failed - email exists",
          "system",
          "invitation-signup"
      );
      throw new ConflictException("Email already exists");
    }

    // Step 6: Get organization
    Organization organization = organizationRepository.findById(invitation.getOrganizationId())
        .orElseThrow(() -> new IllegalStateException("Organization not found"));

    // Step 7: Switch to target tenant schema context
    String previousTenantId = TenantContext.getCurrentTenantId();
    try {
      TenantContext.setCurrentTenantId(invitation.getTenantId());

      // Step 8: Hash password
      String hashedPassword = passwordEncoder.encode(request.password());

      // Step 9: Create user in tenant schema
      User user = new User(
          request.username(),
          request.email(),
          hashedPassword,
          request.firstName(),
          request.lastName(),
          invitation.getTenantId()
      );
      user.setEmailVerified(false);
      user.setPreferredLocale(request.preferredLocale());

      // Step 10: Assign authorities
      List<Authority> authorities = assignAuthoritiesToUser(user, invitation.getAuthority());

      // Step 11: Save user
      user = userRepository.save(user);

      // Step 12: Increment invitation usage
      invitation.incrementUses();

      // Step 13: Update invitation status if single-use email invitation
      if (invitation.isSingleUseEmail()) {
        invitation.markAsAccepted(user.getId());
      }

      invitationRepository.save(invitation);

      // Step 14: Generate JWT tokens
      String accessToken = jwtService.generateAccessToken(user);
      String refreshToken = jwtService.generateRefreshToken(user);

      // Get access token expiration from configuration
      Duration accessTokenExpiry = jwtConfiguration.getAccessTokenExpiry();
      long expiresIn = accessTokenExpiry.getSeconds();

      TokenResponse tokens = new TokenResponse(accessToken, refreshToken, expiresIn);

      // Step 15: Send email verification
      try {
        emailVerificationService.generateVerificationToken(user);
        logger.info("Email verification sent to user: {} ({})", user.getUsername(), user.getEmail());
      } catch (Exception e) {
        // Log error but don't fail signup
        logger.error("Failed to send verification email to user: {}", user.getEmail(), e);
      }

      // Step 16: Create audit log
      auditService.logInvitationAccepted(
          invitation.getId(),
          user.getId(),
          user.getUsername(),
          "system",
          "invitation-signup"
      );

      logger.info("User {} successfully signed up with invitation {} for organization {}",
          user.getUsername(),
          invitation.getId(),
          organization.getName()
      );

      // Step 18: Send invitation accepted notification to admin
      try {
        // Get the admin user who created the invitation
        User adminUser = userRepository.findById(invitation.getInvitedByUserId())
            .orElse(null);
        
        if (adminUser != null) {
          invitationEmailService.sendInvitationAcceptedNotification(
              adminUser,
              user.getUsername(),
              user.getEmail(),
              organization.getName()
          );
          logger.info("Invitation accepted notification sent to admin {} for new user {}",
              adminUser.getUsername(),
              user.getUsername()
          );
        } else {
          logger.warn("Could not find admin user {} to send invitation accepted notification",
              invitation.getInvitedByUserId()
          );
        }
      } catch (Exception e) {
        // Log error but don't fail signup
        logger.error("Failed to send invitation accepted notification for user {}",
            user.getUsername(),
            e
        );
      }

      // Step 19: Return response
      List<String> authorityNames = authorities.stream()
          .map(Authority::getName)
          .toList();

      return new SignupWithInvitationResponse(
          user.getId(),
          user.getUsername(),
          user.getEmail(),
          user.getTenantId(),
          organization.getName(),
          authorityNames,
          tokens,
          "Account created successfully. Please verify your email."
      );

    } finally {
      // Restore previous tenant context
      if (previousTenantId != null) {
        TenantContext.setCurrentTenantId(previousTenantId);
      } else {
        TenantContext.clear();
      }
    }
  }

  /**
   * Get organization preview for an invitation.
   *
   * @param invitationCode Invitation code
   * @return Organization preview DTO
   * @throws IllegalArgumentException if invitation not found
   */
  public OrganizationPreviewDto getOrganizationPreview(String invitationCode) {
    // Find invitation
    OrganizationInvitation invitation = invitationRepository.findByInvitationCode(invitationCode)
        .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

    // Get organization
    Organization organization = organizationRepository.findById(invitation.getOrganizationId())
        .orElseThrow(() -> new IllegalStateException("Organization not found"));

    return new OrganizationPreviewDto(
        organization.getName(),
        organization.getDescription(),
        organization.getIndustry(),
        organization.getCity(),
        organization.getCountry(),
        invitation.getInvitedByUsername(),
        invitation.getExpiresAt()
    );
  }

  /**
   * Assign authorities to user based on invitation and platform defaults.
   *
   * @param user               User to assign authorities to
   * @param invitationAuthority Authority from invitation
   * @return List of assigned authorities
   */
  private List<Authority> assignAuthoritiesToUser(User user, String invitationAuthority) {
    List<Authority> authorities = new ArrayList<>();

    // Get default authorities from platform configuration
    List<Authority> defaultAuthorities = findOrCreateDefaultAuthorities();
    authorities.addAll(defaultAuthorities);

    // Add invitation authority if not in defaults
    boolean invitationAuthorityIsDefault = defaultAuthorities.stream()
        .anyMatch(a -> a.getName().equals(invitationAuthority));

    if (!invitationAuthorityIsDefault) {
      Authority invitationAuth = findOrCreateAuthority(invitationAuthority);
      authorities.add(invitationAuth);
    }

    // Assign all authorities to user
    for (Authority authority : authorities) {
      user.addAuthority(authority);
    }

    logger.debug("Assigned authorities to user {}: {}",
        user.getUsername(),
        authorities.stream().map(Authority::getName).toList()
    );

    return authorities;
  }

  /**
   * Find or create default authorities from platform configuration.
   *
   * @return List of default authorities
   */
  private List<Authority> findOrCreateDefaultAuthorities() {
    List<String> configuredAuthorities = platformConfig.authorities().defaultAuthorities();

    logger.debug("Finding or creating default authorities: {}", configuredAuthorities);

    List<Authority> authorities = new ArrayList<>();
    for (String authorityName : configuredAuthorities) {
      Authority authority = findOrCreateAuthority(authorityName);
      authorities.add(authority);
    }

    return authorities;
  }

  /**
   * Find or create an authority by name.
   *
   * @param authorityName Authority name
   * @return Authority entity
   */
  private Authority findOrCreateAuthority(String authorityName) {
    Optional<Authority> existing = authorityRepository.findByName(authorityName);
    if (existing.isPresent()) {
      return existing.get();
    }

    // Create new authority
    Authority authority = new Authority(authorityName);
    authority.setDescription(getAuthorityDescription(authorityName));
    return authorityRepository.save(authority);
  }

  /**
   * Get description for an authority.
   *
   * @param authorityName Authority name
   * @return Description
   */
  private String getAuthorityDescription(String authorityName) {
    var authorityDef = platformConfig.authorities().getAuthority(authorityName);
    return authorityDef != null ? authorityDef.description() : "User with " + authorityName + " privileges";
  }

  /**
   * Check if username exists globally across all tenants.
   *
   * @param username Username to check
   * @return true if username exists in any tenant
   */
  private boolean isUsernameExistsGlobally(String username) {
    String currentTenantId = TenantContext.getCurrentTenantId();
    try {
      // Get all active tenants
      List<String> tenantIds = tenantRepository.findAll().stream()
          .map(tenant -> tenant.getTenantId())
          .toList();

      // Check each tenant schema
      for (String tenantId : tenantIds) {
        TenantContext.setCurrentTenantId(tenantId);
        if (userRepository.existsByUsername(username)) {
          return true;
        }
      }

      return false;
    } finally {
      // Restore original tenant context
      if (currentTenantId != null) {
        TenantContext.setCurrentTenantId(currentTenantId);
      } else {
        TenantContext.clear();
      }
    }
  }

  /**
   * Check if email exists globally across all tenants.
   *
   * @param email Email to check
   * @return true if email exists in any tenant
   */
  private boolean isEmailExistsGlobally(String email) {
    String currentTenantId = TenantContext.getCurrentTenantId();
    try {
      // Get all active tenants
      List<String> tenantIds = tenantRepository.findAll().stream()
          .map(tenant -> tenant.getTenantId())
          .toList();

      // Check each tenant schema
      for (String tenantId : tenantIds) {
        TenantContext.setCurrentTenantId(tenantId);
        if (userRepository.existsByEmail(email)) {
          return true;
        }
      }

      return false;
    } finally {
      // Restore original tenant context
      if (currentTenantId != null) {
        TenantContext.setCurrentTenantId(currentTenantId);
      } else {
        TenantContext.clear();
      }
    }
  }

  /**
   * Exception for conflict errors (409).
   */
  public static class ConflictException extends RuntimeException {
    public ConflictException(String message) {
      super(message);
    }
  }
}
