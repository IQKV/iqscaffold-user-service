package com.iqscaffold.userservice.shared;

import java.io.InputStream;
import java.util.Optional;

import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for file storage operations.
 *
 * <p>Provides abstraction over object storage systems (S3, MinIO, etc.) for
 * file upload, download, and management operations with proper error handling
 * and security considerations.
 *
 * <h3>Core Operations</h3>
 * <ul>
 *   <li><strong>Upload</strong> - Store files with automatic key generation</li>
 *   <li><strong>Download</strong> - Retrieve files by key with streaming support</li>
 *   <li><strong>Delete</strong> - Remove files from storage</li>
 *   <li><strong>URL Generation</strong> - Create presigned URLs for direct access</li>
 * </ul>
 *
 * <h3>Security Features</h3>
 * <ul>
 *   <li><strong>File Validation</strong> - MIME type and size validation</li>
 *   <li><strong>Key Generation</strong> - Secure, unique file keys</li>
 *   <li><strong>Tenant Isolation</strong> - Files scoped to tenant context</li>
 *   <li><strong>Presigned URLs</strong> - Time-limited access URLs</li>
 * </ul>
 *
 * @author IQ Key Value
 */
public interface FileStorageService {

  /**
   * Uploads a file to object storage with automatic key generation.
   *
   * @param file   the multipart file to upload
   * @param folder the folder/prefix for organizing files
   * @return the generated storage key for the uploaded file
   * @throws FileStorageException if upload fails or file is invalid
   */
  String uploadFile(MultipartFile file, String folder);

  /**
   * Downloads a file from object storage.
   *
   * @param key the storage key of the file
   * @return input stream of the file content, empty if not found
   * @throws FileStorageException if download fails
   */
  Optional<InputStream> downloadFile(String key);

  /**
   * Deletes a file from object storage.
   *
   * @param key the storage key of the file to delete
   * @throws FileStorageException if deletion fails
   */
  void deleteFile(String key);

  /**
   * Generates a presigned URL for direct file access.
   *
   * @param key the storage key of the file
   * @return presigned URL for file access, empty if file not found
   * @throws FileStorageException if URL generation fails
   */
  Optional<String> generatePresignedUrl(String key);

  /**
   * Validates if a file meets upload requirements.
   *
   * @param file the file to validate
   * @return true if file is valid for upload
   */
  boolean isValidFile(MultipartFile file);

  /**
   * Gets the maximum allowed file size in bytes.
   *
   * @return maximum file size in bytes
   */
  long getMaxFileSizeBytes();
}
