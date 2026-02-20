package com.iqscaffold.userservice.invitation;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * OrganizationInvitation entity representing invitation-based user onboarding.
 *
 * <p>Invitations are stored in the PUBLIC schema (system-wide) to enable cross-tenant
 * invitation validation and signup flows. Each invitation is tied to a specific organization
 * and tenant, allowing new users to join existing organizations through secure invitation codes.
 *
 * <h3>Key Features</h3>
 * <ul>
 *   <li>Cryptographically secure invitation codes (minimum 32 characters)</li>
 *   <li>Three invitation types: EMAIL, LINK, CODE</li>
 *   <li>Configurable expiration (1 hour to 30 days)</li>
 *   <li>Usage tracking and limits (single-use or multi-use)</li>
 *   <li>Authority assignment for new users</li>
 * </ul>
 *
 * <h3>Invitation Types</h3>
 * <ul>
 *   <li><strong>EMAIL</strong> - Sent to specific email, single-use only</li>
 *   <li><strong>LINK</strong> - Shareable link with optional max uses</li>
 *   <li><strong>CODE</strong> - Short code for manual entry</li>
 * </ul>
 *
 * <h3>Status Lifecycle</h3>
 * <ul>
 *   <li><strong>PENDING</strong> - Active and can be used</li>
 *   <li><strong>ACCEPTED</strong> - Successfully used (single-use)</li>
 *   <li><strong>EXPIRED</strong> - Past expiration date</li>
 *   <li><strong>REVOKED</strong> - Manually cancelled</li>
 * </ul>
 */
@Entity
@Table(name = "organization_invitations", schema = "public")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "com.iqscaffold.userservice.invitation.OrganizationInvitation")
public class OrganizationInvitation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "tenant_id", nullable = false, length = 100)
  private String tenantId;

  @Column(name = "invitation_code", nullable = false, unique = true, length = 64)
  private String invitationCode;

  @Column(name = "invitation_type", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private InvitationType type;

  @Column(name = "invitee_email", length = 255)
  private String inviteeEmail;

  @Column(name = "invited_by_user_id", nullable = false)
  private Long invitedByUserId;

  @Column(name = "invited_by_username", nullable = false, length = 255)
  private String invitedByUsername;

  @Column(name = "authority", nullable = false, length = 50)
  private String authority;

  @Column(name = "status", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private InvitationStatus status;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "accepted_at")
  private LocalDateTime acceptedAt;

  @Column(name = "accepted_by_user_id")
  private Long acceptedByUserId;

  @Column(name = "max_uses")
  private Integer maxUses;

  @Column(name = "current_uses", nullable = false)
  private Integer currentUses = 0;

  @Column(name = "metadata", columnDefinition = "TEXT")
  private String metadata;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected OrganizationInvitation() {
  }

  public OrganizationInvitation(
      Long organizationId,
      String tenantId,
      String invitationCode,
      InvitationType type,
      String invitedByUsername,
      Long invitedByUserId,
      String authority,
      LocalDateTime expiresAt
  ) {
    this.organizationId = organizationId;
    this.tenantId = tenantId;
    this.invitationCode = invitationCode;
    this.type = type;
    this.invitedByUsername = invitedByUsername;
    this.invitedByUserId = invitedByUserId;
    this.authority = authority;
    this.expiresAt = expiresAt;
    this.status = InvitationStatus.PENDING;
    this.currentUses = 0;
  }

  // Getters and Setters

  public Long getId() {
    return id;
  }

  public Long getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(Long organizationId) {
    this.organizationId = organizationId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getInvitationCode() {
    return invitationCode;
  }

  public void setInvitationCode(String invitationCode) {
    this.invitationCode = invitationCode;
  }

  public InvitationType getType() {
    return type;
  }

  public void setType(InvitationType type) {
    this.type = type;
  }

  public String getInviteeEmail() {
    return inviteeEmail;
  }

  public void setInviteeEmail(String inviteeEmail) {
    this.inviteeEmail = inviteeEmail;
  }

  public Long getInvitedByUserId() {
    return invitedByUserId;
  }

  public void setInvitedByUserId(Long invitedByUserId) {
    this.invitedByUserId = invitedByUserId;
  }

  public String getInvitedByUsername() {
    return invitedByUsername;
  }

  public void setInvitedByUsername(String invitedByUsername) {
    this.invitedByUsername = invitedByUsername;
  }

  public String getAuthority() {
    return authority;
  }

  public void setAuthority(String authority) {
    this.authority = authority;
  }

  public InvitationStatus getStatus() {
    return status;
  }

  public void setStatus(InvitationStatus status) {
    this.status = status;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(LocalDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public LocalDateTime getAcceptedAt() {
    return acceptedAt;
  }

  public void setAcceptedAt(LocalDateTime acceptedAt) {
    this.acceptedAt = acceptedAt;
  }

  public Long getAcceptedByUserId() {
    return acceptedByUserId;
  }

  public void setAcceptedByUserId(Long acceptedByUserId) {
    this.acceptedByUserId = acceptedByUserId;
  }

  public Integer getMaxUses() {
    return maxUses;
  }

  public void setMaxUses(Integer maxUses) {
    this.maxUses = maxUses;
  }

  public Integer getCurrentUses() {
    return currentUses;
  }

  public void setCurrentUses(Integer currentUses) {
    this.currentUses = currentUses;
  }

  public String getMetadata() {
    return metadata;
  }

  public void setMetadata(String metadata) {
    this.metadata = metadata;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  // Business Logic Methods

  /**
   * Check if the invitation is currently valid for use.
   */
  public boolean isValid() {
    return status == InvitationStatus.PENDING
           && LocalDateTime.now().isBefore(expiresAt)
           && !hasReachedMaxUses();
  }

  /**
   * Check if the invitation has expired.
   */
  public boolean isExpired() {
    return LocalDateTime.now().isAfter(expiresAt) || LocalDateTime.now().isEqual(expiresAt);
  }

  /**
   * Check if the invitation has reached its maximum usage limit.
   */
  public boolean hasReachedMaxUses() {
    return maxUses != null && currentUses >= maxUses;
  }

  /**
   * Check if the invitation is a single-use email invitation.
   */
  public boolean isSingleUseEmail() {
    return type == InvitationType.EMAIL;
  }

  /**
   * Increment the usage counter.
   */
  public void incrementUses() {
    this.currentUses++;
  }

  /**
   * Mark the invitation as accepted (for single-use invitations).
   */
  public void markAsAccepted(Long acceptedByUserId) {
    this.status = InvitationStatus.ACCEPTED;
    this.acceptedAt = LocalDateTime.now();
    this.acceptedByUserId = acceptedByUserId;
  }

  /**
   * Mark the invitation as revoked.
   */
  public void revoke() {
    this.status = InvitationStatus.REVOKED;
  }

  /**
   * Mark the invitation as expired.
   */
  public void markAsExpired() {
    this.status = InvitationStatus.EXPIRED;
  }

  /**
   * Validate email match for email invitations.
   */
  public boolean emailMatches(String email) {
    if (type != InvitationType.EMAIL) {
      return true; // Email validation only applies to EMAIL type
    }
    return inviteeEmail != null && inviteeEmail.equalsIgnoreCase(email);
  }

  /**
   * Equals based on business key (invitationCode) which is unique and immutable.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }

    OrganizationInvitation that = (OrganizationInvitation) obj;
    return Objects.equals(invitationCode, that.invitationCode);
  }

  /**
   * HashCode based on business key (invitationCode).
   */
  @Override
  public int hashCode() {
    return invitationCode != null ? invitationCode.hashCode() : 0;
  }

  @Override
  public String toString() {
    return "OrganizationInvitation{" +
           "id=" + id +
           ", organizationId=" + organizationId +
           ", tenantId='" + tenantId + '\'' +
           ", invitationCode='" + invitationCode + '\'' +
           ", type=" + type +
           ", status=" + status +
           ", expiresAt=" + expiresAt +
           ", currentUses=" + currentUses +
           ", maxUses=" + maxUses +
           ", createdAt=" + createdAt +
           '}';
  }
}
