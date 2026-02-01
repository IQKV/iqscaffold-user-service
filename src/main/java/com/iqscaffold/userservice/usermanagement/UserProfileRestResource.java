package com.iqscaffold.userservice.usermanagement;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.iqscaffold.userservice.authentication.AuthenticationService;
import com.iqscaffold.userservice.authentication.JwtService;
import com.iqscaffold.userservice.passwordmanagement.ChangePasswordRequest;
import com.iqscaffold.userservice.shared.exception.FileStorageException;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for current user profile operations.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Profile", description = "Current authenticated user profile operations")
@SecurityRequirement(name = "bearerAuth")
public class UserProfileRestResource {

  private final JwtService jwtService;
  private final AuthenticationService authenticationService;
  private final AvatarManagementService avatarManagementService;

  public UserProfileRestResource(
      final JwtService jwtService,
      final AuthenticationService authenticationService,
      final AvatarManagementService avatarManagementService) {
    this.jwtService = jwtService;
    this.authenticationService = authenticationService;
    this.avatarManagementService = avatarManagementService;
  }

  @GetMapping("/me")
  @Operation(
      summary = "Get current authenticated user",
      description = "Return the user context derived from the bearer JWT used to authenticate the request.",
      tags = {"User Profile"}
  )
  @Timed(value = "auth.endpoint", extraTags = {"endpoint", "me"})
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "User context returned",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = UserContext.class),
              examples = @ExampleObject(
                  name = "User Context",
                  summary = "Authenticated user information",
                  value = """
                      {
                        "userId": 1,
                        "username": "john.doe",
                        "email": "john.doe@example.com",
                        "authorities": ["USER"],
                        "permissions": [],
                        "firstName": "John",
                        "lastName": "Doe",
                        "tenantId": "tenant-123",
                        "customClaims": {}
                      }
                      """
              )
          )
      ),
      @ApiResponse(responseCode = "401", description = "Unauthorized", ref = "#/components/responses/Unauthorized")
  })
  public ResponseEntity<UserContext> getCurrentUser(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken token) {
      var user = jwtService.extractUserContext(token.getToken());
      return ResponseEntity.ok(user);
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  @PatchMapping("/me/password")
  @Operation(
      summary = "Change password",
      description = """
          Change password for authenticated user. Requires current password verification.
          
          ## Features
          - Current password verification
          - Strong password validation
          - Session preservation
          - Confirmation email notification
          
          ## Password Requirements
          - Minimum 8 characters
          - Must include uppercase letter
          - Must include lowercase letter
          - Must include number
          - Must include special character
          - Cannot be same as current password
          
          ## Security
          - Requires valid authentication token
          - Verifies current password
          - Audit logging of password changes
          - User receives confirmation email
          """,
      tags = {"User Profile"}
  )
  @ApiResponses(value = {
      @ApiResponse(responseCode = "204", description = "Password changed successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid input or current password incorrect", ref = "#/components/responses/BadRequest"),
      @ApiResponse(responseCode = "401", description = "Unauthorized", ref = "#/components/responses/Unauthorized")
  })
  @Timed(value = "password.endpoint", extraTags = {"endpoint", "change"})
  public ResponseEntity<Void> changePassword(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          description = "Change password request containing current and new password",
          required = true,
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ChangePasswordRequest.class)
          )
      )
      @Valid @RequestBody ChangePasswordRequest request,
      Authentication authentication,
      HttpServletRequest httpRequest) {
    if (authentication instanceof JwtAuthenticationToken token) {
      var subject = token.getToken().getSubject();
      try {
        var userId = Long.parseLong(subject);
        var clientIp = getClientIpAddress(httpRequest);
        authenticationService.changePassword(userId, request.currentPassword(), request.newPassword(), clientIp);
        return ResponseEntity.noContent().build();
      } catch (final NumberFormatException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  @PatchMapping("/me/locale")
  @Operation(
      summary = "Update user's preferred locale",
      description = """
          Update the authenticated user's preferred locale setting.
          
          ## Supported Locales
          - `en` - English (default)
          - `es` - Spanish
          - `fr` - French
          
          ## Behavior
          - Updates user's preferred locale in database
          - New locale will be used for future email notifications
          - JWT tokens issued after this change will include the new locale
          - Response messages will use the updated locale
          
          ## Security
          - Requires valid authentication token
          - Only affects the authenticated user's settings
          """,
      tags = {"User Profile"}
  )
  @ApiResponses(value = {
      @ApiResponse(responseCode = "204", description = "Locale updated successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid locale", ref = "#/components/responses/BadRequest"),
      @ApiResponse(responseCode = "401", description = "Unauthorized", ref = "#/components/responses/Unauthorized")
  })
  @Timed(value = "user.locale.update", extraTags = {"endpoint", "locale"})
  public ResponseEntity<Void> updateLocale(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          description = "Update locale request containing the new preferred locale",
          required = true,
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = UpdateLocaleRequest.class),
              examples = {
                  @ExampleObject(name = "English", value = "{\"locale\": \"en\"}"),
                  @ExampleObject(name = "Spanish", value = "{\"locale\": \"es\"}"),
                  @ExampleObject(name = "French", value = "{\"locale\": \"fr\"}")
              }
          )
      )
      @Valid @RequestBody UpdateLocaleRequest request,
      Authentication authentication) {

    if (authentication instanceof JwtAuthenticationToken token) {
      var subject = token.getToken().getSubject();
      try {
        var userId = Long.parseLong(subject);
        authenticationService.updateUserLocale(userId, request.locale());
        return ResponseEntity.noContent().build();
      } catch (final NumberFormatException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  /**
   * Get client IP address, considering proxy headers.
   */
  private String getClientIpAddress(HttpServletRequest request) {
    // Check for X-Forwarded-For header (common in load balancers)
    var headerxForwardedFor = request.getHeader("X-Forwarded-For");
    if (headerxForwardedFor != null && !headerxForwardedFor.isEmpty()) {
      // Take the first IP in the chain
      return headerxForwardedFor.split(",")[0].trim();
    }

    // Check for X-Real-IP header (nginx)
    var headerxRealIp = request.getHeader("X-Real-IP");
    if (headerxRealIp != null && !headerxRealIp.isEmpty()) {
      return headerxRealIp;
    }

    // Fall back to remote address
    return request.getRemoteAddr();
  }

  @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Upload user avatar",
      description = """
          Upload a new avatar image for the authenticated user.
          
          ## File Requirements
          - Maximum file size: 5MB
          - Supported formats: JPEG, PNG, WebP, GIF
          - File content is validated using MIME type detection
          
          ## Features
          - Automatic file validation and security scanning
          - Tenant-isolated storage with unique file keys
          - Automatic cleanup of previous avatar
          - Presigned URL generation for secure access
          - Metadata tracking (file size, type, upload date)
          
          ## Storage Organization
          Files are stored with tenant isolation:
          ```
          bucket/tenant-{tenantId}/avatars/{year}/{month}/{day}/{uuid}.{ext}
          ```
          
          ## Security
          - Requires valid authentication token
          - File type validation prevents malicious uploads
          - Size limits prevent abuse
          - Tenant isolation ensures data segregation
          """,
      tags = {"User Profile"}
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Avatar uploaded successfully",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = AvatarUploadResponse.class)
          )
      ),
      @ApiResponse(responseCode = "400", description = "Invalid file or file too large"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "413", description = "File size exceeds maximum allowed"),
      @ApiResponse(responseCode = "415", description = "Unsupported file type")
  })
  @Timed(value = "avatar.upload", extraTags = {"endpoint", "upload"})
  public ResponseEntity<?> uploadAvatar(
      @Parameter(
          description = "Avatar image file (JPEG, PNG, WebP, GIF - max 5MB)",
          required = true,
          content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
      )
      @RequestParam("file") MultipartFile file,
      Authentication authentication) {

    if (!(authentication instanceof JwtAuthenticationToken token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    try {
      var userContext = jwtService.extractUserContext(token.getToken());
      var response = avatarManagementService.uploadAvatar(file, userContext);
      return ResponseEntity.ok(response);

    } catch (final FileStorageException e) {
      return ResponseEntity.badRequest()
          .body(new ErrorResponse("AVATAR_UPLOAD_FAILED", e.getMessage()));
    } catch (final Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new ErrorResponse("INTERNAL_ERROR", "Failed to upload avatar"));
    }
  }

  @GetMapping("/me/avatar")
  @Operation(
      summary = "Get current user avatar URL",
      description = """
          Get the current avatar URL for the authenticated user.
          
          ## Response
          - Returns presigned URL for direct avatar access
          - URL is time-limited (expires in 1 hour)
          - Returns 404 if no avatar is set
          
          ## Security
          - Requires valid authentication token
          - URLs are tenant-scoped and user-specific
          - Presigned URLs provide secure, time-limited access
          """,
      tags = {"User Profile"}
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Avatar URL returned",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = AvatarUrlResponse.class)
          )
      ),
      @ApiResponse(responseCode = "404", description = "No avatar found"),
      @ApiResponse(responseCode = "401", description = "Unauthorized")
  })
  @Timed(value = "avatar.get", extraTags = {"endpoint", "get"})
  public ResponseEntity<?> getAvatarUrl(Authentication authentication) {

    if (!(authentication instanceof JwtAuthenticationToken token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    try {
      var userContext = jwtService.extractUserContext(token.getToken());
      var avatarUrl = avatarManagementService.getAvatarUrl(userContext);

      if (avatarUrl.isEmpty()) {
        return ResponseEntity.notFound().build();
      }

      return ResponseEntity.ok(new AvatarUrlResponse(avatarUrl.get()));

    } catch (final Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new ErrorResponse("INTERNAL_ERROR", "Failed to get avatar URL"));
    }
  }

  @DeleteMapping("/me/avatar")
  @Operation(
      summary = "Delete user avatar",
      description = """
          Delete the current avatar for the authenticated user.
          
          ## Behavior
          - Removes avatar file from storage
          - Clears avatar metadata from user preferences
          - Returns 404 if no avatar exists
          
          ## Security
          - Requires valid authentication token
          - Only affects the authenticated user's avatar
          - Permanent deletion - cannot be undone
          """,
      tags = {"User Profile"}
  )
  @ApiResponses(value = {
      @ApiResponse(responseCode = "204", description = "Avatar deleted successfully"),
      @ApiResponse(responseCode = "404", description = "No avatar found"),
      @ApiResponse(responseCode = "401", description = "Unauthorized")
  })
  @Timed(value = "avatar.delete", extraTags = {"endpoint", "delete"})
  public ResponseEntity<?> deleteAvatar(Authentication authentication) {

    if (!(authentication instanceof JwtAuthenticationToken token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    try {
      var userContext = jwtService.extractUserContext(token.getToken());
      var deleted = avatarManagementService.deleteAvatar(userContext);

      if (!deleted) {
        return ResponseEntity.notFound().build();
      }

      return ResponseEntity.noContent().build();

    } catch (final FileStorageException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new ErrorResponse("AVATAR_DELETE_FAILED", e.getMessage()));
    } catch (final Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new ErrorResponse("INTERNAL_ERROR", "Failed to delete avatar"));
    }
  }

  /**
   * Response DTO for avatar URL.
   */
  public record AvatarUrlResponse(
      @Schema(description = "Presigned URL for avatar access",
              example = "https://minio.example.com/user-avatars/tenant-123/avatars/2024/01/15/uuid.jpg?X-Amz-Expires=3600")
      String avatarUrl
  ) {
  }

  /**
   * Response DTO for error messages.
   */
  public record ErrorResponse(
      @Schema(description = "Error code", example = "AVATAR_UPLOAD_FAILED")
      String code,

      @Schema(description = "Error message", example = "File size exceeds maximum allowed")
      String message
  ) {
  }
}
