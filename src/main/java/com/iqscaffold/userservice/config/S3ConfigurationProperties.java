package com.iqscaffold.userservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;
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
 * @author IQ Key Value
 */
@ConfigurationProperties(prefix = "iqscaffold.object-storage")
@Validated
public record S3ConfigurationProperties(

    @NotBlank(message = "S3 endpoint URL is required")
    String endpoint,

    @NotBlank(message = "S3 access key is required")
    @Name("access-key")
    String accessKey,

    @NotBlank(message = "S3 secret key is required")
    @Name("secret-key")
    String secretKey,

    @NotBlank(message = "S3 bucket name is required")
    @Name("bucket-name")
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
      @Name("max-file-size-bytes")
      Long maxFileSizeBytes,

      @NotNull(message = "Allowed MIME types list is required")
      @Name("allowed-mime-types")
      String allowedMimeTypes,

      @Positive(message = "Presigned URL expiration must be positive")
      @Name("presigned-url-expiration-minutes")
      Integer presignedUrlExpirationMinutes
  ) {

    /**
     * Get allowed MIME types as a list
     *
     * @return list of allowed MIME types
     */
    public java.util.List<String> getAllowedMimeTypesList() {
      return java.util.Arrays.asList(allowedMimeTypes.split(","));
    }
  }
}

