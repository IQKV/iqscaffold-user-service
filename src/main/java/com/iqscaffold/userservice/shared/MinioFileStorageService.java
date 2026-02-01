package com.iqscaffold.userservice.shared;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.iqscaffold.userservice.config.S3ConfigurationProperties;
import com.iqscaffold.userservice.shared.exception.FileStorageException;
import com.iqscaffold.userservice.tenancy.TenantContext;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * MinIO implementation of file storage service.
 *
 * <p>Provides comprehensive file storage operations using MinIO/S3 compatible
 * object storage with proper tenant isolation, security validation, and
 * error handling.
 *
 * <h3>Key Features</h3>
 * <ul>
 *   <li><strong>Tenant Isolation</strong> - Files organized by tenant ID</li>
 *   <li><strong>Secure Keys</strong> - UUID-based file keys with timestamps</li>
 *   <li><strong>MIME Validation</strong> - Apache Tika for content type detection</li>
 *   <li><strong>Size Limits</strong> - Configurable file size restrictions</li>
 *   <li><strong>Presigned URLs</strong> - Time-limited direct access</li>
 * </ul>
 *
 * <h3>File Organization</h3>
 * <pre>
 * bucket/
 * ├── tenant-{tenantId}/
 * │   ├── avatars/
 * │   │   ├── 2024/01/15/
 * │   │   │   └── {uuid}.{ext}
 * │   └── documents/
 * │       └── ...
 * </pre>
 *
 * @author IQ Scaffold
 */
@Service
public class MinioFileStorageService implements FileStorageService {

  private static final Logger log = LoggerFactory.getLogger(MinioFileStorageService.class);

  private final MinioClient minioClient;
  private final S3ConfigurationProperties s3Properties;
  private final Tika tika;

  public MinioFileStorageService(
      final MinioClient minioClient,
      final S3ConfigurationProperties s3Properties) {
    this.minioClient = minioClient;
    this.s3Properties = s3Properties;
    this.tika = new Tika();
  }

  @PostConstruct
  public void initializeBucket() {
    try {
      // Check if bucket exists, create if not
      var bucketExists = minioClient.bucketExists(
          io.minio.BucketExistsArgs.builder()
              .bucket(s3Properties.bucketName())
              .build()
      );

      if (!bucketExists) {
        log.info("Creating bucket: {}", s3Properties.bucketName());
        minioClient.makeBucket(
            io.minio.MakeBucketArgs.builder()
                .bucket(s3Properties.bucketName())
                .region(s3Properties.region())
                .build()
        );
        log.info("Bucket created successfully: {}", s3Properties.bucketName());
      } else {
        log.info("Bucket already exists: {}", s3Properties.bucketName());
      }
    } catch (final Exception e) {
      log.error("Failed to initialize bucket: {}", s3Properties.bucketName(), e);
      throw new FileStorageException("Failed to initialize storage bucket", e);
    }
  }

  @Override
  public String uploadFile(final MultipartFile file, final String folder) {
    if (!isValidFile(file)) {
      throw new FileStorageException("Invalid file: " + file.getOriginalFilename());
    }

    try {
      var key = generateFileKey(file, folder);
      var contentType = detectContentType(file);

      log.info("Uploading file: {} -> {}", file.getOriginalFilename(), key);

      minioClient.putObject(
          PutObjectArgs.builder()
              .bucket(s3Properties.bucketName())
              .object(key)
              .stream(file.getInputStream(), file.getSize(), -1)
              .contentType(contentType)
              .build()
      );

      log.info("File uploaded successfully: {}", key);
      return key;

    } catch (final Exception e) {
      log.error("Failed to upload file: {}", file.getOriginalFilename(), e);
      throw new FileStorageException("Failed to upload file", e);
    }
  }

  @Override
  public Optional<InputStream> downloadFile(final String key) {
    try {
      log.debug("Downloading file: {}", key);

      var inputStream = minioClient.getObject(
          GetObjectArgs.builder()
              .bucket(s3Properties.bucketName())
              .object(key)
              .build()
      );

      return Optional.of(inputStream);

    } catch (final Exception e) {
      log.warn("Failed to download file: {}", key, e);
      return Optional.empty();
    }
  }

  @Override
  public void deleteFile(final String key) {
    try {
      log.info("Deleting file: {}", key);

      minioClient.removeObject(
          RemoveObjectArgs.builder()
              .bucket(s3Properties.bucketName())
              .object(key)
              .build()
      );

      log.info("File deleted successfully: {}", key);

    } catch (final Exception e) {
      log.error("Failed to delete file: {}", key, e);
      throw new FileStorageException("Failed to delete file", e);
    }
  }

  @Override
  public Optional<String> generatePresignedUrl(final String key) {
    try {
      log.debug("Generating presigned URL for: {}", key);

      var url = minioClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.GET)
              .bucket(s3Properties.bucketName())
              .object(key)
              .expiry(s3Properties.upload().presignedUrlExpirationMinutes(), TimeUnit.MINUTES)
              .build()
      );

      return Optional.of(url);

    } catch (final Exception e) {
      log.warn("Failed to generate presigned URL for: {}", key, e);
      return Optional.empty();
    }
  }

  @Override
  public boolean isValidFile(final MultipartFile file) {
    if (file == null || file.isEmpty()) {
      log.warn("File is null or empty");
      return false;
    }

    // Check file size
    if (file.getSize() > s3Properties.upload().maxFileSizeBytes()) {
      log.warn("File size {} exceeds maximum allowed size {}",
          file.getSize(), s3Properties.upload().maxFileSizeBytes());
      return false;
    }

    // Check MIME type
    var contentType = detectContentType(file);
    if (!s3Properties.upload().allowedMimeTypes().contains(contentType)) {
      log.warn("File type {} is not allowed. Allowed types: {}",
          contentType, s3Properties.upload().allowedMimeTypes());
      return false;
    }

    return true;
  }

  @Override
  public long getMaxFileSizeBytes() {
    return s3Properties.upload().maxFileSizeBytes();
  }

  /**
   * Generates a unique file key with tenant isolation and date organization.
   *
   * @param file   the file being uploaded
   * @param folder the folder/category for the file
   * @return unique file key
   */
  private String generateFileKey(final MultipartFile file, final String folder) {
    var tenantId = TenantContext.getCurrentTenantId();
    var now = LocalDateTime.now();
    var datePath = now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
    var uuid = UUID.randomUUID().toString();
    var extension = getFileExtension(file.getOriginalFilename());

    return String.format("tenant-%s/%s/%s/%s%s",
        tenantId, folder, datePath, uuid, extension);
  }

  /**
   * Detects the actual content type of a file using Apache Tika.
   *
   * @param file the file to analyze
   * @return detected MIME type
   */
  private String detectContentType(final MultipartFile file) {
    try {
      return tika.detect(file.getInputStream(), file.getOriginalFilename());
    } catch (final Exception e) {
      log.warn("Failed to detect content type for: {}, using provided type",
          file.getOriginalFilename(), e);
      return file.getContentType() != null ? file.getContentType() : "application/octet-stream";
    }
  }

  /**
   * Extracts file extension from filename.
   *
   * @param filename the original filename
   * @return file extension with dot prefix, or empty string if none
   */
  private String getFileExtension(final String filename) {
    if (filename == null || filename.isEmpty()) {
      return "";
    }

    var lastDotIndex = filename.lastIndexOf('.');
    if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
      return "";
    }

    return filename.substring(lastDotIndex);
  }
}
