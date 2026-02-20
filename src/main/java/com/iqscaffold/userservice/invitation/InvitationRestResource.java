package com.iqscaffold.userservice.invitation;

import com.iqscaffold.userservice.usermanagement.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

/**
 * REST controller for authenticated invitation management endpoints.
 * Requires ADMIN or TENANT_ADMIN authority.
 */
@RestController
@RequestMapping("/api/v1/invitations")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Invitations", description = "Organization invitation management API (authenticated)")
public class InvitationRestResource {

  private final InvitationService invitationService;

  public InvitationRestResource(InvitationService invitationService) {
    this.invitationService = invitationService;
  }

  /**
   * Create a new organization invitation.
   *
   * @param request     Invitation creation request
   * @param currentUser Current authenticated user
   * @return Created invitation DTO
   */
  @PostMapping
  @PreAuthorize("hasAnyAuthority('ADMIN', 'TENANT_ADMIN')")
  @Operation(
      summary = "Create organization invitation",
      description = "Create a new invitation for users to join the organization. Requires ADMIN or TENANT_ADMIN authority."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "201",
          description = "Invitation created successfully",
          content = @Content(schema = @Schema(implementation = OrganizationInvitationDto.class))
      ),
      @ApiResponse(responseCode = "400", description = "Invalid request data"),
      @ApiResponse(responseCode = "401", description = "Authentication required"),
      @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
      @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
  })
  public ResponseEntity<OrganizationInvitationDto> createInvitation(
      @Valid @RequestBody CreateInvitationRequest request,
      @AuthenticationPrincipal UserContext currentUser
  ) {
    OrganizationInvitationDto invitation = invitationService.createInvitation(request, currentUser);
    return ResponseEntity.status(HttpStatus.CREATED).body(invitation);
  }

  /**
   * List invitations for the current user's organization.
   *
   * @param status      Optional status filter
   * @param pageable    Pagination parameters
   * @param currentUser Current authenticated user
   * @return Page of invitations
   */
  @GetMapping
  @PreAuthorize("hasAnyAuthority('ADMIN', 'TENANT_ADMIN')")
  @Operation(
      summary = "List organization invitations",
      description = "Get paginated list of invitations for the current user's organization. Requires ADMIN or TENANT_ADMIN authority."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Invitations retrieved successfully",
          content = @Content(schema = @Schema(implementation = Page.class))
      ),
      @ApiResponse(responseCode = "401", description = "Authentication required"),
      @ApiResponse(responseCode = "403", description = "Insufficient permissions")
  })
  public ResponseEntity<Page<OrganizationInvitationDto>> listInvitations(
      @Parameter(description = "Filter by invitation status")
      @RequestParam(required = false) InvitationStatus status,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
      @AuthenticationPrincipal UserContext currentUser
  ) {
    Page<OrganizationInvitationDto> invitations = invitationService.getOrganizationInvitations(
        status,
        pageable,
        currentUser
    );
    return ResponseEntity.ok(invitations);
  }

  /**
   * Revoke an invitation.
   *
   * @param invitationId Invitation ID to revoke
   * @param currentUser  Current authenticated user
   * @return No content
   */
  @DeleteMapping("/{invitationId}")
  @PreAuthorize("hasAnyAuthority('ADMIN', 'TENANT_ADMIN')")
  @Operation(
      summary = "Revoke invitation",
      description = "Revoke an existing invitation. Requires ADMIN or TENANT_ADMIN authority."
  )
  @ApiResponses(value = {
      @ApiResponse(responseCode = "204", description = "Invitation revoked successfully"),
      @ApiResponse(responseCode = "401", description = "Authentication required"),
      @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
      @ApiResponse(responseCode = "404", description = "Invitation not found")
  })
  public ResponseEntity<Void> revokeInvitation(
      @Parameter(description = "Invitation ID") @PathVariable Long invitationId,
      @AuthenticationPrincipal UserContext currentUser
  ) {
    invitationService.revokeInvitation(invitationId, currentUser);
    return ResponseEntity.noContent().build();
  }

  /**
   * Get invitation link for sharing.
   *
   * @param invitationId Invitation ID
   * @param currentUser  Current authenticated user
   * @return Invitation link response
   */
  @GetMapping("/{invitationId}/link")
  @PreAuthorize("hasAnyAuthority('ADMIN', 'TENANT_ADMIN')")
  @Operation(
      summary = "Get invitation link",
      description = "Get the full invitation link for sharing. Requires ADMIN or TENANT_ADMIN authority."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Invitation link retrieved successfully",
          content = @Content(schema = @Schema(implementation = InvitationLinkResponse.class))
      ),
      @ApiResponse(responseCode = "401", description = "Authentication required"),
      @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
      @ApiResponse(responseCode = "404", description = "Invitation not found")
  })
  public ResponseEntity<InvitationLinkResponse> getInvitationLink(
      @Parameter(description = "Invitation ID") @PathVariable Long invitationId,
      @AuthenticationPrincipal UserContext currentUser
  ) {
    InvitationLinkResponse link = invitationService.getInvitationLink(invitationId, currentUser);
    return ResponseEntity.ok(link);
  }
}
