package com.iqscaffold.userservice.invitation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for public invitation endpoints.
 * No authentication required - used for signup flow.
 */
@RestController
@RequestMapping("/api/v1/public/invitations")
@Tag(name = "Public Invitations", description = "Public invitation API for signup flow (no authentication required)")
public class PublicInvitationRestResource {

  private final InvitationService invitationService;
  private final InvitationSignupService invitationSignupService;

  public PublicInvitationRestResource(
      InvitationService invitationService,
      InvitationSignupService invitationSignupService
  ) {
    this.invitationService = invitationService;
    this.invitationSignupService = invitationSignupService;
  }

  /**
   * Get organization preview for an invitation.
   * Shows organization information before signup.
   *
   * @param invitationCode Invitation code
   * @return Organization preview DTO
   */
  @GetMapping("/{invitationCode}/preview")
  @Operation(
      summary = "Preview organization",
      description = "Get organization information for an invitation code. No authentication required."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Organization preview retrieved successfully",
          content = @Content(schema = @Schema(implementation = OrganizationPreviewDto.class))
      ),
      @ApiResponse(responseCode = "404", description = "Invitation not found")
  })
  public ResponseEntity<OrganizationPreviewDto> previewInvitation(
      @Parameter(description = "Invitation code") @PathVariable String invitationCode
  ) {
    OrganizationPreviewDto preview = invitationSignupService.getOrganizationPreview(invitationCode);
    return ResponseEntity.ok(preview);
  }

  /**
   * Validate an invitation code.
   * Checks if invitation is valid and can be used for signup.
   *
   * @param invitationCode Invitation code
   * @return Validation result
   */
  @PostMapping("/{invitationCode}/validate")
  @Operation(
      summary = "Validate invitation",
      description = "Validate an invitation code before signup. No authentication required."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Validation result returned",
          content = @Content(schema = @Schema(implementation = InvitationValidationResult.class))
      ),
      @ApiResponse(responseCode = "404", description = "Invitation not found")
  })
  public ResponseEntity<InvitationValidationResult> validateInvitation(
      @Parameter(description = "Invitation code") @PathVariable String invitationCode
  ) {
    InvitationValidationResult result = invitationService.validateInvitation(invitationCode);
    return ResponseEntity.ok(result);
  }

  /**
   * Sign up with an invitation code.
   * Creates a new user account in the target organization.
   *
   * @param invitationCode Invitation code
   * @param request        Signup request
   * @return Signup response with JWT tokens
   */
  @PostMapping("/{invitationCode}/signup")
  @Operation(
      summary = "Sign up with invitation",
      description = "Create a new user account using an invitation code. No authentication required."
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "201",
          description = "User account created successfully",
          content = @Content(schema = @Schema(implementation = SignupWithInvitationResponse.class))
      ),
      @ApiResponse(responseCode = "400", description = "Invalid request data or invitation"),
      @ApiResponse(responseCode = "409", description = "Username or email already exists"),
      @ApiResponse(responseCode = "410", description = "Invitation expired or revoked")
  })
  public ResponseEntity<SignupWithInvitationResponse> signupWithInvitation(
      @Parameter(description = "Invitation code") @PathVariable String invitationCode,
      @Valid @RequestBody SignupWithInvitationRequest request
  ) {
    SignupWithInvitationResponse response = invitationSignupService.signupWithInvitation(
        invitationCode,
        request
    );
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
