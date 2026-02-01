package com.iqscaffold.userservice.usermanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import com.iqscaffold.userservice.shared.FileStorageService;
import com.iqscaffold.userservice.shared.exception.FileStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Unit tests for AvatarManagementService.
 */
@ExtendWith(MockitoExtension.class)
class AvatarManagementServiceTest {

  @Mock
  private FileStorageService fileStorageService;

  @Mock
  private UserPreferenceService userPreferenceService;

  private AvatarManagementService avatarManagementService;

  private UserContext userContext;
  private UserPreferenceDto userPreferenceDto;

  @BeforeEach
  void setUp() {
    avatarManagementService = new AvatarManagementService(fileStorageService, userPreferenceService);

    userContext = new UserContext(
        1L, "testuser", "test@example.com",
        Set.of("USER"), Set.of(),
        "Test", "User", "tenant-123", null, java.util.Map.of()
    );

    userPreferenceDto = new UserPreferenceDto(
        1L, 1L, "testuser", "en", "UTC", "USD",
        "yyyy-MM-dd", "HH:mm:ss", "light", null, null, null,
        true, false, true, false, null, null, "tenant-123",
        LocalDateTime.now(), LocalDateTime.now()
    );
  }

  @Test
  @DisplayName("Should upload avatar successfully")
  void shouldUploadAvatarSuccessfully() {
    // Arrange
    var file = new MockMultipartFile(
        "avatar",
        "test-avatar.jpg",
        "image/jpeg",
        "test image content".getBytes()
    );

    var storageKey = "tenant-123/avatars/2024/01/15/uuid.jpg";
    var presignedUrl = "https://minio.example.com/bucket/" + storageKey;

    when(fileStorageService.isValidFile(file)).thenReturn(true);
    when(fileStorageService.uploadFile(file, "avatars")).thenReturn(storageKey);
    when(fileStorageService.generatePresignedUrl(storageKey)).thenReturn(Optional.of(presignedUrl));
    when(userPreferenceService.getMyPreferences(userContext)).thenReturn(userPreferenceDto);
    when(userPreferenceService.updateMyPreferences(any(UpdateUserPreferenceRequest.class), eq(userContext)))
        .thenReturn(userPreferenceDto);

    // Act
    var result = avatarManagementService.uploadAvatar(file, userContext);

    // Assert
    assertThat(result.storageKey()).isEqualTo(storageKey);
    assertThat(result.avatarUrl()).isEqualTo(presignedUrl);
    assertThat(result.fileSize()).isEqualTo(file.getSize());
    assertThat(result.contentType()).isEqualTo(file.getContentType());

    verify(userPreferenceService).updateMyPreferences(any(UpdateUserPreferenceRequest.class), eq(userContext));
  }

  @Test
  @DisplayName("Should throw exception for invalid file")
  void shouldThrowExceptionForInvalidFile() {
    // Arrange
    var file = new MockMultipartFile(
        "avatar",
        "test.txt",
        "text/plain",
        "not an image".getBytes()
    );

    when(fileStorageService.isValidFile(file)).thenReturn(false);

    // Act & Assert
    assertThatThrownBy(() -> avatarManagementService.uploadAvatar(file, userContext))
        .isInstanceOf(FileStorageException.class)
        .hasMessageContaining("Invalid avatar file");
  }

  @Test
  @DisplayName("Should get avatar URL successfully")
  void shouldGetAvatarUrlSuccessfully() {
    // Arrange
    var avatarUrl = "https://minio.example.com/bucket/avatar.jpg";
    var preferenceWithAvatar = new UserPreferenceDto(
        1L, 1L, "testuser", "en", "UTC", "USD",
        "yyyy-MM-dd", "HH:mm:ss", "light", avatarUrl, null, null,
        true, false, true, false, null, null, "tenant-123",
        LocalDateTime.now(), LocalDateTime.now()
    );

    when(userPreferenceService.getMyPreferences(userContext)).thenReturn(preferenceWithAvatar);

    // Act
    var result = avatarManagementService.getAvatarUrl(userContext);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(avatarUrl);
  }

  @Test
  @DisplayName("Should return empty when no avatar exists")
  void shouldReturnEmptyWhenNoAvatarExists() {
    // Arrange
    when(userPreferenceService.getMyPreferences(userContext)).thenReturn(userPreferenceDto);

    // Act
    var result = avatarManagementService.getAvatarUrl(userContext);

    // Assert
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should delete avatar successfully")
  void shouldDeleteAvatarSuccessfully() {
    // Arrange
    var avatarUrl = "https://minio.example.com/bucket/avatar.jpg";
    var preferenceWithAvatar = new UserPreferenceDto(
        1L, 1L, "testuser", "en", "UTC", "USD",
        "yyyy-MM-dd", "HH:mm:ss", "light", avatarUrl, null, null,
        true, false, true, false, null, null, "tenant-123",
        LocalDateTime.now(), LocalDateTime.now()
    );

    when(userPreferenceService.getMyPreferences(userContext)).thenReturn(preferenceWithAvatar);
    when(userPreferenceService.updateMyPreferences(any(UpdateUserPreferenceRequest.class), eq(userContext)))
        .thenReturn(userPreferenceDto);

    // Act
    var result = avatarManagementService.deleteAvatar(userContext);

    // Assert
    assertThat(result).isTrue();
    verify(userPreferenceService).updateMyPreferences(any(UpdateUserPreferenceRequest.class), eq(userContext));
  }

  @Test
  @DisplayName("Should return false when deleting non-existent avatar")
  void shouldReturnFalseWhenDeletingNonExistentAvatar() {
    // Arrange
    when(userPreferenceService.getMyPreferences(userContext)).thenReturn(userPreferenceDto);

    // Act
    var result = avatarManagementService.deleteAvatar(userContext);

    // Assert
    assertThat(result).isFalse();
  }
}
