package com.iqscaffold.userservice.invitation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for OrganizationInvitation entity.
 * Provides query methods for invitation management and validation.
 */
@Repository
public interface InvitationRepository extends JpaRepository<OrganizationInvitation, Long> {

  /**
   * Find invitation by invitation code.
   */
  Optional<OrganizationInvitation> findByInvitationCode(String invitationCode);

  /**
   * Find invitations by organization ID with pagination.
   */
  Page<OrganizationInvitation> findByOrganizationId(Long organizationId, Pageable pageable);

  /**
   * Find invitations by organization ID and status with pagination.
   */
  Page<OrganizationInvitation> findByOrganizationIdAndStatus(
      Long organizationId,
      InvitationStatus status,
      Pageable pageable
  );

  /**
   * Find all pending invitations that have expired.
   * Used by scheduled cleanup task.
   */
  @Query("""
      SELECT i FROM OrganizationInvitation i 
      WHERE i.status = 'PENDING' 
      AND i.expiresAt < :currentTime
      """)
  List<OrganizationInvitation> findExpiredInvitations(@Param("currentTime") LocalDateTime currentTime);

  /**
   * Find invitations by tenant ID.
   */
  List<OrganizationInvitation> findByTenantId(String tenantId);

  /**
   * Find invitations by invitee email.
   */
  List<OrganizationInvitation> findByInviteeEmail(String inviteeEmail);

  /**
   * Find invitations created by a specific user.
   */
  List<OrganizationInvitation> findByInvitedByUserId(Long userId);

  /**
   * Count pending invitations for an organization.
   */
  @Query("""
      SELECT COUNT(i) FROM OrganizationInvitation i 
      WHERE i.organizationId = :organizationId 
      AND i.status = 'PENDING'
      """)
  long countPendingInvitationsByOrganization(@Param("organizationId") Long organizationId);

  /**
   * Count invitations created by a user within a time window.
   * Used for rate limiting.
   */
  @Query("""
      SELECT COUNT(i) FROM OrganizationInvitation i 
      WHERE i.organizationId = :organizationId 
      AND i.createdAt >= :since
      """)
  long countInvitationsCreatedSince(
      @Param("organizationId") Long organizationId,
      @Param("since") LocalDateTime since
  );

  /**
   * Check if invitation code exists.
   */
  boolean existsByInvitationCode(String invitationCode);

  /**
   * Find invitations by status.
   */
  List<OrganizationInvitation> findByStatus(InvitationStatus status);

  /**
   * Find invitations by type.
   */
  List<OrganizationInvitation> findByType(InvitationType type);
}
