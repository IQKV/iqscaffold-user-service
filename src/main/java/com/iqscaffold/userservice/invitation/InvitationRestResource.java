package com.iqscaffold.userservice.invitation;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.iqscaffold.userservice.usermanagement.UserContext;

/**
 * REST controller for authenticated invitation management operations.
 * Provides endpoints for creating, listing, revoking invitations and retrieving invitation links.
 * All endpoints require ADMIN or TENANT_ADMIN authority.
 */
@RestController
@RequestMapping("/api/v1/invitations")
@Tag(name = "Invitation Management", description = "Authenticated invitation management operations for organization administrators")
@SecurityRequirement(name = "bearerAuth")
public class InvitationRestResource {

  private final InvitationService invitationService;

  public InvitationRestResource(final InvitationService invitationService) {
    this.invitationService = invitationService;
  }

  @Operation(
      summary = "Create invitation",
      description = "Create a new invitation for users to join the organization. Requires ADMIN or TENANT_ADMIN role."
  )
  @ApiResponses(value = {
      @ApiResponse(responseCode = "201", description = "Invitation created successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid input data or validation errors"),
      @ApiResponse(responseCode = "403", description = "Insufficient permissions - requires ADMIN or TENANT_ADMIN role"),
      @ApiResponse(responseCode = "429", description = "Rate limit exceeded - maximum 10 invitations per hour"),
      @ApiResponse(responseCode = "401", description = "Authentication required")
  })
  @PostMapping
  @PreAuthorize("hasAnyAuthority('ADMIN', 'TENANT_ADMIN')")
  public ResponseEntity<OrganizationInvitationDto> createInvitation(
      @Parameter(description = "Invitation creation request", required = true)
      @Valid @RequestBody CreateInvitationRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal UserContext currentUser) {

    var invitation = invitationService.createInvitation(request, currentUser);
    return ResponseEntity.status(HttpStatus.CREATED).body(invitation);
  }

  @Operation(
      summary = "List invitations",
      description = "Get paginated list of invitations for the current organization. Requires ADMIN or TENANT_ADMIN role."
  )
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Invitations retrieved successfully"),
      @ApiResponse(responseCode = "403", description = "Insufficient permissions - requires ADMIN or TENANT_ADMIN role"),
      @ApiResponse(responseCode = "401", description = "Authentication required")
  })
  @GetMapping
  @PreAuthorize("hasAnyAuthority('ADMIN', 'TENANT_ADMIN')")
  public ResponseEntity<Page<OrganizationInvitationDto>> listInvitations(
      @Parameter(description = "Filter by invitation status (optional)")
      @RequestParam(required = false) InvitationStatus status,
      @PageableDefault(size = 20) Pageable pageable,
      @Parameter(hidden = true) @AuthenticationPrincipal UserContext currentUser) {

    var invitations = invitationService.getOrganizationInvitations(
        status,
        pageable,
        currentUser
    );
    return ResponseEntity.ok(invitations);
  }

  @Operation(
      summary = "Revoke invitation",
      description = "Revoke an existing invitation. Requires ADMIN or TENANT_ADMIN role."
  )
  @ApiResponses(value = {
      @ApiResponse(responseCode = "204", description = "Invitation revoked successfully"),
      @ApiResponse(responseCode = "403", description = "Insufficient permissions or invitation not accessible"),
      @ApiResponse(responseCode = "404", description = "Invitation not found"),
      @ApiResponse(responseCode = "401", description = "Authentication required")
  })
  @DeleteMapping("/{invitationId}")
  @PreAuthorize("hasAnyAuthority('ADMIN', 'TENANT_ADMIN')")
  public ResponseEntity<Void> revokeInvitation(
      @Parameter(description = "Invitation ID", required = true) @PathVariable Long invitationId,
      @Parameter(hidden = true) @AuthenticationPrincipal UserContext currentUser) {

    invitationService.revokeInvitation(invitationId, currentUser);
    return ResponseEntity.noContent().build();
  }

  @Operation(
      summary = "Get invitation link",
      description = "Retrieve the full invitation link for sharing. Requires ADMIN or TENANT_ADMIN role."
  )
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Invitation link retrieved successfully"),
      @ApiResponse(responseCode = "403", description = "Insufficient permissions or invitation not accessible"),
      @ApiResponse(responseCode = "404", description = "Invitation not found"),
      @ApiResponse(responseCode = "401", description = "Authentication required")
  })
  @GetMapping("/{invitationId}/link")
  @PreAuthorize("hasAnyAuthority('ADMIN', 'TENANT_ADMIN')")
  public ResponseEntity<InvitationLinkResponse> getInvitationLink(
      @Parameter(description = "Invitation ID", required = true) @PathVariable Long invitationId,
      @Parameter(hidden = true) @AuthenticationPrincipal UserContext currentUser) {

    var invitationLink = invitationService.getInvitationLink(invitationId, currentUser);
    return ResponseEntity.ok(invitationLink);
  }
}
