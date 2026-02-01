package com.iqscaffold.userservice.usermanagement;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for avatar upload operations.
 *
 * <p>Contains the result of avatar upload including the storage key,
 * access URL, and metadata about the uploaded file.
 *
 * @param storageKey  the unique storage key for the uploaded avatar
 * @param avatarUrl   the URL to access the avatar image
 * @param fileSize    the size of the uploaded file in bytes
 * @param contentType the MIME type of the uploaded file
 * @author IQ Scaffold
 */
@Schema(description = "Response containing avatar upload results")
public record AvatarUploadResponse(

    @Schema(description = "Unique storage key for the uploaded avatar",
            example = "tenant-123/avatars/2024/01/15/a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg")
    String storageKey,

    @Schema(description = "URL to access the avatar image",
            example = "https://minio.example.com/user-avatars/tenant-123/avatars/2024/01/15/a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg")
    String avatarUrl,

    @Schema(description = "Size of the uploaded file in bytes",
            example = "1048576")
    Long fileSize,

    @Schema(description = "MIME type of the uploaded file",
            example = "image/jpeg")
    String contentType
) {
}
