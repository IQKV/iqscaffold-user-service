package com.iqscaffold.userservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for S3/MinIO integration.
 *
 * <p>Provides comprehensive configuration for object storage operations including
 * connection settings, bucket configuration, and file upload constraints.
 *
 * <h3>Configuration Structure</h3>
 * <ul>
 *   <li><strong>Connection</strong> - Endpoint, credentials, and SSL settings</li>
 *   <li><strong>Bucket</strong> - Bucket name and region configuration</li>
 *   <li><strong>Upload Limits</strong> - File size and type restrictions</li>
 *   <li><strong>URL Generation</strong> - Presigned URL expiration settings</li>
 * </ul>
 *
 * <h3>Security Considerations</h3>
 * <ul>
 *   <li>Credentials should be provided via environment variables</li>
 *   <li>SSL should be enabled for production environments</li>
 *   <li>File size limits prevent abuse and storage costs</li>
 *   <li>Allowed MIME types restrict upload content</li>
 * </ul>
 *
 * @author IQ Scaffold
 */
@ConfigurationProperties(prefix = "iqscaffold.s3")
@Validated
public record S3ConfigurationProperties(

    @NotBlank(message = "S3 endpoint URL is required")
    String endpoint,

    @NotBlank(message = "S3 access key is required")
    String accessKey,

    @NotBlank(message = "S3 secret key is required")
    String secretKey,

    @NotBlank(message = "S3 bucket name is required")
    String bucketName,

    String region,

    @NotNull(message = "SSL configuration is required")
    Boolean ssl,

    @NotNull(message = "Upload configuration is required")
    UploadConfig upload
) {

  /**
   * Upload configuration for file constraints and validation.
   */
  public record UploadConfig(

      @Positive(message = "Maximum file size must be positive")
      Long maxFileSizeBytes,

      @NotNull(message = "Allowed MIME types list is required")
      String allowedMimeTypes,

      @Positive(message = "Presigned URL expiration must be positive")
      Integer presignedUrlExpirationMinutes
  ) {

    /**
     * Default constructor with sensible defaults for profile picture uploads.
     */
    public UploadConfig() {
      this(
          5 * 1024 * 1024L, // 5MB max file size
          "image/jpeg,image/png,image/webp,image/gif",
          60 // 1 hour expiration for presigned URLs
      );
    }

    /**
     * Get allowed MIME types as a list
     *
     * @return list of allowed MIME types
     */
    public java.util.List<String> getAllowedMimeTypesList() {
      return java.util.Arrays.asList(allowedMimeTypes.split(","));
    }
  }

  /**
   * Default constructor with common MinIO development settings.
   */
  public S3ConfigurationProperties() {
    this(
        "http://localhost:9000",
        "",
        "",
        "user-avatars",
        "us-east-1",
        false,
        new UploadConfig()
    );
  }
}
