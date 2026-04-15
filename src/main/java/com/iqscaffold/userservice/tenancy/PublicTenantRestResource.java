package com.iqscaffold.userservice.tenancy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public Tenant REST Resource.
 * Provides public endpoints for tenant/organization discovery (no authentication required).
 */
@RestController
@RequestMapping("/api/v1/public/tenants")
@Tag(name = "Public Tenants", description = "Public tenant/organization discovery API (no authentication required)")
public class PublicTenantRestResource {

  private final PublicTenantService publicTenantService;

  public PublicTenantRestResource(final PublicTenantService publicTenantService) {
    this.publicTenantService = publicTenantService;
  }

  @Operation(
      summary = "List all active tenants/organizations",
      description = """
          Returns a map of all active tenants with their organization names.
          This endpoint is public and does not require authentication.
          
          ## Use Cases
          - Tenant selection on login page
          - Organization discovery
          - Multi-tenant application routing
          
          ## Response Format
          Returns a map where:
          - Key: Tenant ID (used for API requests)
          - Value: Organization name (displayed to users)
          
          ## Security Notes
          - Only returns enabled/active organizations
          - Does not expose sensitive organization data
          - Rate limited to prevent abuse
          """,
      tags = {"Public Tenants"}
  )
  @ApiResponses(value = {
      @ApiResponse(
          responseCode = "200",
          description = "Successfully retrieved tenant list",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = Map.class),
              examples = @ExampleObject(
                  name = "Tenant List",
                  summary = "Map of tenant IDs to organization names",
                  value = """
                      {
                        "default": "IQ Key Value Platform",
                        "acme": "Acme Corporation",
                        "techcorp": "Tech Corp Inc",
                        "startup": "Startup Ventures"
                      }
                      """
              )
          )
      ),
      @ApiResponse(
          responseCode = "500",
          description = "Internal server error",
          content = @Content(mediaType = "application/problem+json")
      )
  })
  @GetMapping
  public ResponseEntity<Map<String, String>> listTenants() {
    var tenants = publicTenantService.getAllActiveTenants();
    return ResponseEntity.ok(tenants);
  }
}
