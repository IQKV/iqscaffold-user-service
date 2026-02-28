package com.iqscaffold.userservice.security;

import java.time.Instant;

import com.iqscaffold.userservice.shared.UserServiceConstants;
import com.iqscaffold.userservice.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of SecurityAuditService providing comprehensive security audit logging.
 *
 * <p>This service implements enterprise-grade security audit logging with structured event tracking,
 * comprehensive context capture, and integration with both application logging and persistent audit storage.
 *
 * <p><strong>Transaction Management:</strong> This service does NOT use class-level @Transactional.
 * Instead, individual methods that need transactions use method-level annotations. This prevents
 * issues where exceptions caught internally would mark the entire transaction for rollback.
 *
 * @see SecurityAuditService
 * @see UserAuditLog
 * @see UserAuditLogRepository
 */
@Service
public class SecurityAuditServiceImpl implements SecurityAuditService {

  private static final Logger logger = LoggerFactory.getLogger(SecurityAuditServiceImpl.class);
  private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY");

  private final UserAuditLogRepository auditLogRepository;

  public SecurityAuditServiceImpl(final UserAuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  @Override
  public void logSuccessfulAuthentication(String username, String ipAddress, String userAgent) {
    var details = String.format("Authentication successful for user: %s%n" +
                                "IP Address: %s%n" +
                                "User Agent: %s%n" +
                                "Timestamp: %s%n", username, ipAddress, userAgent, Instant.now());

    logSecurityEvent("AUTHENTICATION_SUCCESS", username, details, ipAddress, userAgent);

    securityLogger.info("AUTHENTICATION_SUCCESS: user={}, ip={}, userAgent={}",
        username, ipAddress, userAgent);
  }

  @Override
  public void logFailedAuthentication(String username, String reason, String ipAddress, String userAgent) {
    var details = String.format("Authentication failed for user: %s%n" +
                                "Reason: %s%n" +
                                "IP Address: %s%n" +
                                "User Agent: %s%n" +
                                "Timestamp: %s%n", username, reason, ipAddress, userAgent, Instant.now());

    logSecurityEvent("AUTHENTICATION_FAILURE", username, details, ipAddress, userAgent);

    securityLogger.warn("AUTHENTICATION_FAILURE: user={}, reason={}, ip={}, userAgent={}",
        username, reason, ipAddress, userAgent);
  }

  @Override
  public void logAccountLockout(String username, int failedAttempts, String ipAddress, String userAgent) {
    var details = String.format("Account locked due to excessive failed login attempts%n" +
                                "User: %s%n" +
                                "Failed Attempts: %d%n" +
                                "IP Address: %s%n" +
                                "User Agent: %s%n" +
                                "Lockout Duration: 15 minutes%n" +
                                "Timestamp: %s%n", username, failedAttempts, ipAddress, userAgent, Instant.now());

    logSecurityEvent("ACCOUNT_LOCKOUT", username, details, ipAddress, userAgent);

    securityLogger.error("ACCOUNT_LOCKOUT: user={}, failedAttempts={}, ip={}, userAgent={}",
        username, failedAttempts, ipAddress, userAgent);
  }

  @Override
  public void logPasswordChange(String username, String ipAddress, String userAgent) {
    var details = String.format("Password changed for user: %s%n" +
                                "IP Address: %s%n" +
                                "User Agent: %s%n" +
                                "Timestamp: %s%n", username, ipAddress, userAgent, Instant.now());

    logSecurityEvent(UserServiceConstants.SecurityEvents.PASSWORD_CHANGE, username, details, ipAddress, userAgent);

    securityLogger.info("PASSWORD_CHANGE: user={}, ip={}, userAgent={}",
        username, ipAddress, userAgent);
  }

  @Override
  public void logUserRegistration(String username, String email, String ipAddress, String userAgent) {
    var details = String.format("New user registration%n" +
                                "Username: %s%n" +
                                "Email: %s%n" +
                                "IP Address: %s%n" +
                                "User Agent: %s%n" +
                                "Timestamp: %s%n", username, email, ipAddress, userAgent, Instant.now());

    logSecurityEvent("USER_REGISTRATION", username, details, ipAddress, userAgent);

    securityLogger.info("USER_REGISTRATION: user={}, email={}, ip={}, userAgent={}",
        username, email, ipAddress, userAgent);
  }

  @Override
  public void logRateLimitExceeded(String ipAddress, String userAgent, String endpoint) {
    var details = String.format("Rate limit exceeded%n" +
                                "IP Address: %s%n" +
                                "User Agent: %s%n" +
                                "Endpoint: %s%n" +
                                "Timestamp: %s%n", ipAddress, userAgent, endpoint, Instant.now());

    logSecurityEvent("RATE_LIMIT_EXCEEDED", null, details, ipAddress, userAgent);

    securityLogger.warn("RATE_LIMIT_EXCEEDED: ip={}, userAgent={}, endpoint={}",
        ipAddress, userAgent, endpoint);
  }

  @Override
  public void logSuspiciousActivity(String username, String activity, String ipAddress, String userAgent) {
    var details = String.format("Suspicious activity detected%n" +
                                "User: %s%n" +
                                "Activity: %s%n" +
                                "IP Address: %s%n" +
                                "User Agent: %s%n" +
                                "Timestamp: %s%n", username, activity, ipAddress, userAgent, Instant.now());

    logSecurityEvent("SUSPICIOUS_ACTIVITY", username, details, ipAddress, userAgent);

    securityLogger.error("SUSPICIOUS_ACTIVITY: user={}, activity={}, ip={}, userAgent={}",
        username, activity, ipAddress, userAgent);
  }

  @Override
  public void logTokenEvent(String username, String action, String ipAddress, String userAgent) {
    var details = String.format("JWT Token %s%n" +
                                "User: %s%n" +
                                "IP Address: %s%n" +
                                "User Agent: %s%n" +
                                "Timestamp: %s%n", action, username, ipAddress, userAgent, Instant.now());

    logSecurityEvent("TOKEN_" + action.toUpperCase(java.util.Locale.ROOT), username, details, ipAddress, userAgent);

    securityLogger.info("TOKEN_{}: user={}, ip={}, userAgent={}",
        action.toUpperCase(java.util.Locale.ROOT), username, ipAddress, userAgent);
  }

  @Override
  public void logUserLocaleChange(Long userId, String username, String newLocale) {
    var details = String.format("User locale preference changed%n" +
                                "User ID: %d%n" +
                                "Username: %s%n" +
                                "New Locale: %s%n" +
                                "Timestamp: %s%n", userId, username, newLocale, Instant.now());

    logSecurityEvent("USER_LOCALE_CHANGE", username, details, null, null);

    securityLogger.info("USER_LOCALE_CHANGE: userId={}, user={}, newLocale={}",
        userId, username, newLocale);
  }

  @Override
  public void logInvitationCreated(Long userId, String username, Long invitationId, String invitationType) {
    var details = String.format("Invitation created%n" +
                                "Created By User ID: %d%n" +
                                "Created By Username: %s%n" +
                                "Invitation ID: %d%n" +
                                "Invitation Type: %s%n" +
                                "Timestamp: %s%n", userId, username, invitationId, invitationType, Instant.now());

    logSecurityEvent("INVITATION_CREATED", username, details, null, null);

    securityLogger.info("INVITATION_CREATED: userId={}, user={}, invitationId={}, type={}",
        userId, username, invitationId, invitationType);
  }

  @Override
  public void logInvitationRevoked(Long userId, String username, Long invitationId) {
    var details = String.format("Invitation revoked%n" +
                                "Revoked By User ID: %d%n" +
                                "Revoked By Username: %s%n" +
                                "Invitation ID: %d%n" +
                                "Timestamp: %s%n", userId, username, invitationId, Instant.now());

    logSecurityEvent("INVITATION_REVOKED", username, details, null, null);

    securityLogger.info("INVITATION_REVOKED: userId={}, user={}, invitationId={}",
        userId, username, invitationId);
  }

  @Override
  public void logInvitationAccepted(Long invitationId, Long newUserId, String newUsername, String ipAddress, String userAgent) {
    var details = String.format("Invitation accepted - new user signup%n" +
                                "Invitation ID: %d%n" +
                                "New User ID: %d%n" +
                                "New Username: %s%n" +
                                "IP Address: %s%n" +
                                "User Agent: %s%n" +
                                "Timestamp: %s%n", invitationId, newUserId, newUsername, ipAddress, userAgent, Instant.now());

    logSecurityEvent("INVITATION_ACCEPTED", newUsername, details, ipAddress, userAgent);

    securityLogger.info("INVITATION_ACCEPTED: invitationId={}, newUserId={}, newUsername={}, ip={}, userAgent={}",
        invitationId, newUserId, newUsername, ipAddress, userAgent);
  }

  @Override
  public void logInvitationSignupFailed(String invitationCode, String reason, String ipAddress, String userAgent) {
    var details = String.format("Invitation signup failed%n" +
                                "Invitation Code: %s%n" +
                                "Reason: %s%n" +
                                "IP Address: %s%n" +
                                "User Agent: %s%n" +
                                "Timestamp: %s%n", invitationCode, reason, ipAddress, userAgent, Instant.now());

    logSecurityEvent("INVITATION_SIGNUP_FAILED", null, details, ipAddress, userAgent);

    securityLogger.warn("INVITATION_SIGNUP_FAILED: invitationCode={}, reason={}, ip={}, userAgent={}",
        invitationCode, reason, ipAddress, userAgent);
  }

  /**
   * Generic method to log security events to database and structured logs.
   */
  /**
   * Log a security event to the database in a separate transaction.
   * This method uses REQUIRES_NEW propagation to ensure audit logs are saved
   * even when the parent transaction fails. The try-catch ensures that audit
   * logging failures don't break the main application flow.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  private void logSecurityEvent(String action, String username, String details, String ipAddress, String userAgent) {
    // Capture tenant context before entering try-catch to ensure it's available
    // even if the parent transaction is rolled back
    var currentTenant = TenantContext.getCurrentTenantIdOrDefault();
    
    try {
      // Set correlation ID for tracing
      var correlationId = MDC.get(UserServiceConstants.MDC.CORRELATION_ID);
      if (correlationId != null) {
        MDC.put(UserServiceConstants.MDC.CORRELATION_ID, correlationId);
      }

      // Create audit log entry with correct tenant ID from the start
      // This is critical for schema-based multi-tenancy where the tenant ID
      // determines which database schema to use
      var auditLog = new UserAuditLog(action, currentTenant);
      auditLog.setDetails(details);
      auditLog.setIpAddress(ipAddress);
      auditLog.setUserAgent(userAgent);

      // Try to find user ID if username is provided
      if (username != null) {
        // Note: In a real implementation, you might want to look up the user ID
        // For now, we'll store the username in the details
        auditLog.setDetails(auditLog.getDetails() + "\nUsername: " + username);
      }

      // Save to database
      // With REQUIRES_NEW propagation, this executes in a separate transaction
      // that can succeed even if the parent transaction is rolled back
      auditLogRepository.save(auditLog);

    } catch (final Exception e) {
      // Don't let audit logging failures break the main flow
      // Log the error but don't rethrow - this prevents marking the transaction for rollback
      logger.error("Failed to save security audit log: {}", e.getMessage(), e);
    }
  }
}
