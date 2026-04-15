package com.iqscaffold.userservice.shared.exception;

/**
 * Exception thrown when file storage operations fail.
 *
 * <p>Represents various file storage related errors including upload failures,
 * download errors, validation issues, and storage system connectivity problems.
 *
 * <h3>Common Scenarios</h3>
 * <ul>
 *   <li><strong>Upload Failures</strong> - Network issues, storage full, permissions</li>
 *   <li><strong>Validation Errors</strong> - Invalid file type, size exceeded</li>
 *   <li><strong>Download Errors</strong> - File not found, access denied</li>
 *   <li><strong>Configuration Issues</strong> - Invalid credentials, endpoint unreachable</li>
 * </ul>
 *
 * @author IQ Key Value
 */
public class FileStorageException extends RuntimeException {

  /**
   * Constructs a new file storage exception with the specified detail message.
   *
   * @param message the detail message
   */
  public FileStorageException(final String message) {
    super(message);
  }

  /**
   * Constructs a new file storage exception with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause   the cause of the exception
   */
  public FileStorageException(final String message, final Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new file storage exception with the specified cause.
   *
   * @param cause the cause of the exception
   */
  public FileStorageException(final Throwable cause) {
    super(cause);
  }
}
