package com.iqscaffold.userservice.invitation;

import com.iqscaffold.userservice.config.IqScaffoldProperties;
import com.iqscaffold.userservice.emailverification.VerificationMetrics;
import com.iqscaffold.userservice.organization.Organization;
import com.iqscaffold.userservice.usermanagement.User;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Service for sending invitation-related emails.
 * Handles invitation emails and acceptance notification emails.
 */
@Service
public class InvitationEmailService {

  private static final Logger logger = LoggerFactory.getLogger(InvitationEmailService.class);

  private final JavaMailSender mailSender;
  private final TemplateEngine templateEngine;
  private final IqScaffoldProperties properties;
  private final VerificationMetrics metricsService;

  public InvitationEmailService(
      JavaMailSender mailSender,
      TemplateEngine templateEngine,
      IqScaffoldProperties properties,
      VerificationMetrics metricsService
  ) {
    this.mailSender = mailSender;
    this.templateEngine = templateEngine;
    this.properties = properties;
    this.metricsService = metricsService;
  }

  /**
   * Send invitation email to invitee.
   *
   * @param inviteeEmail       Email address of the invitee
   * @param invitationCode     Unique invitation code
   * @param organization       Organization the invitee is being invited to
   * @param inviterUsername    Username of the person who created the invitation
   * @param expiresAt          Expiration date/time of the invitation
   * @throws InvitationEmailException if email sending fails
   */
  public void sendInvitationEmail(
      String inviteeEmail,
      String invitationCode,
      Organization organization,
      String inviterUsername,
      java.time.LocalDateTime expiresAt
  ) {
    var timerSample = metricsService.startEmailSendTimer();

    try {
      var mimeMessage = mailSender.createMimeMessage();
      var helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

      var emailConfig = properties.email();
      var senderConfig = emailConfig.sender();

      // Set email properties
      helper.setFrom(senderConfig.fromEmail(), senderConfig.fromName());
      helper.setTo(inviteeEmail);
      helper.setSubject("You're invited to join " + organization.getName() + " on IQ Scaffold");

      // Build invitation URL
      var invitationUrl = buildInvitationUrl(invitationCode);

      // Create template context
      var context = new Context();
      context.setVariable("inviteeEmail", inviteeEmail);
      context.setVariable("invitationCode", invitationCode);
      context.setVariable("invitationUrl", invitationUrl);
      context.setVariable("organizationName", organization.getName());
      context.setVariable("organizationDescription", organization.getDescription());
      context.setVariable("organizationIndustry", organization.getIndustry());
      context.setVariable("organizationCity", organization.getCity());
      context.setVariable("organizationCountry", organization.getCountry());
      context.setVariable("inviterUsername", inviterUsername);
      context.setVariable("expiresAt", expiresAt);

      // Process HTML template
      var htmlContent = templateEngine.process("email/invitation/invitation-email", context);
      helper.setText(htmlContent, true);

      // Send email
      mailSender.send(mimeMessage);

      // Record successful send
      metricsService.recordEmailSent();

      logger.info("Invitation email sent successfully to {} for organization {}",
          inviteeEmail, organization.getName());

    } catch (final MessagingException e) {
      metricsService.recordEmailSendFailed();
      logger.error("Failed to create invitation email for {}",
          inviteeEmail, e);
      throw new InvitationEmailException("Failed to create invitation email", e);
    } catch (final MailException e) {
      metricsService.recordEmailSendFailed();
      logger.error("Failed to send invitation email to {}",
          inviteeEmail, e);
      throw new InvitationEmailException("Failed to send invitation email", e);
    } catch (final Exception e) {
      metricsService.recordEmailSendFailed();
      logger.error("Unexpected error sending invitation email to {}",
          inviteeEmail, e);
      throw new InvitationEmailException("Unexpected error sending invitation email", e);
    } finally {
      timerSample.stop(metricsService.getEmailSendTimer());
    }
  }

  /**
   * Send notification email to admin user when their invitation is accepted.
   *
   * @param adminUser          Admin user who created the invitation
   * @param newUserUsername    Username of the new user who accepted the invitation
   * @param newUserEmail       Email of the new user who accepted the invitation
   * @param organizationName   Name of the organization
   * @throws InvitationEmailException if email sending fails
   */
  public void sendInvitationAcceptedNotification(
      User adminUser,
      String newUserUsername,
      String newUserEmail,
      String organizationName
  ) {
    var timerSample = metricsService.startEmailSendTimer();

    try {
      var mimeMessage = mailSender.createMimeMessage();
      var helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

      var emailConfig = properties.email();
      var senderConfig = emailConfig.sender();

      // Set email properties
      helper.setFrom(senderConfig.fromEmail(), senderConfig.fromName());
      helper.setTo(adminUser.getEmail());
      helper.setSubject("New member joined " + organizationName + " - IQ Scaffold");

      // Create template context
      var context = new Context();
      context.setVariable("adminFirstName", adminUser.getFirstName());
      context.setVariable("newUserUsername", newUserUsername);
      context.setVariable("newUserEmail", newUserEmail);
      context.setVariable("organizationName", organizationName);

      // Process HTML template
      var htmlContent = templateEngine.process("email/invitation/invitation-accepted", context);
      helper.setText(htmlContent, true);

      // Send email
      mailSender.send(mimeMessage);

      // Record successful send
      metricsService.recordEmailSent();

      logger.info("Invitation accepted notification sent to {} for new user {}",
          adminUser.getEmail(), newUserUsername);

    } catch (final MessagingException e) {
      metricsService.recordEmailSendFailed();
      logger.error("Failed to create invitation accepted notification for {}",
          adminUser.getEmail(), e);
      throw new InvitationEmailException("Failed to create invitation accepted notification", e);
    } catch (final MailException e) {
      metricsService.recordEmailSendFailed();
      logger.error("Failed to send invitation accepted notification to {}",
          adminUser.getEmail(), e);
      throw new InvitationEmailException("Failed to send invitation accepted notification", e);
    } catch (final Exception e) {
      metricsService.recordEmailSendFailed();
      logger.error("Unexpected error sending invitation accepted notification to {}",
          adminUser.getEmail(), e);
      throw new InvitationEmailException("Unexpected error sending invitation accepted notification", e);
    } finally {
      timerSample.stop(metricsService.getEmailSendTimer());
    }
  }

  /**
   * Build invitation URL for joining organization.
   *
   * @param invitationCode Invitation code
   * @return Full invitation URL
   */
  private String buildInvitationUrl(String invitationCode) {
    var authBaseUrl = properties.email().sender().authBaseUrl();
    var cleanBaseUrl = authBaseUrl.endsWith("/")
        ? authBaseUrl.substring(0, authBaseUrl.length() - 1)
        : authBaseUrl;
    return cleanBaseUrl + "/join/" + invitationCode;
  }

  /**
   * Custom exception for invitation email service errors.
   */
  public static class InvitationEmailException extends RuntimeException {

    public InvitationEmailException(final String message) {
      super(message);
    }

    public InvitationEmailException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
